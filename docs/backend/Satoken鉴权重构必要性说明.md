# Satoken 鉴权重构必要性说明

## 结论

当前项目可以重构为以 Satoken 为核心的鉴权体系，且重构收益明确。

现有实现已经具备基础登录能力，但本质是“自研 JWT + MVC 拦截器 + ThreadLocal 当前用户”的轻量方案，只解决了“请求是否携带有效 token”。如果项目继续向多用户知识库、会话隔离、管理能力、权限分层方向演进，继续手写鉴权会让安全策略分散在 `JwtUtil`、`LoginInterceptor`、`UserContext`、前端 token 约定和测试 Mock 中，后续维护成本会持续上升。

建议将 Satoken 作为后端统一鉴权框架引入，先保持现有接口契约不变：前端继续使用 `Authorization: Bearer <token>`，登录响应继续返回 `data.token`，未登录继续映射为业务错误码 `1004`。

## 当前鉴权体系

后端当前鉴权链路如下：

1. `AuthController.login` 调用 `UserService.login` 完成用户名密码校验。
2. 登录成功后调用 `JwtUtil.generateToken(userId)` 签发 JWT，并放入 `UserResponse.token` 返回给前端。
3. 前端将 token 保存到 `localStorage` 的 `rag_token`。
4. axios 请求拦截器统一写入 `Authorization: Bearer <token>`。
5. SSE 流式消息接口没有走 axios，但 `messagesApi.stream` 会在 `fetch` 请求里手动补同样的 Authorization 头。
6. 后端 `LoginInterceptor` 拦截 `/api/**`，放行 `/api/auth/login`、`/api/auth/register` 和 `OPTIONS`。
7. 拦截器解析 JWT subject 得到 userId，写入 `UserContext` 的 `ThreadLocal<Long>`。
8. Controller 通过 `UserContext.get()` 获取当前用户，再传给业务 Service。
9. `logout` 没有服务端 token 失效逻辑，实际依赖前端删除 token。

当前方案的优点是简单、依赖少、接口契约清晰。短板也比较明显：

- 无服务端登录态治理，无法主动注销已签发 token。
- 无踢下线、单端登录、同端互斥、封禁账号、续期治理等能力。
- 无角色/权限模型，后续如果出现管理员、普通用户、只读用户等角色，需要继续手写扩展。
- JWT 密钥、过期策略、异常处理、前端协议靠项目约定维护，安全策略缺少框架级边界。
- Controller 测试大量 Mock `LoginInterceptor` 和 `JwtUtil`，鉴权能力越扩展，测试替换成本越高。

## Satoken 调研摘要

Satoken 是 Dromara 生态下的 Java 权限认证框架。官方仓库和文档覆盖登录认证、权限认证、Session 会话、单点登录、OAuth2、微服务网关鉴权等能力。核心 API 以 `StpUtil` 为入口，例如：

- `StpUtil.login(id)`：建立登录态。
- `StpUtil.checkLogin()`：校验当前请求是否登录。
- `StpUtil.getLoginIdAsLong()`：获取当前登录用户 id。
- `StpUtil.logout()`：注销当前登录态。
- `StpUtil.kickout(id)`：踢指定账号下线。
- `StpUtil.checkRole(...)` / `StpUtil.checkPermission(...)`：角色和权限校验。

本项目使用 Spring Boot 3.2.0 和 Spring MVC，应优先评估 `cn.dev33:sa-token-spring-boot3-starter`。本次实现按项目约束固定使用 `1.44.0`，不追随当前可见的更新版本。

Satoken 可以通过 MVC 拦截器接管当前 `LoginInterceptor` 的职责，示意如下：

```java
registry.addInterceptor(new SaInterceptor(handle -> {
    SaRouter.match("/api/**")
            .notMatch("/api/auth/login", "/api/auth/register")
            .check(r -> StpUtil.checkLogin());
}));
```

为兼容现有前端，可以保留 Bearer 协议：

```yaml
sa-token:
  token-name: Authorization
  token-prefix: Bearer
  is-read-header: true
  is-read-cookie: false
```

## 是否适合重构

适合，但需要先明确 token 存储模型。

从代码结构看，重构面比较集中：

- `JwtUtil` 可由 Satoken 登录态 API 替代。
- `LoginInterceptor` 可由 `SaInterceptor` + `SaRouter` 替代。
- `AuthController.login` 可在用户密码校验成功后调用 `StpUtil.login(userId)`，再通过 `StpUtil.getTokenValue()` 返回 token。
- `AuthController.logout` 可调用 `StpUtil.logout()`，从“前端丢弃 token”升级为服务端注销。
- `UserContext.get()` 可替换为 `StpUtil.getLoginIdAsLong()`；也可以先保留一个兼容封装，减少业务 Controller 的一次性改动。
- `GlobalExceptionHandler` 需要捕获 Satoken 未登录异常，并映射为现有 `NOT_LOGGED_IN(1004)`，保证前端跳转逻辑不变。

