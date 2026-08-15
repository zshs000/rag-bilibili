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
| 2006 | 导入批次不存在 |
| 2007 | 批量导入数量超限 |
| 2008 | 批量导入凭证加密配置无效 |
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

### 创建批量导入任务

```
POST /api/video-import-batches
```

需要登录。`inputs` 接收 1～50 个非空的视频链接或 BV 号。批内重复、当前用户已导入或正在导入的视频会创建为 `SKIPPED` 明细，不会导致整批失败。Cookie 仅用于本批次后台导入，不会出现在响应中。

请求体：

```json
{
  "inputs": [
    "BV1DCfsBKExV",
    "https://www.bilibili.com/video/BV1iH3763Ezm"
  ],
  "sessdata": "xxx",
  "biliJct": "xxx",
  "buvid3": "xxx"
}
```

响应 data：`VideoImportBatchResponse`。

可能的业务错误：

- `2007`：非空输入超过 50 个。
- `2008`：服务端未正确配置批量导入凭证加密密钥。

### 获取最近导入批次

```
GET /api/video-import-batches
```

需要登录。响应 data：当前用户最近 20 个 `VideoImportBatchResponse[]`，按创建时间倒序返回；列表响应中的 `items` 为空数组。

### 获取导入批次详情

```
GET /api/video-import-batches/{id}
```

需要登录。响应 data：包含明细的 `VideoImportBatchResponse`。批次不存在或不属于当前用户时均返回 `2006`，不泄露其他用户数据。

### 重试批次失败项

```
POST /api/video-import-batches/{id}/retry-failed
```

需要登录。仅将该批次的 `FAILED` 明细重新置为 `QUEUED` 并唤醒后台处理，不复制成功或已跳过项。响应 data：更新后的 `VideoImportBatchResponse`。批次不存在或不属于当前用户时返回 `2006`。

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
data: {"type":"end","assistantMessageId":2,"fullContent":"完整回答[1]","sources":[{"index":1,"bvid":"BV1DCfsBKExV","videoTitle":"视频标题","pageNumber":1,"startTimeMs":130000,"endTimeMs":156000,"snippet":"回答所依据的字幕片段","jumpUrl":"https://www.bilibili.com/video/BV1DCfsBKExV/?p=1&t=127.5"}]}
```

`sources` 始终为数组，只包含回答正文中实际出现且可映射到本次检索结果的 `[n]` 引用。旧消息、用户消息或没有合法引用的回答返回空数组。跳转地址由后端根据来源快照生成，默认比字幕片段起点提前 2.5 秒。

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

### VideoImportBatchResponse

```json
{
  "id": 10,
  "status": "RUNNING",
  "totalCount": 3,
  "queuedCount": 1,
  "runningCount": 1,
  "succeededCount": 0,
  "skippedCount": 1,
  "failedCount": 0,
  "createTime": "2026-08-16 10:00:00",
  "updateTime": "2026-08-16 10:00:02",
  "finishTime": null,
  "items": [
    {
      "id": 101,
      "originalInput": "BV1DCfsBKExV",
      "bvid": "BV1DCfsBKExV",
      "status": "RUNNING",
      "failReason": null,
      "retryCount": 0,
      "videoId": null,
      "createTime": "2026-08-16 10:00:00",
      "startTime": "2026-08-16 10:00:02",
      "finishTime": null
    }
  ]
}
```

批次 `status`：

- `RUNNING`：仍有 `QUEUED` 或 `RUNNING` 明细。
- `COMPLETED`：全部明细均为 `SUCCEEDED` 或 `SKIPPED`。
- `PARTIAL_FAILED`：全部明细结束且至少一项为 `FAILED`。

明细 `status`：`QUEUED` / `RUNNING` / `SUCCEEDED` / `SKIPPED` / `FAILED`。时间字段格式与其他 REST 响应一致，均为 `yyyy-MM-dd HH:mm:ss`。

### MessageResponse

```json
{
  "id": 1,
  "role": "USER",
  "content": "这个视频讲了什么？",
  "createTime": "2026-05-12 10:00:00",
  "sources": []
}
```

`role`：`USER` / `ASSISTANT`

助手消息引用来源时，`sources` 元素结构如下：

```json
{
  "index": 1,
  "bvid": "BV1DCfsBKExV",
  "videoTitle": "视频标题",
  "pageNumber": 1,
  "startTimeMs": 130000,
  "endTimeMs": 156000,
  "snippet": "回答所依据的字幕片段",
  "jumpUrl": "https://www.bilibili.com/video/BV1DCfsBKExV/?p=1&t=127.5"
}
```

该结构是生成回答时的来源快照，不依赖对应视频继续保存在本地知识库中。`jumpUrl` 指向 B站网页端对应分 P 和时间位置。
