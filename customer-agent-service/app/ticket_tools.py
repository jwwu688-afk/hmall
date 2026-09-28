from langchain_core.tools import tool

from app.java_client import JavaClient, JavaUnavailable


def make_ticket_tool(client: JavaClient, conversation_id: str, run_id: str):
    @tool("create_handoff_ticket")
    def create_handoff_ticket(reason: str, order_id: int | None = None) -> dict:
        """用户明确要求人工处理时创建排队工单；成功必须有已保存的工单编号。"""
        try:
            return client.create_ticket(conversation_id, reason, order_id, run_id)
        except (JavaUnavailable, PermissionError, ValueError) as exc:
            return {"status": "FAILED", "error": str(exc)}

    return create_handoff_ticket
