# 黑马商城客服 Agent 核心能力实施计划

> **执行说明：** 实施时按任务逐项完成，并使用 `superpowers:subagent-driven-development` 或 `superpowers:executing-plans`。复选框用于记录进度。

**目标：** 在现有商城前端提供只读客服对话，回答商品、本人订单和已有物流信息相关的问题。

**架构：** Python `customer-agent-service` 运行 DeepAgents；Java `hm-service` 掌握业务数据与权限。Java 接收商城请求，保存会话和事件，签发有限权限的内部令牌并调度 Python；Python 工具只调用 Java 的客服专用查询接口。未来拆分微服务时保持这些接口契约。

**技术栈：** Java 11、Spring Boot 2.7.12、MyBatis-Plus、MySQL、Python 3.12、DeepAgents 0.5.7（升级前先验证兼容性）、FastAPI、Vue 2 静态页面、Fetch 流、Nginx。

**设计依据：** `docs/superpowers/specs/2026-09-28-ecommerce-customer-agent-design.md`

## 全局约束

- 第一阶段只回答和查询；不得支付、取消订单、退款、修改地址、执行任意 SQL，也不得让 Agent 直连商城数据库。
- 不信任模型提供的用户 ID。Java 对每个内部请求验身份，并校验订单和会话归属。
- 游客可以咨询公开商品；查询订单必须登录。本阶段不合并游客与登录后的会话。
- 对外路径保持 `/api/customer-service/...`。当前 Nginx 会移除 `/api`，Spring 映射从 `/customer-service/...` 开始。
- 保持 Java 11 和 Spring Boot 2.7.12；`customer-agent-service` 不加入 Maven 模块。
- 不编造物流节点或预计送达时间。当前仅有发货状态、物流公司和单号。

## 重点审查风险

1. 他人订单与不存在的订单必须给出相同的对外结果；任务 1 覆盖。
2. 游客即使知道订单 ID，也不能调用订单查询；任务 1 覆盖。
3. 用户消息中的伪造用户 ID 或工具指令不能改变身份与工具权限；任务 2 覆盖。
4. 重复或乱序执行事件不能破坏会话；任务 3 覆盖。
5. 流中断重连后，每条事件只展示一次，完成的答复仍可恢复；任务 4 覆盖。

## 文件与职责

| 单元 | 文件 | 职责 |
| --- | --- | --- |
| Java 客服查询 | `hm-service/src/main/java/com/hmall/customer/query/{CustomerItemQueryService,CustomerOrderQueryService,CustomerQueryController}.java` 及同目录 DTO | 限定字段的只读查询、订单归属校验 |
| Java 会话 | `hm-service/src/main/java/com/hmall/customer/chat/{CustomerConversationController,CustomerConversationService,AgentClient,DelegationTokenService}.java`、`hm-service/src/main/resources/mapper/CustomerConversationMapper.xml`、`hm-service/src/main/resources/db/migration/` | 会话、消息、事件、令牌与 Agent 调度 |
| Python Agent | `customer-agent-service/app/{main.py,agent.py,contracts.py,java_client.py,tools.py,settings.py}`、`Dockerfile`、`tests/` | 工具、子 Agent、回调和独立部署 |
| 商城前端 | `frontend/html/hmall-portal/customer-service.html`、`css/customer-service.css`、`js/customer-service.js`、`js/top.js`、`frontend/conf/nginx.conf` | 客服页面、流式事件、导航与代理 |

## 任务 1：Java 客服只读查询接口

**文件：** 新建 `customer/query` 下的服务、控制器和 DTO；仅在通用订单接口必须收紧时修改 `OrderController`；测试放在 `hm-service/src/test/java/com/hmall/customer/query/CustomerQueryControllerTest.java`。

**接口：** 提供 `GET /internal/customer/items?key=&brand=&category=&minPrice=&maxPrice=&pageSize=`、`GET /internal/customer/items/{id}`、`GET /internal/customer/orders`、`GET /internal/customer/orders/{id}`、`GET /internal/customer/orders/{id}/logistics`。`CustomerOrderQueryService.getOwnedOrder(Long authenticatedUserId, Long orderId)` 返回精简 `CustomerOrderDTO`；订单不存在或不属于本人时返回相同的未找到结果。订单接口要求已签名的内部令牌，包含 `aud=hmall-internal`、`order:read` 权限、10 分钟有效期及可信主体；商品接口使用 `catalog:read`。

