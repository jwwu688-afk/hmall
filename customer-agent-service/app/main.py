import asyncio
import base64
import binascii
import hashlib
import hmac
import json
import os
from pathlib import Path
import secrets
import time

import httpx
from dotenv import load_dotenv
from fastapi import BackgroundTasks, FastAPI, Header, HTTPException
from langchain_openai import ChatOpenAI
from langgraph.checkpoint.sqlite import SqliteSaver
from pydantic import BaseModel, Field

from app.agent import build_agent
from app.java_client import JavaClient

load_dotenv(Path(__file__).parents[1] / ".env.local")

app = FastAPI(title="黑马商城客服 Agent", docs_url=None, redoc_url=None)


class RunRequest(BaseModel):
    conversationId: str = Field(min_length=1, max_length=36)
    runId: str = Field(min_length=1, max_length=36)
    message: str = Field(min_length=1, max_length=2000)
    delegationToken: str = Field(min_length=1)


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/internal/runs", status_code=202)
async def run_request(request: RunRequest, background: BackgroundTasks,
                      x_agent_service_secret: str | None = Header(default=None)):
    secret = os.environ.get("HM_AGENT_SERVICE_SECRET", "")
    if not secret or not x_agent_service_secret or not secrets.compare_digest(secret, x_agent_service_secret):
        raise HTTPException(status_code=401, detail="内部服务认证失败")
    background.add_task(process_run, request)
    return {"runId": request.runId, "status": "accepted"}


async def _callback(request: RunRequest, sequence: int, event_type: str, data: str):
    url = os.environ.get("HM_JAVA_BASE_URL", "http://127.0.0.1:8080").rstrip("/")
    secret = os.environ.get("HM_AGENT_SERVICE_SECRET", "")
    payload = {"runId": request.runId, "sequence": sequence, "type": event_type, "data": data}
    async with httpx.AsyncClient(timeout=5.0) as client:
        for attempt in range(3):
            try:
                response = await client.post(
                    f"{url}/internal/customer-service/runs/{request.runId}/events",
                    headers={"X-Agent-Callback-Secret": secret}, json=payload)
                response.raise_for_status()
                return
            except httpx.HTTPError:
                if attempt == 2:
                    raise
                await asyncio.sleep(0.5 * (attempt + 1))


def _invoke(request: RunRequest) -> str:
    model_name = os.environ.get("HM_AGENT_MODEL", "")
    api_key = os.environ.get("HM_AGENT_MODEL_API_KEY", "")
    if not model_name or not api_key:
        raise RuntimeError("尚未配置客服模型")
    model = ChatOpenAI(model=model_name, api_key=api_key,
                       base_url=os.environ.get("HM_AGENT_MODEL_BASE_URL") or None,
                       temperature=0.1)
    authenticated = "order:read" in _verified_scopes(request.delegationToken)
    java = JavaClient(os.environ.get("HM_JAVA_BASE_URL", "http://127.0.0.1:8080"),
                      request.delegationToken,
                      service_secret=os.environ.get("HM_AGENT_SERVICE_SECRET", ""))
    checkpoint_path = Path(os.environ.get("HM_AGENT_CHECKPOINT_DB", "data/customer-agent-checkpoints.sqlite3"))
    checkpoint_path.parent.mkdir(parents=True, exist_ok=True)
    with SqliteSaver.from_conn_string(str(checkpoint_path)) as saver:
        agent = build_agent(model, java, authenticated, request.message, checkpointer=saver,
                            conversation_id=request.conversationId, run_id=request.runId)
        result = agent.invoke({"messages": [{"role": "user", "content": request.message}]},
                              config={"configurable": {"thread_id": request.conversationId}})
        content = result["messages"][-1].content
        return content if isinstance(content, str) else str(content)


def _verified_scopes(token: str) -> set[str]:
    secret = os.environ.get("HM_AGENT_TOKEN_SECRET", "")
    if len(secret) < 32:
        raise ValueError("内部令牌密钥未配置")
    try:
        header, payload, signature = token.split(".")
        expected = hmac.new(secret.encode(), f"{header}.{payload}".encode(), hashlib.sha256).digest()
        actual = base64.urlsafe_b64decode(signature + "=" * (-len(signature) % 4))
        if not hmac.compare_digest(expected, actual):
            raise ValueError("签名无效")
        claims = json.loads(base64.urlsafe_b64decode(payload + "=" * (-len(payload) % 4)))
        meta = json.loads(base64.urlsafe_b64decode(header + "=" * (-len(header) % 4)))
        if meta.get("alg") != "HS256" or claims.get("aud") != "hmall-internal" or claims.get("exp", 0) <= time.time():
            raise ValueError("令牌无效或过期")
        scopes = set(claims.get("scope", []))
        if "order:read" in scopes and not claims.get("sub"):
            raise ValueError("订单权限缺少可信主体")
        return scopes
    except (ValueError, KeyError, TypeError, binascii.Error, json.JSONDecodeError) as exc:
        raise ValueError("内部令牌无效") from exc


async def process_run(request: RunRequest):
    try:
        answer = await asyncio.to_thread(_invoke, request)
        await _callback(request, 1, "completed", answer)
    except Exception:
        await _callback(request, 1, "error", "客服服务暂时不可用，请稍后重试或转人工")
