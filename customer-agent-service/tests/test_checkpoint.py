from langchain_core.language_models.fake_chat_models import FakeMessagesListChatModel
from langchain_core.messages import AIMessage
from langgraph.checkpoint.sqlite import SqliteSaver

from app.agent import build_agent


class ToolCapableFake(FakeMessagesListChatModel):
    def bind_tools(self, tools, **kwargs):
        return self


def test_conversation_state_restores_after_checkpointer_reopens(tmp_path):
    path = str(tmp_path / "checkpoint.sqlite3")
    with SqliteSaver.from_conn_string(path) as saver:
        agent = build_agent(ToolCapableFake(responses=[AIMessage(content="第一次答复")]),
                            object(), False, "第一次提问", checkpointer=saver)
        agent.invoke({"messages": [{"role": "user", "content": "第一次提问"}]},
                     config={"configurable": {"thread_id": "conversation-1"}})
    with SqliteSaver.from_conn_string(path) as reopened:
        state = reopened.get_tuple({"configurable": {"thread_id": "conversation-1"}})
        assert state is not None
        assert any(getattr(message, "content", None) == "第一次答复"
                   for message in state.checkpoint["channel_values"]["messages"])
