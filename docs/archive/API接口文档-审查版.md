# RAG-Bilibili API 接口文档

本文档按当前代码整理，核对来源包括：

- 后端 Controller：`rag-bilibili-server/src/main/java/com/example/ragbilibili/controller/`
- 后端 DTO：`rag-bilibili-server/src/main/java/com/example/ragbilibili/dto/`
- 前端接口封装：`rag-bilibili-front/src/api/`

最后核对时间：2026-05-12。

## 1. 基础约定

### 1.1 服务地址

本地开发默认：

- 后端：`http://localhost:8080`
- 前端：`http://localhost:5173`
- 前端 API 基础路径：`/api`

前端通过 `VITE_API_BASE_URL` 控制接口前缀，默认值为 `/api`。开发环境下，Vite 会把 `/api/*` 代理到 `VITE_PROXY_TARGET`，默认是 `http://localhost:8080`。

### 1.2 认证方式

除注册和登录外，所有 `/api/**` 接口都需要登录。

请求头：

```http
Authorization: Bearer <token>
```

token 来自登录接口响应的 `data.token`。前端会把 token 保存到 `localStorage` 的 `rag_token`，普通 REST 请求由 Axios 拦截器自动加请求头，SSE 流式请求由 `fetch` 手动加请求头。

### 1.3 统一响应格式

普通 REST 接口返回：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

前端 Axios 拦截器会校验 `code === 200`，并把业务层 `data` 作为接口调用结果返回给页面。

### 1.4 常用错误码

| code | 含义 |
| --- | --- |
| 200 | 成功 |
| 400 | 参数错误 |
| 500 | 系统错误 |
| 1001 | 用户不存在 |
| 1002 | 用户已存在 |
| 1003 | 密码错误 |
| 1004 | 未登录 |
| 1005 | 注册已关闭 |
| 1006 | 操作过于频繁 |
| 2001 | 视频不存在 |
| 2002 | 视频已存在 |
| 2003 | 视频导入失败 |
| 2004 | 视频无字幕 |
| 2005 | BV 号解析失败 |
| 3001 | 会话不存在 |
| 3002 | 会话类型错误 |
| 4001 | 向量删除失败 |
| 4002 | 向量写入失败 |

## 2. 数据结构

### 2.1 UserResponse

```json
{
  "id": 1,
  "username": "test_user",
  "createTime": "2026-05-12 10:00:00",
  "token": "satoken-value"
}
```

说明：`token` 只在登录响应中一定存在；注册和当前用户接口也使用同一响应类型，但不应依赖它一定有值。

### 2.2 VideoResponse

```json
{
  "id": 1,
  "bvid": "BV1DCfsBKExV",
  "title": "视频标题",
  "description": "视频简介",
  "chunkCount": 48,
  "importTime": "2026-05-12 10:00:00",
  "status": "SUCCESS",
  "failReason": null
}
```

`status` 取值：

- `IMPORTING`
- `SUCCESS`
- `FAILED`

### 2.3 SessionResponse

```json
{
  "id": 1,
  "sessionType": "SINGLE_VIDEO",
  "videoId": 1,
  "videoTitle": "视频标题",
  "createTime": "2026-05-12 10:00:00"
}
```

`sessionType` 取值：

- `SINGLE_VIDEO`
- `ALL_VIDEOS`

`ALL_VIDEOS` 会话的 `videoId` 和 `videoTitle` 可以为 `null`。

### 2.4 MessageResponse

```json
{
  "id": 1,
  "role": "USER",
  "content": "这个视频讲了什么？",
  "createTime": "2026-05-12 10:00:00"
}
```

`role` 取值：

- `USER`
- `ASSISTANT`

## 3. 认证接口

### 3.1 注册

```http
POST /api/auth/register
Content-Type: application/json
```

不需要登录。

请求体：

```json
{
  "username": "test_user",
  "password": "test123"
}
```

校验规则：

- `username` 必填，长度 3-50，只允许字母、数字、下划线。
- `password` 必填，长度 6-20。

成功响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "username": "test_user",
    "createTime": "2026-05-12 10:00:00",
    "token": null
  }
}
```

前端封装：`authApi.register(payload)`。

### 3.2 登录

```http
POST /api/auth/login
Content-Type: application/json
```

不需要登录。

请求体：

```json
{
  "username": "test_user",
  "password": "test123"
}
```

成功响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "username": "test_user",
    "createTime": "2026-05-12 10:00:00",
    "token": "satoken-value"
  }
}
```

