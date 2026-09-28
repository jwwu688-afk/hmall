import pytest
from fastapi.testclient import TestClient

from app.main import app, _verified_scopes
from app.main import RunRequest
from app.main import process_run
from app.main import _invoke
from app.main import _callback
from app.main import health
from app.main import run_request
from app.main import os
from app.main import secrets
from app.main import json
from app.main import base64
from app.main import hmac
from app.main import hashlib
from app.main import time


def test_internal_run_requires_service_secret(monkeypatch):
    monkeypatch.setenv("HM_AGENT_SERVICE_SECRET", "test-service-secret")
    response = TestClient(app).post("/internal/runs", json={
        "conversationId": "conversation-1", "runId": "run-1",
        "message": "鞋子多少钱", "delegationToken": "signed"})
    assert response.status_code == 401


def test_internal_run_queues_background_work(monkeypatch):
    monkeypatch.setenv("HM_AGENT_SERVICE_SECRET", "test-service-secret")
    called = []

    async def fake_run(request):
        called.append(request.runId)

    monkeypatch.setattr("app.main.process_run", fake_run)
    response = TestClient(app).post("/internal/runs",
        headers={"X-Agent-Service-Secret": "test-service-secret"},
        json={"conversationId": "conversation-1", "runId": "run-1",
              "message": "鞋子多少钱", "delegationToken": "signed"})
    assert response.status_code == 202
    assert called == ["run-1"]


def test_signed_scope_cannot_be_forged_with_message_text(monkeypatch):
    monkeypatch.setenv("HM_AGENT_TOKEN_SECRET", "local-test-secret-with-adequate-length")
    from app.agent import tool_names_for_request
    from app.main import _verified_scopes
    from app.main import hmac, hashlib, base64, json, time
    header = base64.urlsafe_b64encode(b'{"alg":"HS256"}').rstrip(b"=").decode()
    payload = base64.urlsafe_b64encode(json.dumps({"aud": "hmall-internal", "sub": "1",
        "scope": ["catalog:read"], "exp": time.time() + 60}).encode()).rstrip(b"=").decode()
    signed = f"{header}.{payload}"
    signature = base64.urlsafe_b64encode(hmac.new(b"local-test-secret-with-adequate-length",
        signed.encode(), hashlib.sha256).digest()).rstrip(b"=").decode()
    token = f"{signed}.{signature}"
    assert "order:read" not in _verified_scopes(token)
    with pytest.raises(ValueError):
        _verified_scopes(f"{header}.A{payload[1:]}.{signature}")


def test_source_and_ticket_events_precede_completed_answer(monkeypatch):
    import asyncio
    import app.main as main

    async def fake_to_thread(function, request):
        return {"answer": "已创建排队工单", "sources": [{"policyId": 5, "version": 2,
                "title": "退货规则", "effectiveFrom": "2026-09-01T00:00:00"}],
                "ticket": {"ticketId": "ticket-1", "status": "QUEUED"}}

    sent = []

    async def fake_callback(request, sequence, event_type, data):
        sent.append((sequence, event_type, data))

    monkeypatch.setattr(main.asyncio, "to_thread", fake_to_thread)
    monkeypatch.setattr(main, "_callback", fake_callback)
    asyncio.run(main.process_run(RunRequest(conversationId="conv-1", runId="run-1",
                                          message="请转人工", delegationToken="signed")))
    assert [(sequence, event_type) for sequence, event_type, _ in sent] == [
        (1, "sources"), (2, "ticket"), (3, "completed")]


def test_signed_token_must_bind_to_requested_conversation_and_run(monkeypatch):
    from app.main import require_request_binding
    monkeypatch.setenv("HM_AGENT_TOKEN_SECRET", "local-test-secret-with-adequate-length")
    claims = {"aud": "hmall-internal", "sub": "1", "scope": ["catalog:read"],
              "conversation_id": "conversation-1", "run_id": "run-1", "exp": time.time() + 60}
    header = base64.urlsafe_b64encode(b'{"alg":"HS256"}').rstrip(b"=").decode()
    payload = base64.urlsafe_b64encode(json.dumps(claims).encode()).rstrip(b"=").decode()
    signed = f"{header}.{payload}"
    signature = base64.urlsafe_b64encode(hmac.new(b"local-test-secret-with-adequate-length",
        signed.encode(), hashlib.sha256).digest()).rstrip(b"=").decode()
    token = f"{signed}.{signature}"
    assert require_request_binding(RunRequest(conversationId="conversation-1", runId="run-1",
                                              message="查询商品", delegationToken=token)) == {"catalog:read"}
    with pytest.raises(ValueError):
        require_request_binding(RunRequest(conversationId="conversation-2", runId="run-1",
                                           message="查询商品", delegationToken=token))


def test_final_answer_requires_policy_evidence_and_persisted_ticket():
    from app.main import finalize_answer
    assert "无法确认" in finalize_answer("退货规则是什么", "支持七天退货", [], [])["answer"]
    policy = {"sources": [{"policyId": 5, "version": 2, "title": "退货规则",
                            "effectiveFrom": "2026-09-01", "excerpt": "凭购买凭证申请退货。"}],
              "needs_handoff": False}
    grounded = finalize_answer("退货规则是什么", "模型编造的退款承诺", [policy], [])
    assert "凭购买凭证申请退货" in grounded["answer"]
    assert "模型编造" not in grounded["answer"]
    indirect = finalize_answer("几天发货？", "明天肯定到", [], [])
    assert "明天肯定到" not in indirect["answer"]
    factual = finalize_answer("有哪些商品", "任意编造", [], [],
                              [{"kind": "items", "value": {"items": [{"id": 1, "name": "书包", "price": 1999}]}}])
    assert "书包" in factual["answer"] and "任意编造" not in factual["answer"]
    assert "尚未创建" in finalize_answer("请转人工", "已创建工单", [], [])["answer"]
    answer = finalize_answer("请转人工", "已创建工单", [],
                             [{"ticketId": "T-1", "status": "QUEUED"}])
    assert "T-1" in answer["answer"]
    assert answer["ticket"]["ticketId"] == "T-1"