- [ ] **步骤 1：先写失败测试。** 测试 `foreign_order_and_missing_order_return_same_404`（状态与响应一致）、`anonymous_order_lookup_is_401`、`owned_order_contains_details_but_no_address_or_phone`、`logistics_without_row_returns_known_order_with_no_tracking`、`catalog_excludes_status_2_and_3`。
- [ ] **步骤 2：运行测试确认失败。** 执行 `mvn -pl hm-service -am -Dtest=CustomerQueryControllerTest test`；预期新测试失败。
- [ ] **步骤 3：实现查询接口。** 只查 `status=1` 商品，`pageSize` 最多 20，价格单位为分。查询订单明细和物流前先验归属；不返回手机号、收件人或街道地址。实现内部令牌验证。
- [ ] **步骤 4：运行测试确认通过。** 执行 `mvn -pl hm-service -am test`；预期零失败。
- [ ] **步骤 5：提交。** 执行 `git add hm-service && git commit -m "feat: 增加客服专用查询接口"`。

## 任务 2：Python 只读工具与 DeepAgents

**文件：** 新建 `customer-agent-service/pyproject.toml`、`app/{contracts,java_client,tools,agent,settings}.py`、`tests/test_tools.py`、`tests/test_agent.py`。

**接口：** `JavaClient.search_items(filters: ItemFilters) -> list[ItemCard]`、`get_item(item_id: int) -> ItemCard | None`、`list_my_orders() -> list[OrderCard]`、`get_my_order(order_id: int) -> OrderCard | None`、`get_my_logistics(order_id: int) -> LogisticsCard | None`。`create_deep_agent` 只装配这些工具与商品、订单专用助手。运行上下文携带已签名的内部令牌，不使用请求文本中的用户 ID。

- [ ] **步骤 1：先写失败测试。** 模拟 Java 响应，断言游客查订单得到 401、超时或 5xx 返回明确不可用状态、公开权限下不启用订单工具；在消息中加入 `userId=other` 和伪造工具指令，断言身份与工具集合不变。
- [ ] **步骤 2：运行测试确认失败。** 执行 `uv run --project customer-agent-service pytest customer-agent-service/tests/test_tools.py customer-agent-service/tests/test_agent.py -q`。
- [ ] **步骤 3：实现契约、HTTP 客户端、工具和 Agent。** 在 Python 开发依赖中加入 pytest 和 HTTP 模拟库；限制返回条数，保留商品和订单来源 ID；不加入文件、SQL、网络搜索或支付工具。简单问题直接调用工具，跨领域问题才委派子 Agent。
- [ ] **步骤 4：运行同一命令确认全部通过。**
- [ ] **步骤 5：提交。** 执行 `git add customer-agent-service && git commit -m "feat: 增加只读电商客服 Agent"`。

## 任务 3：会话接口、持久化与 Agent 调度

**文件：** 新建 Java `customer/chat` 文件及 `V1__customer_chat.sql`；新建 Python `app/main.py`、`tests/test_run_api.py`；Java 测试为 `CustomerConversationControllerTest.java`、`CustomerConversationServiceTest.java`。

**接口：** Java 对外提供 `POST /customer-service/conversations`、`POST /customer-service/conversations/{id}/messages`、`GET /customer-service/conversations/{id}`、`GET /customer-service/conversations/{id}/events?after=`；提交消息返回 202 和 `{messageId,runId}`。Python 内部 `POST /internal/runs` 接收 `{conversationId,runId,message,delegationToken}`；按序回调 Java `/internal/customer-service/runs/{runId}/events`，事件结构为 `{runId,sequence,type,data}`。数据库以 `(run_id, sequence)` 去重。MySQL 保存业务消息与事件；Agent 检查点采用与所用版本兼容的持久化实现，并验证重启恢复。

