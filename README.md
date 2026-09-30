# 黑马商城微服务与智能客服

本仓库是 Java 11 / Spring Boot 2.7.12 的黑马商城示例项目。商城业务已拆为五个服务，并新增独立 Java `customer-service` 与 Python `customer-agent-service`。根 Maven reactor 仍保留原 `hm-service`，用于兼容和对照；客服生产调用链不再依赖它。

## 模块与端口

| 模块 | 端口 | 数据库 | 职责 |
| --- | ---: | --- | --- |
| `item-service` | 8081 | `hm-item` | 商品 |
| `cart-service` | 8082 | `hm-cart` | 购物车 |
| `user-service` | 8084 | `hm-user` | 用户、登录与 JWT 身份解析 |
| `trade-service` | 8085 | `hm-trade` | 订单与物流字段 |
| `pay-service` | 8086 | `hm-pay` | 支付 |
| `customer-service` | 8087 | `hm-customer` | 客服会话、事件、政策、工单与 Agent 编排 |
| `customer-agent-service` | 8001 | SQLite 检查点 | DeepAgents 与受限工具 |

服务通过 Nacos（默认 `localhost:8848`）发现。`customer-service` 只通过 Feign 访问商品、交易和用户服务，不读取这些服务的数据库；Python Agent 只调用 `customer-service` 的 `/internal/customer/**`，不直连商城数据库。

## 本地准备与启动

1. 准备 Nacos、MySQL，以及 `hm-item`、`hm-cart`、`hm-user`、`hm-trade`、`hm-pay`、`hm-customer` 数据库。五个业务库使用原项目拆分后的表结构；`hm-customer` 由 `customer-service/src/main/resources/db/migration/` 中的 Flyway V1～V3 创建客服表。迁移前先备份数据库。
2. 用环境变量或被 Git 忽略的本地配置提供数据库口令。`customer-service` 使用 `HM_DB_HOST`、`HM_DB_USERNAME`、`HM_DB_PASSWORD`；其他服务沿用各模块的 `hm.db.host`、`hm.db.pw` 配置。
3. 为 `user-service` 准备本地 `user-service/src/main/resources/hmall.jks`，并设置 `HM_JWT_PASSWORD`。密钥库、`application-dev.yaml`、`application-local.yaml` 和 `.env.local` 均被 Git 忽略，禁止提交。
4. 所有访问内部客服端点的 Java 服务设置相同的 `HM_INTERNAL_SERVICE_SECRET`。`customer-service` 与 Python Agent 还需共享 `HM_AGENT_TOKEN_SECRET`（至少 32 字符）和 `HM_AGENT_SERVICE_SECRET`；Java 使用 `HM_AGENT_URL`（默认 `http://127.0.0.1:8001`），并可用 `HM_AGENT_CONNECT_TIMEOUT`、`HM_AGENT_READ_TIMEOUT` 调整 Agent HTTP 连接/读取超时（默认 2 秒/5 秒）。
5. 按 Nacos/MySQL → 五个业务服务 → `customer-service` → Python Agent → Nginx 的顺序启动。Python 模型与检查点配置见 [Agent 服务说明](customer-agent-service/README.md)。

构建与测试：

```powershell
mvn clean test
mvn -DskipTests package
Set-Location customer-agent-service
python -m pip install -e ".[test]"
python -m pytest tests -q
```

## 前端

`frontend` 是静态 HTML/CSS/JavaScript，无 npm 构建步骤。将 `frontend/html` 与 `frontend/conf` 放入 Nginx 对应目录后，商城门户默认监听 `http://localhost:18080/`。通用 `/api` 仍代理到兼容后端 8080，客服路径优先代理到 `customer-service:8087`。

## 智能客服（第一期）

商城门户 `/customer-service.html` 支持游客商品与公开规则咨询，以及登录用户的本人订单、现有物流信息和排队人工工单。外部地址保持 `/api/customer-service/**`；Nginx 将它单独代理到 8087，并为 SSE 关闭缓冲、保留 `after` 事件游标。

Agent 仅装配商品、本人订单/物流、已发布政策和创建工单工具，不开放任意 HTTP、SQL、文件、支付、退款、取消订单或地址修改。订单归属在 `trade-service` 再校验；工单只有在 Java 返回持久化编号和 `QUEUED` 后才显示成功。模型缺失或失败会产生可重试错误事件，不会伪造工具结果。

当前自动化验证结果及尚未完成的真实模型/部署验收见 [第一版验证记录](docs/testing/customer-agent-v1-verification.md)。开发交接与后续优先级见 [Agent 交接文档](docs/handoffs/2026-09-29-customer-agent-handoff.md)。
