# 黑马商城智能客服 Agent 交接文档

更新日期：2026-09-30

交接分支：[`main`](https://github.com/jwwu688-afk/hmall/tree/main)

当前代码提交：以分支最新提交为准。

## 1. 一句话现状

第一期的**代码能力已实现并通过离线自动化测试**：商城买家可以使用客服页面咨询在售商品、本人订单和已有物流字段，检索已发布政策，并创建排队人工工单。独立 Java `customer-service` 负责会话、政策、工单和编排；五个业务微服务继续拥有领域数据；Python DeepAgents 只选择受限工具。**尚未完成真实模型与生产环境的端到端验收**，因此当前是可部署联调版，不应直接宣布上线。

## 2. 已完成的功能与位置

| 能力 | 当前实现 | 主要位置 |
| --- | --- | --- |
| 受控业务查询 | 仅查询在售商品、当前登录用户的订单与已有物流公司、单号；他人订单和不存在订单使用相同的未找到结果 | `customer-service/.../customer/query/`、`item-service`、`trade-service`、`user-service` |
| 会话与事件 | 游客密钥或登录身份绑定会话，消息幂等，MySQL 持久化消息和事件，SSE 按事件 ID 续读，Agent 运行失败有错误事件 | `customer-service/.../customer/chat/` |
| DeepAgents | 商品、订单、政策、工单专用工具与子 Agent；内部委托令牌绑定会话和运行，游客不装配订单工具 | `customer-agent-service/app/` |
| 有依据的答复 | 模型选择工具，对外文本由可信工具结果生成；政策答复显示已发布版本的 ID、标题、生效时间和原文片段；无可信结果时不转发模型自由文本 | `customer-agent-service/app/main.py`、`app/policy_tools.py` |
| 人工工单 | 显式转人工创建 `QUEUED` 工单，按会话和幂等键去重，可关联已验证为本人所有的订单；工单摘要只保存咨询主题和关联订单号 | `customer-service/.../customer/ticket/`、`customer-agent-service/app/ticket_tools.py` |
| 商城页面 | `/customer-service.html`，支持历史恢复、断线重连、政策来源卡片、工单状态和游客登录引导 | `frontend/html/hmall-portal/customer-service.html`、`js/customer-service.js`、`css/customer-service.css` |
| 数据迁移与部署材料 | Flyway V1 会话、V2 政策、V3 工单；Python Dockerfile、环境变量样例、评测样例与测试 | `customer-service/src/main/resources/db/migration/`、`customer-agent-service/` |

边界：Agent 不执行支付、取消订单、退款、退换货申请或修改地址；不提供实时人工接待。现有物流数据没有运输轨迹和预计送达时间。

## 3. 当前调用链和数据归属

```text
商城页面 /api/customer-service/...
        │
        ▼
Nginx → customer-service:8087（会话、SSE、政策、工单） → hm-customer
                    │                          │
                    │ Feign + 内部服务密钥     ├→ item-service
                    │                          ├→ trade-service
                    │                          └→ user-service
                    │ 签发短时限域内部令牌
                    ▼
          customer-agent-service（DeepAgents、SQLite 检查点）
                    │ 带令牌调用受控接口并回调
                    └──────────────────────────► customer-service
```

- 浏览器只访问 Java 对外接口；Python 的 `/internal/runs` 仅供 Java 调用，不应公开到互联网。
- 业务服务用 `HM_INTERNAL_SERVICE_SECRET` 保护内部端点；Java/Python 用 `HM_AGENT_SERVICE_SECRET` 验证服务间请求，委托令牌用 `HM_AGENT_TOKEN_SECRET` 签名，并携带范围、主体、会话 ID、运行 ID 和有效期。订单归属最终由 `trade-service` 再次校验。
- MySQL 保存 `customer_conversation`、`customer_message`、`customer_run`、`customer_event`、`customer_policy`、`customer_ticket`；Python 的 SQLite 文件保存 Agent 检查点。两类持久化都需要备份和持久卷规划。
- Nginx 将 `/api/customer-service/**` 优先转给 8087，并关闭缓冲、设置 300 秒读超时；页面使用的外部契约保持不变。

## 4. 接手人如何运行

1. 获取 `main` 分支。项目使用 Java 11、Maven、MySQL 和 Python 3.12。当前机器的 Java/Maven 与 Python 虚拟环境、缓存尽量放在 D 盘；这些本地工具目录没有提交到 Git。
2. 准备 Nacos、五个业务数据库和 `hm-customer` 并先备份。客服 Flyway V1～V3 只创建客服表，不创建原商城业务表。
3. 配置数据库连接和 `HM_INTERNAL_SERVICE_SECRET`；为 `user-service` 配置 `HM_JWT_PASSWORD` 及本地 `hmall.jks`；为客服 Java 配置 `HM_AGENT_TOKEN_SECRET`、`HM_AGENT_SERVICE_SECRET`、`HM_AGENT_URL`，并按环境需要调整 `HM_AGENT_CONNECT_TIMEOUT`、`HM_AGENT_READ_TIMEOUT`（默认 2 秒/5 秒）。不要提交密钥库或含密码的本地 profile。
4. 参考 [Python 环境样例](../../customer-agent-service/.env.example) 创建被 Git 忽略的 `customer-agent-service/.env.local`，配置 `HM_AGENT_MODEL`、`HM_AGENT_MODEL_BASE_URL`、`HM_AGENT_MODEL_API_KEY`、`HM_JAVA_BASE_URL`、`HM_AGENT_CHECKPOINT_DB`，并使两项内部密钥与 Java 一致。不要把实际密钥发到聊天或提交到仓库。
5. 启动顺序：Nacos/MySQL → 五个业务服务 → `customer-service` → Python Agent → Nginx。客服 Java 默认 `8087`；Python 默认 `127.0.0.1:8001`；商城门户默认 `18080`。

常用命令（先按本机环境设置变量，并把依赖缓存放在 D 盘）：

```powershell
# 仓库根目录
mvn clean test
mvn -DskipTests package
Set-Location customer-agent-service
# 使用 Python 3.12 虚拟环境
python -m pip install -e ".[test]"
python -m pytest tests -q
python -m uvicorn app.main:app --host 127.0.0.1 --port 8001
```

更完整的启动、部署说明见 [Python 服务说明](../../customer-agent-service/README.md) 和 [根目录说明](../../README.md)。

## 5. 已验证结果与未验证范围

截至 2026-09-30：十模块 `mvn clean test` 成功，Java **38 项执行、0 失败、0 错误、1 项旧数据库写入场景跳过**；十模块打包成功；Python **20 项通过**；前端 3 项契约测试与 `node --check` 通过。详细证据见 [验证记录](../testing/customer-agent-v1-verification.md)。

以下仍须在接手环境补验，不能将自动化测试视为它们的替代：

- 尚未提供可用模型配置，因此**真实模型工具选择、政策来源卡片、准确率和延迟**未做完整端到端验证。
- 仓库不包含真实商家政策。应由业务负责人审核、发布现行版本，再验证政策检索与答复；不要让 Agent 自行编造平台规则。
- 本机没有 Nginx 可执行文件，尚未运行 `nginx -t`。在部署环境验证 SSE 代理、断线续读与超时设置。
- 页面验收清单尚未逐项在真实模型、登录用户和故障注入环境走完；见 [前端验收清单](../../frontend/README-customer-service.md)。
- 当前只有排队工单记录和状态查询，没有人工客服工作台、分配、通知或实际处理流程。

## 6. 下一步任务（按优先级）

### P0：完成第一期上线验收

1. **真实模型联调。** 配置模型名称、兼容 API 地址与密钥；以 `customer-agent-service/evals/core_cases.jsonl` 为样例建立并运行真实模型评测脚本，记录工具调用、事实准确率、失败率与响应时间。当前仓库只有样例结构校验，没有自动执行真实模型的评测器。重点检查商品、订单、物流、规则、工单和提示词注入场景。
2. **发布真实规则。** 明确规则负责人和发布流程，录入配送、支付、退换货政策的版本与生效时间；检查过期、冲突和空结果行为。
3. **部署验证。** 在目标环境运行数据库备份与 Flyway 迁移、`nginx -t`、双服务重启、SSE 断线续读、工单重复提交、他人订单越权以及服务超时测试。补充日志、告警和备份恢复演练。

### P1：完成微服务运维能力

1. 引入统一网关与配置中心管理，同时保持页面 `/api/customer-service/...` 契约。
2. 为 Feign 和 Agent 调用补齐指标、告警、链路追踪、限流、熔断与部署配置。
3. 完成客服历史数据迁移、备份恢复演练及跨服务端到端测试后再切换生产流量。

### P2：完善客服运营能力

1. 建人工工单工作台、领取与分配、状态变更、处理记录和通知；页面展示真实处理进度，不承诺实时接待。
2. 加入商品卡片、订单状态中文映射、必要的澄清问答及客服满意度反馈。继续保证事实来自业务接口。
3. 建立版本化评测集、回归门槛、数据脱敏审计、限流和运行监控；根据真实数据优化工具选择与响应性能。

### P3：评估新增 Agent 操作能力

在业务服务具备正式流程后，再分别设计退换货申请、退款、取消订单、地址修改、优惠券建议、实时物流轨迹等能力。每项写操作都需要服务端规则校验、明确的用户确认、幂等、审计和失败补偿；不能只加提示词或工具名就开放。

## 7. 接手时特别注意

- [设计文档](../superpowers/specs/2026-09-28-ecommerce-customer-agent-design.md) 和 [核心实施计划](../superpowers/plans/2026-09-28-ecommerce-agent-core.md)、[规则与工单计划](../superpowers/plans/2026-09-28-ecommerce-agent-policy-handoff.md) 记录了设计过程，部分措辞与最终实现不完全一致；**以当前代码、迁移脚本和本交接文档的实测状态为准**。
- `application-dev.yaml`、`hmall.jks`、`.env.local`、本地 MySQL 数据和 D 盘工具链都被忽略或仅存在于开发机，克隆仓库后须在目标环境自行准备。
- `hm-service` 仍在 reactor 中用于兼容原项目，但新客服调用链使用五个业务服务与独立 `customer-service`；不要再把客服代码迁回单体。
