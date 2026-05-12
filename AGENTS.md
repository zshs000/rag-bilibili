# AGENTS.md - RAG-Bilibili

本文档是本仓库给 AI agent 使用的根级工作规则。目标是短、明确、可执行，并且和代码事实保持一致。

## 项目结构

RAG-Bilibili 是一个基于 B 站视频字幕的检索增强问答系统。

- `rag-bilibili-server/`：Spring Boot 后端
- `rag-bilibili-front/`：Vue 前端
- `subtitle-probe/`：Node.js + Playwright 字幕按钮探测工具
- `subtitle-cleaning-eval-lab/`：字幕清洗评估实验室
- `docs/`：API、设计、开发、归档文档

## 指令优先级

当不同信息源互相冲突时，按以下顺序判断：

1. 当前代码和测试
2. `docs/API.md` 中的前后端 HTTP/SSE 契约
3. 当前编辑目录最近的 `AGENTS.md`
4. 模块 README 和启动文档
5. `docs/archive/` 或 `docs/initial-design/` 下的历史设计文档

如果文档和可运行代码冲突，不要静默选择其中一个。需要明确指出冲突，并根据任务范围修正文档，或在改变行为前询问用户。

## 通用工作规则

- 编辑前先阅读最近的相关代码。
- 改动范围只覆盖当前请求的功能或问题。
- 不顺手重写无关文档，不做无关重构。
- 保留工作区里用户已有改动。除非用户明确要求，不要 reset、revert 或删除无关文件。
- 优先做小而清晰的补丁，避免大范围重写。
- 中文 Markdown 和源码注释使用 UTF-8。

## API 契约流程

开发新功能或修改前后端契约时，先阅读 `docs/development/新功能开发指南.md`。

如果任务会改变前后端契约：

1. 先更新 `docs/API.md`，写清楚请求方法、路径、参数、响应、错误码、认证要求；如果是 SSE，还要写清事件格式。
2. 后端和前端都按该契约实现。
3. 如果实现时发现契约不合理，需要在同一轮改动中同步更新 `docs/API.md`，并说明原因。

如果是不会改变公开契约的 bugfix，先修代码；只有文档确实过期时才同步更新文档。

## 后端规则

编辑后端代码前先看 `rag-bilibili-server/AGENTS.md`。

全局要点：

- Java 17、Spring Boot 3.2、Spring AI 1.1.2、MyBatis、MySQL、Sa-Token。
- JSON API Controller 返回 `Result<T>`。
- 业务失败使用 `BusinessException` + `ErrorCode`。
- 保持 controller -> service -> mapper 分层。
- 认证使用 Sa-Token，协议是 `Authorization: Bearer <token>`。

## 前端规则

编辑前端代码前先看 `rag-bilibili-front/AGENTS.md`。

全局要点：

- Vue 3 + Vite + Element Plus + Pinia。
- 后端 API 调用放在 `src/api/`。
- 路由和认证行为要和后端契约保持一致。
- 不要在代码里硬编码后端地址，优先使用 Vite 代理和环境变量配置。

## 常用命令

后端：

```bash
cd rag-bilibili-server
mvn test
mvn clean package -DskipTests
mvn spring-boot:run
```

前端：

```bash
cd rag-bilibili-front
npm install
npm run build
npm run dev
```

字幕探测工具：

```bash
cd subtitle-probe
npm install
npx playwright install chromium
```

## 验证要求

- 后端行为变更：先跑最小相关 Maven 测试，条件允许时再跑 `mvn test`。
- 前端行为变更：跑 `npm run build`；涉及交互时尽量做浏览器或手工验证。
- 纯文档变更：确认 Markdown 可读，相关链接尽量指向存在的文件。
- 如果验证因为缺少服务、凭证或依赖无法完成，需要明确说明。

## Git

- 用户要求提交时，提交信息使用 `type(scope): 中文描述`。
- 不添加 `Co-Authored-By`。
- 不暂存无关文件。
