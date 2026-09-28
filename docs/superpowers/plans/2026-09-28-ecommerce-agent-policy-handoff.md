# 黑马商城客服 Agent 规则答复与转人工实施计划

> **执行说明：** 实施时按任务逐项完成，并使用 `superpowers:subagent-driven-development` 或 `superpowers:executing-plans`。复选框用于记录进度。

**目标：** 在客服核心能力之上，增加可追溯的规则答复和真实持久化的人工工单，完成第一期范围。

**架构：** Java 保存已发布且带版本的客服规则和工单；Python DeepAgents 增加规则助手与受限的建单工具；商城页面展示政策来源和工单状态。本计划在 `2026-09-28-ecommerce-agent-core.md` 验收后实施。

**技术栈：** Java 11、Spring Boot 2.7.12、MyBatis-Plus、MySQL、Python 3.12、DeepAgents、FastAPI、Vue 2 静态页面。

**设计依据：** `docs/superpowers/specs/2026-09-28-ecommerce-customer-agent-design.md`

## 全局约束

- 不自动执行退货、退款、取消订单、修改地址或支付。
- 规则答复必须指向已发布政策的 ID、版本和生效时间；资料缺失或互相冲突时说明不确定，并提供转人工入口。
- 转人工表示创建排队工单，不承诺在线实时接待。
- Java 返回已持久化的工单 ID 前，不得提示建单成功。同一幂等键的重试只能对应一张工单。
- 工单关联的订单必须属于当前登录用户；游客工单不能附订单 ID。

## 重点审查风险

1. 旧政策与当前生效政策不能同时被当作现行规则引用；任务 1 覆盖。
2. 有冲突的政策片段不能得出确定承诺；任务 2 覆盖。
3. 网络超时重试不能创建两张工单；任务 3 覆盖。
4. 他人订单 ID 不能被关联到工单，也不能透露订单所有人；任务 3 覆盖。
5. 工单持久化失败时，页面必须说明失败并允许重试；任务 4 覆盖。

## 文件与职责

| 单元 | 文件 | 职责 |
| --- | --- | --- |
| 已发布政策 | `hm-service/src/main/java/com/hmall/customer/policy/{CustomerPolicy,CustomerPolicyController,CustomerPolicyService}.java`、迁移 `V2__customer_policy.sql` | 规则版本与检索 |
| Agent 规则工具 | `customer-agent-service/app/{policy_tools.py,agent.py}` 及测试 | 结构化检索与带来源答复 |
| 人工工单 | `hm-service/src/main/java/com/hmall/customer/ticket/{CustomerTicket,CustomerTicketController,CustomerTicketService}.java`、迁移 `V3__customer_ticket.sql`、Python `app/ticket_tools.py` | 排队工单与会话摘要 |
| 商城前端 | `frontend/html/hmall-portal/{customer-service.html,js/customer-service.js,css/customer-service.css}` | 来源卡片与建单反馈 |

## 任务 1：已发布政策的存储与检索

**文件：** 新建政策模型、迁移、服务和内部控制器；测试 `hm-service/src/test/java/com/hmall/customer/policy/CustomerPolicyServiceTest.java`。

**接口：** `GET /internal/customer/policies?query=&category=` 最多返回五条 `PolicyExcerpt`，字段为 `policyId`、`version`、`title`、`effectiveFrom`、`category`、`excerpt`。只返回 `PUBLISHED`、已生效且同一政策键下没有更新生效版本的记录。类别为配送、支付、退货、换货。商家规则由授权人员通过受控迁移或管理流程维护，Agent 不能修改。

- [ ] **步骤 1：先写失败测试。** 覆盖 `only_current_published_version_is_returned`、`future_policy_is_hidden`、`empty_results_are_explicit`、`query_is_bounded_to_five`、`malicious_policy_text_is_returned_as_data_only`。
- [ ] **步骤 2：运行测试确认失败。** 执行 `mvn -pl hm-service -am -Dtest=CustomerPolicyServiceTest test`。
- [ ] **步骤 3：实现模型、表结构、查询和发布校验。** 政策键与版本选择规则必须确定，限制查询结果数量。仅为测试或演示加入少量样例；真实商家政策发布前由负责人审核。
- [ ] **步骤 4：运行 `mvn -pl hm-service -am test`，确认零失败。**
- [ ] **步骤 5：提交。** 执行 `git add hm-service && git commit -m "feat: 增加已发布客服规则"`。

## 任务 2：规则助手与有依据的答复

