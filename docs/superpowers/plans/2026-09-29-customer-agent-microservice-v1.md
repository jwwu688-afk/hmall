# Customer Agent 微服务第一版实施计划

> **执行方式：** Native（由当前 Codex 会话按任务顺序实现、验证、提交、合并并推送）。

**目标：** 在保留五个业务微服务拆分成果的基础上，把现有客服 Agent 从旧 `hm-service` 迁移成独立、可运行的第一版客服服务，并在完整验证后合并到 `main`、推送到 `git@github.com:jwwu688-afk/hmall.git`。

**设计依据：** `docs/superpowers/specs/2026-09-28-ecommerce-customer-agent-design.md` 与 `docs/handoffs/2026-09-29-customer-agent-handoff.md`。现有 Python Agent 的工具协议和外部 `/api/customer-service/**` 契约保持兼容；不新增支付、退款、取消订单或修改地址能力。

**目标架构：** 五个业务微服务继续负责各自领域数据；新增 Java `customer-service` 负责会话、事件、政策、工单、用户身份解析及 Agent 编排边界；现有 Python `customer-agent-service` 继续只通过受控内部接口调用 Java。`customer-service` 使用 Feign 调用 `item-service`、`trade-service`、`user-service`，不直连它们的数据库。Nginx 只公开 Java 客服入口，Python 服务保持内部部署。

**技术栈：** Java 11、Spring Boot 2.7.12、Spring Cloud OpenFeign、MyBatis-Plus、Flyway、MySQL、Nacos、Python 3.12、FastAPI、DeepAgents/LangGraph、Vue 2/Nginx。

---

## Task 1：建立集成基线并保护现有拆分成果

**涉及文件：**

- 修改：`pom.xml`
- 检查：`item-service/`、`cart-service/`、`user-service/`、`trade-service/`、`pay-service/`、`hm-api/`
- 检查：`.gitignore`

**步骤：**

1. 确认 `main` 上的五服务拆分提交完整，工作区无未纳入的业务代码或密钥。
2. 将 `main` 合并到 `codex/customer-agent`，只解决真实冲突，不覆盖两边已有成果。
3. 在根 Maven reactor 中保留五个业务服务，并为后续 `customer-service` 预留模块位置。
4. 运行 `mvn -DskipTests package`，确认合并前基线仍可构建。
5. 提交集成基线，提交信息：`Merge five-service architecture into customer agent work`。

## Task 2：先定义跨服务客服查询契约

**涉及文件：**

- 新增：`hm-api/src/main/java/com/hmall/api/client/CustomerItemClient.java`
- 新增：`hm-api/src/main/java/com/hmall/api/client/CustomerTradeClient.java`
- 新增：`hm-api/src/main/java/com/hmall/api/client/UserIdentityClient.java`
- 新增：`hm-api/src/main/java/com/hmall/api/dto/customer/*.java`
- 新增测试：对应服务 `src/test/java/**/Customer*ContractTest.java`

**步骤：**

1. 先写失败的契约测试，固定商品搜索/详情、本人订单列表/详情/物流、登录身份解析的请求和最小响应字段。
2. DTO 只包含客服回答需要的数据，不传地址、手机号、支付凭证等字段。
3. Feign 客户端只指向明确的 `/internal/customer/**` 与 `/internal/auth/**` 路径。
4. 运行相关测试，确认测试先因契约缺失而失败。
5. 添加最小 DTO 和 Feign 声明，使契约测试通过。

## Task 3：在业务服务实现受保护的内部查询

**涉及文件：**

- 新增/修改：`item-service/src/main/java/com/hmall/item/controller/InternalCustomerItemController.java`
- 新增/修改：`trade-service/src/main/java/com/hmall/trade/controller/InternalCustomerOrderController.java`
- 新增/修改：`trade-service/src/main/java/com/hmall/trade/service/**`
- 新增/修改：`user-service/src/main/java/com/hmall/user/controller/InternalIdentityController.java`
- 新增/修改：各服务 `src/main/resources/application.yaml`
- 新增测试：商品可见性、订单归属、物流归属、无效身份、内部鉴权测试

**步骤：**

1. 写失败测试：下架商品不可返回；他人订单、他人物流统一返回不可查询；无效或缺失内部服务凭证拒绝访问；无效用户 token 返回未认证。
2. 使用 `HM_INTERNAL_SERVICE_SECRET` 保护业务服务内部端点；密钥只从环境/本地忽略配置读取，不提交仓库。
3. `user-service` 负责校验商城登录 token 并返回最小身份结果，避免把用户服务私钥复制到客服服务。
4. `trade-service` 必须同时按 `orderId` 和可信 `userId` 查询，不能先 `getById` 再由调用方判断。
5. `item-service` 只返回可售商品；所有列表设置上限，避免模型触发大范围数据读取。
6. 运行服务级测试并提交：`Add protected customer query contracts`。

## Task 4：把 Java 客服能力迁移为独立 customer-service

**涉及文件：**

- 新增：`customer-service/pom.xml`
- 新增：`customer-service/src/main/java/com/hmall/customer/CustomerApplication.java`
- 迁移并修改：原 `hm-service/src/main/java/com/hmall/customer/**`
- 新增：`customer-service/src/main/resources/application.yaml`
- 迁移：`customer-service/src/main/resources/db/migration/V1__customer_chat.sql`
- 迁移：`customer-service/src/main/resources/db/migration/V2__customer_policy.sql`
- 迁移：`customer-service/src/main/resources/db/migration/V3__customer_ticket.sql`
- 新增测试：会话归属、委托令牌、SSE 续读、幂等工单、Feign 适配测试

