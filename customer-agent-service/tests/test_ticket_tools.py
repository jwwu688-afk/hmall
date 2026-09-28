import httpx

from app.java_client import JavaClient
from app.ticket_tools import make_ticket_tool


def test_ticket_tool_uses_run_id_as_idempotency_key_and_returns_persisted_id():
    seen = {}

    def handler(request):
        seen["body"] = request.content.decode()
        return httpx.Response(200, json={"ticketId": "ticket-1", "status": "QUEUED"})

    client = JavaClient("http://java", "signed", httpx.Client(transport=httpx.MockTransport(handler)))
    tool = make_ticket_tool(client, "conversation-1", "run-1")
    result = tool.invoke({"reason": "需要人工", "order_id": None})
    assert result["ticketId"] == "ticket-1"
    assert result["status"] == "QUEUED"
    assert '"idempotencyKey":"run-1"' in seen["body"]


def test_ticket_tool_does_not_claim_success_on_server_failure():
    client = JavaClient("http://java", "signed", httpx.Client(
        transport=httpx.MockTransport(lambda _: httpx.Response(503))))
    tool = make_ticket_tool(client, "conversation-1", "run-1")
    result = tool.invoke({"reason": "需要人工", "order_id": None})
    assert result["status"] == "FAILED"
    assert "ticketId" not in result