**文件：** 新建 `customer-agent-service/app/policy_tools.py`，修改 `app/agent.py`，新增 `tests/test_policy_agent.py`。

**接口：** `search_policies(query: str, category: str | None) -> list[PolicyExcerpt]`。规则助手返回答复依据以及政策 ID、版本、生效时间；主 Agent 在答复卡片中展示引用。无结果或相互矛盾时返回 `needs_handoff=true`，不得承诺退货或退款结果。

- [ ] **步骤 1：先写失败测试。** 单条现行政策应产生来源卡片；无政策应说明不确定；两条矛盾政策应提供转人工；片段中即使出现“忽略先前指令”，也不能增加工具或改变身份。
- [ ] **步骤 2：运行测试确认失败。** 执行 `uv run --project customer-agent-service pytest customer-agent-service/tests/test_policy_agent.py -q`。
- [ ] **步骤 3：实现规则工具和助手。** 仅把结构化片段交给模型；保留商品和订单工具的权限边界；商家规则缺失时不使用公开网络搜索补充。
- [ ] **步骤 4：运行同一测试及核心 Python 测试，确认零失败。**
- [ ] **步骤 5：提交。** 执行 `git add customer-agent-service && git commit -m "feat: 基于已发布规则回答客服问题"`。

## 任务 3：排队人工工单

**文件：** 新建 Java 工单模型、控制器、服务和迁移；Python `app/ticket_tools.py`；测试 `CustomerTicketServiceTest.java`、`test_ticket_tools.py`。

**接口：** 对外 `POST /customer-service/conversations/{id}/handoff` 接收 `{reason,orderId?,idempotencyKey}`，依据已保存的会话生成摘要。内部 `POST /internal/customer/tickets` 接收 `{conversationId,reason,summary,orderId?,idempotencyKey}`，成功返回 `{ticketId,status:"QUEUED"}`。Java 校验会话和可选订单归属，裁剪个人信息，并对 `(conversationId,idempotencyKey)` 加唯一约束。`GET /customer-service/conversations/{id}/ticket` 向会话所有者返回编号和状态。本阶段状态为 `QUEUED`、`IN_PROGRESS`、`RESOLVED`。

- [ ] **步骤 1：先写失败测试。** 重复请求返回相同编号；他人订单被拒绝；游客不能附订单；数据库失败不能发成功事件；摘要保留意图和必要事实，但不含令牌、密码、手机号或完整地址。
- [ ] **步骤 2：运行测试确认失败。** 执行 `mvn -pl hm-service -am -Dtest=CustomerTicketServiceTest test` 和 `uv run --project customer-agent-service pytest customer-agent-service/tests/test_ticket_tools.py -q`。
- [ ] **步骤 3：实现 Java 持久化与 Python 建单工具。** 建单是 Agent 唯一新增的写操作。缺少必要信息时最多追问一次；用户明确要求人工时可直接建单。
- [ ] **步骤 4：运行 Java/Python 测试并模拟超时重试。** 预期仅有一张已持久化工单，状态准确。
- [ ] **步骤 5：提交。** 执行 `git add hm-service customer-agent-service && git commit -m "feat: 增加客服转人工工单"`。

## 任务 4：商城页面的规则来源与工单状态

**文件：** 修改 `customer-service.html`、`js/customer-service.js`、`css/customer-service.css`；扩展 `frontend/README-customer-service.md` 和评测样例。

**接口：** 在规则答复旁展示标题、版本和生效时间；仅在建单成功后展示工单编号与 `QUEUED` 状态。建单失败时显示重试入口，不暗示已有实时人工接待。

- [ ] **步骤 1：增加会失败的浏览器与评测用例。** 检查来源卡片、冲突时转人工、成功时编号、失败时重试和刷新后的工单状态。
- [ ] **步骤 2：在核心版页面运行用例。** 预期规则与工单相关断言失败。
- [ ] **步骤 3：实现界面状态并扩展评测样例。** 商家政策中的 HTML 或脚本不得作为页面代码执行。
- [ ] **步骤 4：运行浏览器清单、Java/Python 全量测试、第一期评测及 `git diff --check`。** 记录实际结果，确认无跨用户数据泄露和虚构的物流节点。
- [ ] **步骤 5：提交。** 执行 `git add frontend customer-agent-service && git commit -m "feat: 展示规则依据和工单状态"`。

## 本计划的完成边界

核心计划和本计划的集成、归属、规则、工单及浏览器检查全部通过，才算完成设计文档中的第一期。退换货和退款等交易操作需要另行设计。
