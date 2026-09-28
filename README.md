# 黑马商城（hmall）

Java 11 / Spring Boot 2.7.12 / MyBatis-Plus 的商城示例项目。当前是单体应用，Maven 模块包括公共代码 `hm-common` 和可运行应用 `hm-service`。`frontend` 目录保存三个静态站点及 Nginx 配置。

## 本地运行

1. 准备 MySQL 数据库 `hmall` 及相应表结构。此仓库没有包含建表脚本。
2. 设置环境变量 `HM_DB_HOST`（默认 `localhost`）、`HM_DB_PASSWORD` 和 `HM_JWT_PASSWORD`。
3. 在 `hm-service/src/main/resources/` 下生成自己的 `hmall.jks`，别将私钥提交到仓库。例如在仓库根目录运行：

   ```sh
   keytool -genkeypair -alias hmall -keyalg RSA -keysize 2048 -validity 3650 -keystore hm-service/src/main/resources/hmall.jks
   ```

   将密钥库密码和密钥密码设为相同值，并用该值设置 `HM_JWT_PASSWORD`。
4. 执行 `mvn -pl hm-service -am package -DskipTests`，然后运行 `java -jar hm-service/target/hm-service.jar`。应用默认监听 `8080` 端口。

`application-dev.yaml`、`application-local.yaml` 和 `hmall.jks` 是本地文件，已被 Git 忽略。已有本地环境可以继续使用这些文件；公开仓库只保留不含凭据的 `application.yaml`。

## 主要功能

商品查询与管理、搜索、购物车、订单、余额支付、用户登录和收货地址查询。接口文档入口为 `/doc.html`。

## 前端

将 `frontend/html` 和 `frontend/conf` 中的文件分别放进 Nginx 安装目录的 `html`、`conf` 目录，启动 Nginx。商城门户使用 `http://localhost:18080/`，管理页面使用 `http://localhost:18081/` 和 `http://localhost:18082/`。配置中的 `/api` 会转发到 `http://localhost:8080`，因此需要先启动后端。

前端是原项目的静态 HTML、CSS 和 JavaScript，不需要 npm 构建。部分页面调用了当前单体后端尚未提供的接口，例如搜索建议及管理权限相关接口；这些页面的相应功能需要后续后端实现。
