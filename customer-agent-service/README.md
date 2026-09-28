# 黑马商城客服 Agent

本服务作为商城的独立 Python 模块运行。Java 保管账号、商品、订单、规则和工单；Python 通过带签名的有限权限令牌调用 Java 内部接口。当前核心能力包括在售商品查询、本人订单和已有物流信息查询，以及持久化会话检查点。客服模型不能直接访问商城数据库。

## 启动

1. 使用 Python 3.12 在 **D 盘**创建虚拟环境，例如：`D:\code\hmall\.venvs\customer-agent`。
2. 在该环境安装依赖：`python -m pip install -e ".[test]"`。可把 `PIP_CACHE_DIR` 设置为 D 盘路径。
3. 将 `.env.example` 复制为被 Git 忽略的 `.env.local`，设置模型名称、模型 API 地址、密钥、Java 地址，以及与 Java 一致的两项内部密钥。
4. 先启动 MySQL 和 `hm-service`，再在本目录运行 `uvicorn app.main:app --host 127.0.0.1 --port 8001`。
5. `GET /health` 返回 `{"status":"ok"}`；Python 内部执行入口只接受 Java 服务密钥，浏览器只访问 Java 的 `/customer-service/...` 接口。

测试：`python -m pytest tests -q`。需要真实模型行为联调时，在 `.env.local` 配置可用模型后运行，记录商品与订单事实准确率及响应延迟。未配置模型时，服务会把运行失败回调给 Java，页面显示重试提示，不会编造答案。

DeepAgents 默认提供文件和终端工具，当前通过文件权限规则拒绝所有读写，默认状态后端也不提供可执行终端。电商工具仅限 Java 专用只读接口；商品和订单助手按登录状态装配。SQLite 检查点保存在 `HM_AGENT_CHECKPOINT_DB`，正式部署应挂载持久卷。

## 部署

`Dockerfile` 暴露 8001 端口。建议仅允许 Java 服务访问，不对公网开放；把 `.env.local` 的值作为容器环境变量注入，并为 `/app/data` 挂载持久卷。Java 端 `HM_AGENT_URL` 指向本服务。当前 Java 11 项目使用 Flyway 8.5.13 的 `flyway-mysql` 模块，已有数据库以版本 0 建立迁移基线，再执行 `V1__customer_chat.sql`。部署前备份数据库并检查迁移日志。
