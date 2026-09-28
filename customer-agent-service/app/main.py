import asyncio
import base64
import binascii
import hashlib
import hmac
import os
import json
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


def _invoke(request: RunRequest) -> dict:
    scopes = require_request_binding(request)
    model_name = os.environ.get("HM_AGENT_MODEL", "")
    api_key = os.environ.get("HM_AGENT_MODEL_API_KEY", "")
    if not model_name or not api_key:
        raise RuntimeError("尚未配置客服模型")
    model = ChatOpenAI(model=model_name, api_key=api_key,
                       base_url=os.environ.get("HM_AGENT_MODEL_BASE_URL") or None,
                       temperature=0.1)
    authenticated = "order:read" in scopes
    java = JavaClient(os.environ.get("HM_JAVA_BASE_URL", "http://127.0.0.1:8080"),
                      request.delegationToken,
                      service_secret=os.environ.get("HM_AGENT_SERVICE_SECRET", ""))
    checkpoint_path = Path(os.environ.get("HM_AGENT_CHECKPOINT_DB", "data/customer-agent-checkpoints.sqlite3"))
    checkpoint_path.parent.mkdir(parents=True, exist_ok=True)
    source_states: list[dict] = []
    ticket_states: list[dict] = []
    fact_states: list[dict] = []
    with SqliteSaver.from_conn_string(str(checkpoint_path)) as saver:
        agent = build_agent(model, java, authenticated, request.message, checkpointer=saver,
                            conversation_id=request.conversationId, run_id=request.runId,
                            source_collector=source_states, ticket_collector=ticket_states,
                            fact_collector=fact_states)
        result = agent.invoke({"messages": [{"role": "user", "content": request.message}]},
                              config={"configurable": {"thread_id": request.conversationId}})
        content = result["messages"][-1].content
        answer = content if isinstance(content, str) else str(content)
    return finalize_answer(request.message, answer, source_states, ticket_states, fact_states)


def format_fact(state: dict) -> str:
    kind, value = state["kind"], state["value"]
    if value.get("error"):
        return value["error"]
    if kind == "items":
        rows = value.get("items") or []
        return "在售商品：" + ("；".join(f"{row['name']}（ID {row['id']}，{row['price']/100:.2f} 元）" for row in rows[:5]) or "暂未查到匹配商品")
    if kind == "item":
        row = value.get("item")
        return f"商品 {row['name']}（ID {row['id']}），价格 {row['price']/100:.2f} 元，库存 {row.get('stock')}。" if row else "未查到该在售商品。"
    if kind == "orders":
        rows = value.get("orders") or []
        return "最近订单：" + ("；".join(f"订单 {row['id']}，状态码 {row['status']}" for row in rows[:5]) or "暂无订单")
    if kind == "order":
        row = value.get("order")
        return f"订单 {row['id']}，状态码 {row['status']}，实付 {row['total_fee']/100:.2f} 元。" if row and row.get("total_fee") is not None else (f"订单 {row['id']}，状态码 {row['status']}。" if row else "未查到该订单。")
    if kind == "logistics":
        row = value.get("logistics")
        if not row:
            return "未查到该订单的物流信息。"
        return (f"订单 {row['order_id']} 当前状态码 {row['order_status']}；"
                f"物流公司：{row.get('logistics_company') or '暂无'}；"
                f"物流单号：{row.get('logistics_number') or '暂无'}。")
    return ""


def finalize_answer(message: str, answer: str, source_states: list[dict], ticket_states: list[dict],
                    fact_states: list[dict] | None = None) -> dict:
    sources = list({(source.get("policyId"), source.get("version")): source
                    for state in source_states for source in state.get("sources", [])}.values())
    uncertain = next((state for state in source_states if state.get("needs_handoff")), None)
    ticket = next((state for state in ticket_states if state.get("ticketId") and state.get("status") == "QUEUED"), None)
    failed_ticket = next((state for state in ticket_states if state.get("status") == "FAILED"), None)
    parts = [line for state in (fact_states or []) if (line := format_fact(state))]
    if len(sources) == 1 and not uncertain:
        source = sources[0]
        parts.append(f"根据已发布规则《{source['title']}》（政策 {source['policyId']}，"
                     f"第 {source['version']} 版，生效于 {source['effectiveFrom']}）：{source['excerpt']}")
    elif len(sources) > 1:
        parts.append("检索到多条可能相关的规则，当前结论不确定；建议转人工核实。")
    if uncertain:
        parts.append(uncertain["notice"])
    if failed_ticket:
        parts.append("人工工单未创建成功，请稍后重试。")
    elif ticket:
        parts.append(f"已创建排队人工工单，编号 {ticket['ticketId']}。")
    if not parts:
        parts.append("尚未取得可核实的商城数据，无法确认答案；人工工单尚未创建，需人工处理请点击“转人工”。")
    # 模型只决定调用哪些受限工具；对外文本由可信工具结果生成，不转发模型自由文本。
    return {"answer": "\n".join(parts), "sources": sources, "ticket": ticket}


def _verified_claims(token: str) -> dict:
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
        return claims
    except (ValueError, KeyError, TypeError, binascii.Error, json.JSONDecodeError) as exc:
        raise ValueError("内部令牌无效") from exc


def _verified_scopes(token: str) -> set[str]:
    return set(_verified_claims(token)["scope"])


def require_request_binding(request: RunRequest) -> set[str]:
    claims = _verified_claims(request.delegationToken)
    if claims.get("conversation_id") != request.conversationId or claims.get("run_id") != request.runId:
        raise ValueError("内部令牌与会话或运行不匹配")
    return set(claims["scope"])


async def process_run(request: RunRequest):
    sequence = 1
    try:
        result = await asyncio.to_thread(_invoke, request)
        if result["sources"]:
            await _callback(request, sequence, "sources", json.dumps(result["sources"], ensure_ascii=False))
            sequence += 1
        if result["ticket"]:
            await _callback(request, sequence, "ticket", json.dumps(result["ticket"], ensure_ascii=False))
            sequence += 1
        await _callback(request, sequence, "completed", result["answer"])
    except Exception:
        await _callback(request, sequence, "error", "客服服务暂时不可用，请稍后重试或转人工")
