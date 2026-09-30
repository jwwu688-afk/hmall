import json
from pathlib import Path

from app.agent import tool_names_for_request


def test_core_eval_cases_define_authorization_and_verifiable_facts():
    path = Path(__file__).parents[1] / "evals" / "core_cases.jsonl"
    cases = [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]
    assert len(cases) >= 8
    assert len({case["id"] for case in cases}) == len(cases)
    required = {"商品事实", "本人订单", "他人订单", "游客订单", "无物流记录", "伪造身份", "Java超时", "断线重连",
                "规则来源", "规则冲突", "转人工成功", "转人工失败"}
    assert required.issubset({case["category"] for case in cases})
    for case in cases:
        assert case["expected_tools"]
        assert case["ownership_result"]
        assert case["fact_assertions"]
        assert case["forbidden_fields"]
        if not case["authenticated"]:
            assert not any("order" in tool or "logistics" in tool for tool in tool_names_for_request(False))