**步骤：**

1. 先迁移现有测试并让它们因缺少独立模块依赖或适配器而失败。
2. 建立端口 `8087`、数据库 `hm-customer`、Nacos 服务名 `customer-service`；Flyway 只管理客服表。
3. 将会话、消息、运行、事件、政策和工单代码从旧单体迁移到新模块，保持外部 `/customer-service/**` 契约不变。
4. 新增身份适配器：登录请求通过 `user-service` 解析用户；匿名会话继续使用服务端签发的安全凭证。
5. 新增商品/订单/物流适配器：通过 `hm-api` Feign 客户端调用对应业务服务，不再访问旧单体 mapper。
6. 保留短时、限域、绑定 `conversationId/runId/userId` 的 Agent 委托令牌；Python 传回的 `userId` 不作为信任来源。
7. 保留可信结果渲染：最终事实回答来自工具结果；模型不能自行承诺退款、赔偿或物流时效。
8. 删除旧 `hm-service` 中已经迁走的客服实现和重复 Flyway 脚本，避免双写与路由歧义。
9. 运行 `mvn -pl customer-service -am test`，提交：`Extract customer service from monolith`。

## Task 5：适配并强化 Python Agent 第一版

**涉及文件：**

- 修改：`customer-agent-service/app/**`
- 修改：`customer-agent-service/tests/**`
- 修改：`customer-agent-service/.env.example`
- 修改：`customer-agent-service/README.md`

**步骤：**

1. 写失败测试覆盖：游客订单请求、委托令牌 scope 不足、工具超时、政策无来源、工单失败、恶意商品/政策文本、模型不可用回退。
2. 保持 Python 只调用 `customer-service` 的 `/internal/customer/**`，由 Java 再访问领域微服务。
3. 将 Java 基址更新为独立客服服务，增加有限超时、错误分类和关联 ID 透传。
4. 确保系统提示和工具白名单只开放商品、本人订单/物流、已发布政策、创建工单；不开放任意 HTTP、SQL、文件、支付或订单写操作。
5. 模型缺失或失败时返回明确可恢复状态，不伪造工具执行成功；转人工失败不得声称已建单。
6. 运行 `pytest -q` 与静态检查，提交：`Harden customer agent service for microservices`。

## Task 6：接入前端与 Nginx，保持外部地址不变

**涉及文件：**

- 修改：`frontend/conf/nginx.conf`
- 修改：`frontend/html/hmall-portal/customer-service.html`
- 修改：`frontend/html/hmall-portal/js/customer-service.js`
- 修改：相关前端测试/检查脚本

**步骤：**

1. 写或更新前端契约检查，确认浏览器仍请求 `/api/customer-service/**`。
2. 将该路径单独代理到 `customer-service:8087`（本地配置使用相应主机名），并置于通用 `/api` 规则之前。
3. SSE 路由关闭代理缓冲、提高读超时，并保留认证头和断线续读的 `after/eventId` 语义。
4. 验证匿名商品/规则咨询入口、登录订单咨询入口和人工工单状态展示。
5. 运行 `node --check` 及可用的前端冒烟测试，提交：`Route storefront support to customer service`。

## Task 7：端到端验证、文档和安全审计

**涉及文件：**

- 修改：`README.md`
- 修改：`docs/handoffs/2026-09-29-customer-agent-handoff.md`
- 新增：`docs/testing/customer-agent-v1-verification.md`
- 检查：所有 `application*.yaml`、`.env*`、证书和密钥文件

**步骤：**

1. 运行完整 Java 测试与构建：`mvn clean test`、`mvn -DskipTests package`。
2. 运行完整 Python 测试：`pytest -q`；如本机缺少 Python 3.12，记录阻塞并使用可用 CI/环境补齐后才宣称通过。
3. 启动可用依赖进行端到端冒烟：匿名商品问答、登录本人订单、越权订单拒绝、规则来源、成功/失败工单、SSE 断线续读。
4. 用仓库搜索检查私钥、口令、token、`.env`、JKS 和本地配置未进入 Git；运行 `git diff --check`。
5. 更新部署参数和端口说明，明确 `HM_INTERNAL_SERVICE_SECRET`、`HM_AGENT_SERVICE_SECRET`、模型配置与数据库初始化方式。
6. 请求代码复核，处理高优先级问题；再次执行受影响测试。
7. 提交：`Document and verify customer agent v1`。

## Task 8：合并到 main 并推送

**步骤：**

1. 确认两个工作区干净，检查待合并提交范围和远端差异。
2. 将 `codex/customer-agent` 合并到 `main`，保留清晰的合并提交。
3. 在最终 `main` 上再次运行完整构建、核心 Java/Python 测试和 `git diff --check`。
4. 仅在验证通过后推送 `main` 到 `git@github.com:jwwu688-afk/hmall.git`。
5. 返回最终 commit SHA、推送结果、测试证据及仍需外部环境验证的项目（例如真实模型与生产 Nacos/MySQL）。

## 完成标准

- 五个业务微服务拆分和客服 Agent 均在 `main`，远端 GitHub 已同步。
- `customer-service` 不直连商品、交易、用户数据库；Python 不直连任何商城数据库。
- 对外客服 URL 不变；订单和物流在业务服务中再次校验所有权。
- 只读能力与唯一写操作“创建工单”边界未被扩大。
- Java、Python、前端检查均有可复现结果；不能执行的真实模型/生产依赖验证被明确列出，不用模拟成功替代。