前端封装：`authApi.login(payload)`。

### 3.3 登出

```http
POST /api/auth/logout
Authorization: Bearer <token>
```

成功响应：

```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

前端封装：`authApi.logout()`。

### 3.4 获取当前用户

```http
GET /api/auth/current
Authorization: Bearer <token>
```

成功响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "username": "test_user",
    "createTime": "2026-05-12 10:00:00",
    "token": null
  }
}
```

前端封装：`authApi.current()`。

## 4. 视频接口

### 4.1 导入视频

```http
POST /api/videos
Authorization: Bearer <token>
Content-Type: application/json
```

请求体：

```json
{
  "bvidOrUrl": "BV1DCfsBKExV",
  "sessdata": "your_SESSDATA",
  "biliJct": "your_bili_jct",
  "buvid3": "your_buvid3"
}
```

字段说明：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `bvidOrUrl` | string | 是 | BV 号或包含 BV 号的视频 URL |
| `sessdata` | string | 是 | B 站 Cookie 中的 SESSDATA |
| `biliJct` | string | 是 | B 站 Cookie 中的 bili_jct |
| `buvid3` | string | 是 | B 站 Cookie 中的 buvid3 |

成功响应 data：`VideoResponse`。

前端封装：`videosApi.importVideo(payload)`。

### 4.2 获取视频列表

```http
GET /api/videos
Authorization: Bearer <token>
```

成功响应 data：`VideoResponse[]`。

前端封装：`videosApi.list()`。

### 4.3 获取视频详情

```http
GET /api/videos/{id}
Authorization: Bearer <token>
```

路径参数：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `id` | number | 视频 ID |

成功响应 data：`VideoResponse`。

前端封装：`videosApi.detail(id)`。

### 4.4 删除视频

```http
DELETE /api/videos/{id}
Authorization: Bearer <token>
```

路径参数：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `id` | number | 视频 ID |

成功响应：

```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

前端封装：`videosApi.remove(id)`。

## 5. 会话接口

### 5.1 创建会话

```http
POST /api/sessions
Authorization: Bearer <token>
Content-Type: application/json
```

请求体：

```json
{
  "sessionType": "SINGLE_VIDEO",
  "videoId": 1
}
```

字段说明：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `sessionType` | string | 是 | `SINGLE_VIDEO` 或 `ALL_VIDEOS` |
| `videoId` | number | 条件必填 | `SINGLE_VIDEO` 时必填；`ALL_VIDEOS` 时传 `null` 或不传 |

成功响应 data：`SessionResponse`。

前端封装：`sessionsApi.create(payload)`。

### 5.2 获取会话列表

```http
GET /api/sessions
Authorization: Bearer <token>
```

成功响应 data：`SessionResponse[]`。

前端封装：`sessionsApi.list()`。

### 5.3 获取会话详情

```http
GET /api/sessions/{id}
Authorization: Bearer <token>
```

路径参数：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `id` | number | 会话 ID |

成功响应 data：`SessionResponse`。

注意：当前后端该接口只返回会话详情，不直接包含消息历史。消息历史需要调用 `GET /api/sessions/{sessionId}/messages`。

前端封装：`sessionsApi.detail(id)`。

### 5.4 删除会话

```http
DELETE /api/sessions/{id}
Authorization: Bearer <token>
```

路径参数：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `id` | number | 会话 ID |

成功响应：

```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

前端封装：`sessionsApi.remove(id)`。

## 6. 消息与流式问答接口

### 6.1 获取消息列表

```http
GET /api/sessions/{sessionId}/messages
Authorization: Bearer <token>
```

路径参数：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `sessionId` | number | 会话 ID |

成功响应 data：`MessageResponse[]`。

前端封装：`messagesApi.list(sessionId)`。

### 6.2 发送消息并流式生成回答

```http
POST /api/sessions/{sessionId}/messages/stream
Authorization: Bearer <token>
Content-Type: application/json
Accept: text/event-stream
```

路径参数：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `sessionId` | number | 会话 ID |

请求体：

```json
{
  "content": "这个视频讲了什么？"
}
```

字段说明：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `content` | string | 是 | 用户问题 |

响应类型：`text/event-stream`。

