# AGENTS.md - 后端

本文档适用于 `rag-bilibili-server/`。

## 技术栈

- Java 17
- Spring Boot 3.2.0
- Spring AI 1.1.2
- Spring AI Alibaba DashScope
- DashVector Java SDK
- MyBatis
- MySQL + Flyway
- Sa-Token 1.44.0

## 源码结构

- `controller/`：HTTP 和 SSE 接口
- `service/`：服务接口
- `service/impl/`：业务实现
- `mapper/`：MyBatis Mapper
- `entity/`：持久化实体
- `dto/`：请求、响应、SSE DTO
- `config/`：Spring、AI、认证、Web、基础设施配置
- `auth/`：Sa-Token 会话封装
- `exception/`：`BusinessException`、`ErrorCode`、全局异常处理
- `util/`：小型共享工具
- `src/main/resources/mapper/`：MyBatis XML
- `src/main/resources/db/migration/`：Flyway 迁移脚本

## 后端规则

- JSON API Controller 返回 `Result<T>`。
- 普通 API 响应不要手动拼 JSON 字符串。
- 可预期的业务失败使用 `BusinessException` + `ErrorCode`。
- 由 `GlobalExceptionHandler` 统一把异常转换为 API 响应。
- 项目业务代码不要直接抛原始 `RuntimeException`。第三方适配或复制的集成代码可以例外，但进入项目 service 边界前应尽量包装成项目异常。
- 依赖方向保持 controller -> service -> mapper。
- 涉及用户数据的 service 必须校验当前用户的数据归属。
- 认证状态通过 `auth/` 下的 Sa-Token 封装读取，不要在 Controller 里临时解析请求头。
- 数据库字段使用 snake_case，Java 命名使用 camelCase。
- 数据库结构变更必须新增 Flyway migration。

## API 变更

改变后端接口契约时，先更新根目录 `docs/API.md`，或在同一补丁中同步更新。需要写清楚：

- 请求方法和路径
- 认证要求
- body、query、path 参数
- `Result<T>` 响应结构
- 错误码
- SSE 接口的事件名和 payload 结构

## 验证

开发过程中优先跑小范围测试，交付前尽量做更完整检查。

```bash
mvn test
mvn clean package -DskipTests
mvn spring-boot:run
```

完整运行后端需要 MySQL 和以下环境变量：

- `DB_USERNAME`
- `DB_PASSWORD`
- `OPENAI_API_KEY`
- `OPENAI_BASE_URL`
- `DASHSCOPE_API_KEY`
- `DASHVECTOR_API_KEY`
- `DASHVECTOR_ENDPOINT`

## 代码审查

做代码审查、尤其在准备说"测试覆盖不够"之前，先看 `docs/development/代码审查与测试评判.md`。

本项目大体按 TDD 写代码，边界用例的留白通常是节奏问题不是 bug。测试是当前规约的可执行表达，不是防御未来错误的护栏，不要用"以后有人改坏了测不到"做论据。

