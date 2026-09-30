# 客服 Agent 第一版验证记录

验证日期：2026-09-30
验证目标：合并后的 `main`（同一代码树在 `codex/customer-agent` 完成验证）

## 自动化结果

| 范围 | 命令 | 结果 |
| --- | --- | --- |
| Java 全量测试 | `mvn clean test` | 十模块成功；38 项执行，0 失败，0 错误，1 跳过 |
| Java 打包 | `mvn -DskipTests package` | 十模块全部成功 |
| Python Agent | `.venv/Scripts/python.exe -m pytest tests -q -p no:cacheprovider --basetemp <ignored-dir>` | 20 项通过 |
| Python 静态编译 | `.venv/Scripts/python.exe -m compileall -q app tests` | 通过 |
| 前端契约 | `node --test frontend/tests/customer-service-contract.test.js` | 3 项通过 |
| 前端语法 | `node --check frontend/html/hmall-portal/js/customer-service.js` | 通过 |

Java 覆盖内部服务密钥、Feign 契约、在售商品过滤、订单归属、裸 JWT/Bearer JWT 身份解析、客服会话/事件、政策、工单、幂等、脱敏和 Agent HTTP 超时配置。所有模块由根 POM 统一使用 Java 11 `--release` 编译。Python 覆盖游客订单拒绝、令牌绑定与 scope、Java 超时、规则缺失/冲突、恶意文本不扩权、工单成功/失败、模型不可用回退、检查点恢复及评测样例结构。前端契约覆盖固定外部地址、认证头、SSE `after` 游标、8087 优先代理和 `QUEUED` 工单成功条件。

旧 `hm-service` 的 `deductStock` 测试会修改共享库存且依赖固定数据，现标记为手工集成场景；JWT 测试改为运行时生成 RSA 密钥对，因此全仓测试不再依赖本地 JKS。

## 安全检查

- `hmall.jks`、`.env.local`、`application-dev.yaml`、`application-local.yaml`、`.venv` 和 `*.egg-info` 均由 `.gitignore` 排除。
- 生产 YAML 只引用环境变量，没有提交实际数据库、模型或服务密钥。
- `customer-service/src/test/resources/application-test.yaml` 的固定值仅用于 H2 隔离测试，名称明确包含 `test`，不能用于部署。
- Python 工具没有任意 HTTP、SQL、文件、终端、支付或订单写能力；DeepAgents 文件权限为全拒绝。
- 商品、交易、用户内部端点在服务密钥为空或不匹配时拒绝请求；订单归属在业务服务中再次校验。

## 未执行的环境验收

本机未提供真实模型 API、完整 Nacos/MySQL 运行数据、已审核商城政策和 Nginx 可执行文件，因此以下项目不能宣称已通过：

- 真实模型的工具选择准确率、幻觉率、响应延迟和限流表现；
- 浏览器中的匿名商品问答、登录本人订单、越权订单拒绝、政策来源卡片、工单成功/失败；
- 服务重启、网络超时、数据库故障、SSE 断线续读和重复消息的完整故障注入；
- `nginx -t` 与目标容器/主机的网络地址验证；
- 生产 MySQL Flyway 迁移、备份和回滚演练。

上线前必须在目标环境按 `frontend/README-customer-service.md` 完成上述验收，并保存日志、截图、延迟和错误率证据。
