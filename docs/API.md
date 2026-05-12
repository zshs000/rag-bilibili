# API 接口文档

## 基础约定

- 后端地址：`http://localhost:8080`
- 前端 API 前缀：`/api`
- 认证方式：`Authorization: Bearer <token>`
- 响应格式：`{ "code": 200, "message": "success", "data": {} }`

## 错误码

| code | 含义 |
|------|------|
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

---

## 认证接口

### 注册

```
POST /api/auth/register
```

请求体：
```json
{
  "username": "test_user",
  "password": "test123"
}
```

响应 data：`UserResponse`

### 登录

```
POST /api/auth/login
```

请求体：
```json
{
  "username": "test_user",
  "password": "test123"
}
```

响应 data：`UserResponse`（包含 `token`）

### 登出

```
POST /api/auth/logout
```

需要登录。响应 data：`null`

### 获取当前用户

```
GET /api/auth/current
```

需要登录。响应 data：`UserResponse`

---

## 视频接口

### 导入视频

```
POST /api/videos
```

需要登录。

请求体：
```json
{
  "bvidOrUrl": "BV1DCfsBKExV",
  "sessdata": "xxx",
  "biliJct": "xxx",
  "buvid3": "xxx"
}
```

响应 data：`VideoResponse`

### 获取视频列表

```
GET /api/videos
```

需要登录。响应 data：`VideoResponse[]`

### 获取视频详情

```
GET /api/videos/{id}
```

需要登录。响应 data：`VideoResponse`

### 删除视频

```
DELETE /api/videos/{id}
```

需要登录。响应 data：`null`

---

## 会话接口

### 创建会话

```
POST /api/sessions
```

需要登录。

请求体：
```json
{
  "sessionType": "SINGLE_VIDEO",
  "videoId": 1
}
```

- `sessionType`：`SINGLE_VIDEO` 或 `ALL_VIDEOS`
- `SINGLE_VIDEO` 时 `videoId` 必填
- `ALL_VIDEOS` 时 `videoId` 可不传

响应 data：`SessionResponse`

### 获取会话列表

```
GET /api/sessions
```

需要登录。响应 data：`SessionResponse[]`

### 获取会话详情

```
GET /api/sessions/{id}
```

需要登录。响应 data：`SessionResponse`

### 删除会话

```
DELETE /api/sessions/{id}
```

需要登录。响应 data：`null`

---

## 消息接口

### 获取消息列表

```
GET /api/sessions/{sessionId}/messages
```

需要登录。响应 data：`MessageResponse[]`

### 发送消息（流式）

```
POST /api/sessions/{sessionId}/messages/stream
```

需要登录。

请求体：
```json
{
  "content": "这个视频讲了什么？"
}
```

响应类型：`text/event-stream`

#### SSE 事件

**开始事件**
```
event: start
data: {"type":"start","userMessageId":1}
```

**内容增量**
```
event: content
data: {"type":"content","delta":"这个视频"}
```

**结束事件**
```
event: end
data: {"type":"end","assistantMessageId":2,"fullContent":"完整回答"}
```

**错误事件**
```
event: error
data: {"type":"error","message":"错误信息"}
```

---

## 数据结构

### UserResponse

```json
{
  "id": 1,
  "username": "test_user",
  "createTime": "2026-05-12 10:00:00",
  "token": "satoken-value"
}
```

`token` 只在登录响应中一定存在。

### VideoResponse

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

`status`：`IMPORTING` / `SUCCESS` / `FAILED`

### SessionResponse

```json
{
  "id": 1,
  "sessionType": "SINGLE_VIDEO",
  "videoId": 1,
  "videoTitle": "视频标题",
  "createTime": "2026-05-12 10:00:00"
}
```

`sessionType`：`SINGLE_VIDEO` / `ALL_VIDEOS`

### MessageResponse

```json
{
  "id": 1,
  "role": "USER",
  "content": "这个视频讲了什么？",
  "createTime": "2026-05-12 10:00:00"
}
```

`role`：`USER` / `ASSISTANT`
