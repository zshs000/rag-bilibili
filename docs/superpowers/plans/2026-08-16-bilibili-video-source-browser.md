# B站视频来源浏览 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 支持从 B站收藏夹或 UP 投稿分页获取视频并在前端完成可视化选择。

**Architecture:** 新增独立的 `BilibiliSourceClient` 负责 HTTP/WBI/响应校验，Service 负责请求约束和 DTO 映射，Controller 暴露三个只读语义的 POST 接口。前端新增 API 模块和来源选择组件，选择状态仅保存在组件内存。

**Tech Stack:** Java 17、Spring Boot、Jackson、JDK HttpClient、JUnit 5、Vue 3、Element Plus、Vite

---

## Task 1: 定义接口契约

- [x] 更新 `docs/API.md`，定义收藏夹目录、收藏夹视频和 UP 视频三个查询接口。
- [x] 明确凭证、分页、错误码和响应字段。

## Task 2: 实现后端来源查询

- [x] 先新增 WBI 签名与 UID 解析失败测试并运行确认失败。
- [x] 实现签名、解析和 B站 HTTP 客户端，使测试通过。
- [x] 先新增 Service/Controller 映射与校验测试并运行确认失败。
- [x] 实现 DTO、Service、Controller 与错误码，使测试通过。

## Task 3: 实现前端浏览与选择

- [x] 新增 `src/api/bilibili-sources.js` 和开发 Mock。
- [x] 新增 `BilibiliSourceBrowser.vue`，实现来源切换、分页、列表、逐项选择和当前页全选。
- [x] 接入 `ImportView.vue` 的第三个 Tab。

## Task 4: 验证和交付

- [ ] 运行后端相关测试与完整 `mvn test`。
- [ ] 运行前端 `npm run build`。
- [ ] 检查敏感信息、diff 和未关联文件。
- [ ] 提交、推送并等待仓库检查。