主要分歧点在登录态模型：

- 使用 Satoken 默认服务端会话：能获得注销、踢人、会话查询、续期、多端策略等治理能力；但需要接受服务端存储状态，单机内存模式下应用重启会让登录态失效，生产更适合接 Redis。
- 使用 Satoken-JWT：更接近当前无状态 JWT 行为；但主动注销、踢下线等能力通常需要黑名单或存储策略配合，否则会弱化引入 Satoken 的治理收益。

因此，推荐路线是：如果项目目标是长期多用户化和可运营，采用 Satoken 默认会话并引入 Redis；如果当前仍是单机个人项目，可先用内存会话完成框架切换，但文档和配置里要明确“重启登录态失效”。

## 实现必要性

重构的必要性不在于当前登录接口不能工作，而在于当前鉴权体系已经到达自研方案的边界。

第一，当前登出没有服务端语义。用户点击退出后，服务端并不知道 token 已失效；只要旧 token 未过期，仍可继续访问接口。对于 7 天有效期的 token，这意味着账号风险无法在服务端及时收敛。

第二，未来权限需求会自然增长。视频导入、删除、会话管理、知识库数据隔离已经依赖 userId；一旦出现管理员账号、公开/私有知识库、邀请码注册、禁用用户等需求，仅靠 `UserContext.get()` 很快会演化成零散判断。Satoken 的角色/权限 API 可以把这类策略收束到统一模型。

第三，安全能力需要框架化。token 续期、并发登录控制、踢下线、封禁账号、Session 查询、token 信息读取，这些能力如果继续手写，会分散在工具类和拦截器里，测试与审计成本都更高。

第四，迁移成本现在较低。项目未引入 Spring Security，鉴权入口集中，前端协议统一，业务侧只需要 userId。现在重构比权限体系扩张后再改更容易控制风险。

第五，接口契约可以保持稳定。通过 Satoken 配置继续读取 `Authorization: Bearer <token>`，前端 axios、SSE、路由守卫都可以基本不动；后端只要维持 `data.token` 和 `NOT_LOGGED_IN(1004)`，用户侧无感。

## 推荐迁移步骤

1. 引入 `sa-token-spring-boot3-starter`，移除直接业务依赖中的 JJWT 鉴权职责。
2. 新增 Satoken 配置，保持 `Authorization: Bearer` 协议。
3. 用 `SaInterceptor` 替换 `LoginInterceptor`，复刻 `/api/**` 与登录/注册白名单。
4. 登录成功后调用 `StpUtil.login(userId)`，返回 `StpUtil.getTokenValue()`。
5. 登出接口改为 `StpUtil.logout()`。
6. 当前用户获取从 `UserContext.get()` 迁移到 `StpUtil.getLoginIdAsLong()`；可先做兼容封装再逐步替换。
7. `GlobalExceptionHandler` 增加 Satoken 异常映射，保证未登录仍返回 `1004`。
8. 替换测试：删除或改造 `JwtUtilTest`，Controller 测试从 Mock `LoginInterceptor/JwtUtil` 改为 Satoken 测试配置。
9. 决定是否接 Redis。生产环境建议接 Redis，以支撑服务重启后的登录态、踢下线和会话治理。

## 风险与控制

- 登录态模型变化：默认 Satoken 是服务端会话，不是当前纯无状态 JWT。需要在开发、测试、生产配置中明确存储策略。
- 前端兼容风险：必须保持 `Authorization: Bearer`、`data.token`、`1004` 三个契约。
- 测试改造成本：现有 WebMvc 测试大量 Mock 自研组件，需要一次性替换鉴权测试基建。
- 依赖版本风险：使用 Spring Boot 3 starter，不要误用 Boot 2 starter 或 WebFlux/Reactor starter。

## 资料来源

- Sa-Token 官方文档：https://sa-token.cc/doc.html
- Sa-Token GitHub 仓库：https://github.com/dromara/Sa-Token
- 官方下载/依赖说明：https://github.com/dromara/Sa-Token/blob/dev/sa-token-doc/start/download.md
- 官方配置说明：https://github.com/dromara/Sa-Token/blob/dev/sa-token-doc/use/config.md
- Maven Central artifact：https://central.sonatype.com/artifact/cn.dev33/sa-token-spring-boot3-starter
- `StpUtil` Javadoc：https://javadoc.io/doc/cn.dev33/sa-token-core/latest/cn/dev33/satoken/stp/StpUtil.html