前端封装：`messagesApi.stream(sessionId, payload, handlers, signal)`。

#### SSE 事件

后端会发送具名 SSE 事件，`event` 名称与 `data.type` 一致。

开始事件：

```text
event: start
data: {"type":"start","userMessageId":1}
```

内容增量事件：

```text
event: content
data: {"type":"content","delta":"这个视频"}
```

结束事件：

```text
event: end
data: {"type":"end","assistantMessageId":2,"fullContent":"完整回答内容"}
```

错误事件：

```text
event: error
data: {"type":"error","message":"错误信息"}
```

注意：当前真实后端的 `end` 事件不包含 `userMessageId`。

## 7. 前后端一致性检查

### 7.1 已确认一致

| 模块 | 前端调用 | 后端接口 | 结论 |
| --- | --- | --- | --- |
| 认证 | `authApi.register` | `POST /api/auth/register` | 一致 |
| 认证 | `authApi.login` | `POST /api/auth/login` | 一致 |
| 认证 | `authApi.logout` | `POST /api/auth/logout` | 一致 |
| 认证 | `authApi.current` | `GET /api/auth/current` | 一致 |
| 视频 | `videosApi.importVideo` | `POST /api/videos` | 一致 |
| 视频 | `videosApi.list` | `GET /api/videos` | 一致 |
| 视频 | `videosApi.detail` | `GET /api/videos/{id}` | 一致 |
| 视频 | `videosApi.remove` | `DELETE /api/videos/{id}` | 一致 |
| 会话 | `sessionsApi.create` | `POST /api/sessions` | 一致 |
| 会话 | `sessionsApi.list` | `GET /api/sessions` | 一致 |
| 会话 | `sessionsApi.detail` | `GET /api/sessions/{id}` | 一致 |
| 会话 | `sessionsApi.remove` | `DELETE /api/sessions/{id}` | 一致 |
| 消息 | `messagesApi.list` | `GET /api/sessions/{sessionId}/messages` | 一致 |
| 消息 | `messagesApi.stream` | `POST /api/sessions/{sessionId}/messages/stream` | 一致 |

### 7.2 字段核对

| 场景 | 前端使用字段 | 后端 DTO | 结论 |
| --- | --- | --- | --- |
| 登录 | `id`, `username`, `createTime`, `token` | `UserResponse` | 一致 |
| 导入视频 | `bvidOrUrl`, `sessdata`, `biliJct`, `buvid3` | `ImportVideoRequest` | 一致 |
| 视频展示 | `id`, `bvid`, `title`, `description`, `chunkCount`, `importTime`, `status`, `failReason` | `VideoResponse` | 一致 |
| 创建会话 | `sessionType`, `videoId` | `CreateSessionRequest` | 一致 |
| 会话展示 | `id`, `sessionType`, `videoId`, `videoTitle`, `createTime` | `SessionResponse` | 一致 |
| 发送消息 | `content` | `SendMessageRequest` | 一致 |
| 消息展示 | `id`, `role`, `content`, `createTime` | `MessageResponse` | 一致 |
| SSE start | `userMessageId` | `SseStartEvent` | 一致 |
| SSE content | `delta` | `SseContentEvent` | 一致 |
| SSE end | `assistantMessageId`, `fullContent` | `SseEndEvent` | 一致 |

### 7.3 发现的差异和过时信息

1. 旧设计文档 `docs/initial-design/概要设计.md` 和 `docs/initial-design/详细设计.md` 仍写了 `Session` / `HttpSession` 认证方式。当前真实实现是 Sa-Token，并通过 `Authorization: Bearer <token>` 传 token。

2. `rag-bilibili-front/前端具体实现文档.md` 写了 Axios `withCredentials: true`。当前真实代码没有启用该配置，登录态不靠 Cookie 传递，而是靠 Bearer token。

3. 前端 mock 服务的 SSE `end` 事件会额外返回 `userMessageId`，真实后端 `SseEndEvent` 不返回该字段。当前页面代码不强依赖 `end.userMessageId`，因此不影响真实前后端联调。

4. 旧设计文档曾描述 `GET /api/sessions/{id}` 返回会话详情及消息历史。当前真实后端只返回 `SessionResponse`，消息历史由 `GET /api/sessions/{sessionId}/messages` 单独获取。当前前端也是按两个接口分别请求，因此代码一致。
