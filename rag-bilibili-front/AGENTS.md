# AGENTS.md - 前端

本文档适用于 `rag-bilibili-front/`。

## 技术栈

- Vue 3
- Vite
- Vue Router
- Pinia
- Element Plus
- Axios

## 源码结构

- `src/views/`：页面级视图
- `src/components/`：可复用组件
- `src/api/`：后端 API 封装
- `src/stores/`：Pinia 状态
- `src/router/`：路由和鉴权守卫
- `src/utils/`：SSE、格式化、日志、共享工具

## 前端规则

- 后端调用放在 `src/api/`，页面不要重复写传输逻辑。
- REST 请求使用现有 Axios 封装和认证处理。
- 流式聊天使用现有 fetch/SSE helper，token 行为保持 `Authorization: Bearer <token>`。
- API 响应按 `Result<T>` 读取：`{ code, message, data }`。
- 路由守卫和登录状态要和后端 Sa-Token 错误保持一致，尤其是 `NOT_LOGGED_IN` / code `1004`。
- 优先使用 Element Plus 组件和项目已有样式，不随意新增一套 UI 模式。
- 不要硬编码 `http://localhost:8080`，使用 `.env` 和 Vite 代理配置。
- 除非任务明确要求重设计，否则避免大范围视觉重写。

## API 变更

如果前端改动需要新增或修改后端契约，先更新根目录 `docs/API.md`，或在同一补丁中同步更新。前端按文档契约实现。

如果 `docs/API.md` 和当前后端行为不一致，先指出差异，不要基于猜测继续开发。

## 验证

```bash
npm run build
npm run dev
```

涉及交互时尽量做浏览器验证。如果缺少依赖，先运行 `npm install`。

