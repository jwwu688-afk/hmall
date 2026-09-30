from langchain_core.tools import tool

from app.java_client import JavaClient, JavaUnavailable


def policy_answer_state(excerpts: list[dict]) -> dict:
    sources = [{"policyId": row["policyId"], "version": row["version"],
                "title": row["title"], "effectiveFrom": row["effectiveFrom"],
                "category": row["category"], "excerpt": row["excerpt"]}
               for row in excerpts[:5]]
    if not sources:
        return {"sources": [], "needs_handoff": True,
                "notice": "没有找到已发布且生效的规则，无法确定答案；可以转人工咨询。"}
    if len(sources) > 1:
        return {"sources": sources, "needs_handoff": True,
                "notice": "检索到多条可能相关的规则，当前结论不确定；建议转人工核实。"}
    return {"sources": sources, "needs_handoff": False, "notice": "请仅依据这条已发布规则回答。"}


def make_policy_tool(client: JavaClient, collector: list[dict] | None = None):
    @tool("search_policies")
    def search_policies(query: str, category: str = "") -> dict:
        """查询已发布且生效的商城政策，并返回政策 ID、版本和生效时间。"""
        try:
            result = policy_answer_state(client.search_policies(query, category or None))
        except JavaUnavailable:
            result = {"sources": [], "needs_handoff": True,
                    "notice": "商城规则查询服务暂不可用，无法确定答案；建议转人工。"}
        if collector is not None:
            collector.append(result)
        return result

    return search_policies