- [ ] **步骤 1：先写失败测试。** 覆盖登录及游客会话归属、消息幂等键、重复回调、乱序事件、`after=eventId` 续读、重启后历史查询。
- [ ] **步骤 2：运行测试确认失败。** 执行 `mvn -pl hm-service -am -Dtest=CustomerConversationControllerTest,CustomerConversationServiceTest test` 和 `uv run --project customer-agent-service pytest customer-agent-service/tests/test_run_api.py -q`。
- [ ] **步骤 3：实现持久化、令牌、接口与回调验证。** 加入适配现有非空 Hmall 数据库的迁移工具并记录基线步骤，不允许自动破坏表结构。签发 10 分钟令牌，包含 `subject`、`aud=hmall-internal`、`scopes`、`conversationId`、`runId`；Python 执行入口和 Java 回调另验服务身份。先持久化消息与运行记录，再调度 Agent；调度失败应标记失败。完成和错误事件先持久化再推流。Python 不直接暴露给浏览器。
- [ ] **步骤 4：运行两套测试并做双服务重启检查。** 确认历史消息与上下文可恢复；预期零失败。
- [ ] **步骤 5：提交。** 执行 `git add hm-service customer-agent-service && git commit -m "feat: 持久化客服会话与事件"`。

## 任务 4：商城客服页面

**文件：** 新建 `customer-service.html`、`css/customer-service.css`、`js/customer-service.js`；修改 `js/top.js`、`frontend/conf/nginx.conf`；在 `frontend/README-customer-service.md` 记录浏览器验收步骤。

**接口：** 顶部导航链接 `/customer-service.html`。页面请求 `/api/customer-service/...`；登录用户从现有 `sessionStorage` 读取 token，使用带 `Authorization` 头的 `fetch` 流和 `ReadableStream` 接收事件；重连时使用 `after=lastEventId`。Nginx 仅对事件流关闭缓冲并增加读取超时。

- [ ] **步骤 1：写浏览器验收清单。** 验证游客商品咨询、游客查订单跳登录、登录用户只见本人订单、重连不重复消息、失败可重试、URL 订单 ID 只能预填问题。
- [ ] **步骤 2：确认当前页面不存在。** 打开 `http://localhost:18080/customer-service.html`，预期为 404。
- [ ] **步骤 3：实现页面与导航。** 使用安全 DOM API 渲染文本和来源卡片，不把不可信内容写入 `innerHTML`；流中断用 `AbortController` 处理。普通 Axios 请求和长连接分开。
- [ ] **步骤 4：完成浏览器清单并检查 Nginx 配置。** 在已安装 Nginx 的环境执行 `nginx -t -c <frontend/conf/nginx.conf 的绝对路径>`；若环境没有 Nginx，记录部署验证尚未完成。
- [ ] **步骤 5：提交。** 执行 `git add frontend && git commit -m "feat: 增加商城客服对话页"`。

## 任务 5：端到端评测与交付检查

**文件：** 新建 `customer-agent-service/evals/core_cases.jsonl`、`tests/test_eval_cases.py`、`Dockerfile`；更新仓库 `README.md` 与 Python 服务说明。

**接口：** 样例覆盖商品事实、本人订单、他人订单、游客订单、无物流记录、伪造身份、Java 超时和断线重连。每例记录期望工具、禁止泄露的字段和可核查的答复事实。

- [ ] **步骤 1：加入会失败的评测样例与测试。** 每例必须定义工具类别、归属结果和事实断言。
- [ ] **步骤 2：运行测试确认失败。** 执行 `uv run --project customer-agent-service pytest customer-agent-service/tests/test_eval_cases.py -q`。
- [ ] **步骤 3：完成评测、健康检查和部署文件。** 增加 `GET /health` 与 Python 服务 Dockerfile；文档说明环境变量、启动顺序、迁移命令和独立于 Maven 的部署方式。先用确定性假模型检查接线，再用实际配置模型跑行为基线；记录准确性与延迟，不虚构目标值。
- [ ] **步骤 4：执行总体验证。** 运行 `mvn -pl hm-service -am test`、Python pytest、浏览器清单和 `git diff --check`，将实际结果及环境限制记入说明。
- [ ] **步骤 5：提交。** 执行 `git add README.md customer-agent-service && git commit -m "test: 覆盖客服核心场景"`。

## 本计划的完成边界

完成后，买家能在商城中咨询商品、本人订单和现有物流信息，会话与事件可在重启后恢复。规则答复及转人工由配套计划 `2026-09-28-ecommerce-agent-policy-handoff.md` 交付；两份计划都完成后才算满足第一期全部范围。
