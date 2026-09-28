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
