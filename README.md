[![Java CI with Maven](https://github.com/zshs000/rag-bilibili/actions/workflows/maven.yml/badge.svg)](https://github.com/zshs000/rag-bilibili/actions/workflows/maven.yml)    [![Node.js CI](https://github.com/zshs000/rag-bilibili/actions/workflows/node.js.yml/badge.svg)](https://github.com/zshs000/rag-bilibili/actions/workflows/node.js.yml)
# rag-bilibili
基于 B 站视频字幕内容构建的检索增强问答系统。项目支持导入单个 Bilibili 视频，将字幕切分、向量化并写入知识库，再通过大模型结合检索结果进行流式问答。

当前仓库采用前后端分离结构：

- `rag-bilibili-server`：Spring Boot 后端，负责用户认证、视频导入、向量检索、会话管理与 SSE 流式问答
- `rag-bilibili-front`：Vue 3 前端，负责登录注册、视频导入、会话管理与对话界面

## 核心能力

- 用户注册、登录、JWT 鉴权
- 导入 B 站视频字幕内容，支持输入 BV 号或包含 BV 的视频 URL
- 自动切分文本并写入 DashVector 向量库
- 管理视频库、查看导入状态与失败原因
- 创建两类问答会话
- `SINGLE_VIDEO`：仅检索单个视频
- `ALL_VIDEOS`：检索当前用户导入的全部视频
- 基于 SSE 的流式问答输出
- 会话与消息历史持久化

## 技术栈

### 后端

- Java 17
- Spring Boot 3.2.0
- Spring AI 1.1.2
- Spring AI Alibaba DashScope
- DashVector
- MyBatis
- MySQL
- Flyway
- JWT

### 前端

- Vue 3
- Vite
- Vue Router
- Pinia
- Element Plus
- Axios

## 系统流程

```text
用户输入 BV 号 / 视频 URL
        |
        v
后端读取 B 站字幕与元数据
        |
        v
文本切分 -> MySQL 保存 chunk / mapping -> DashVector 写入向量
        |
        v
用户创建会话并提问
        |
        v
按会话范围检索相关片段
        |
        v
大模型基于检索上下文流式生成回答
```

## 仓库结构

```text
rag-bilibili/
├── rag-bilibili-front/          # Vue 3 前端
├── rag-bilibili-server/         # Spring Boot 后端
├── 系统需求规约.md
├── 概要设计.md
├── 详细设计.md
├── 向量化实现说明.md
└── 后端实现完成总结.md
```

后端核心目录：

```text
rag-bilibili-server/src/main/java/com/example/ragbilibili/
├── controller/                  # 认证、视频、会话、消息接口
├── service/impl/                # 核心业务实现
├── mapper/                      # MyBatis Mapper
├── entity/                      # 实体
├── dto/                         # 请求/响应/SSE DTO
├── config/                      # Spring AI、线程池、Web 配置
├── interceptor/                 # JWT 登录拦截器
└── util/                        # JWT、限流、BV 解析等工具
```

前端核心目录：

```text
rag-bilibili-front/src/
├── views/                       # 登录、注册、导入、视频、会话、聊天页面
├── api/                         # 认证、视频、会话、消息接口封装
├── stores/                      # Pinia 状态管理
├── router/                      # 路由与鉴权守卫
├── components/                  # 页面组件
└── utils/                       # SSE、格式化、日志、错误处理
```

## 环境要求

- JDK 17+
- Maven 3.6+
- Node.js 18+
- npm 9+
- MySQL 8.0+
- OpenAI 兼容模型服务
- 阿里云 DashScope API Key
- 阿里云 DashVector Collection

## 后端配置

后端默认配置文件为 [application.yml](./rag-bilibili-server/src/main/resources/application.yml)。

当前默认端口与主要依赖如下：

- 服务端口：`8080`
- 数据库：`rag_bilibili`
- DashScope Embedding 模型：`text-embedding-v4`
- OpenAI 兼容聊天模型：`deepseek-v3.2`
- DashVector Collection：`bilibili`

### 1. 配置数据库

当前后端默认连接：

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/rag_bilibili?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:root}
```

你可以直接修改 [application.yml](./rag-bilibili-server/src/main/resources/application.yml)，或者通过环境变量覆盖：

```powershell
$env:DB_USERNAME="root"
$env:DB_PASSWORD="your_password"
```

数据库表由 Flyway 自动初始化，当前核心表包括：

- `user`
- `video`
- `chunk`
- `vector_mapping`
- `session`
- `message`

### 2. 配置模型与向量服务

后端需要以下环境变量：

```powershell
$env:OPENAI_API_KEY="your_openai_api_key"
$env:OPENAI_BASE_URL="https://your-openai-compatible-endpoint"
$env:DASHSCOPE_API_KEY="your_dashscope_api_key"
$env:DASHVECTOR_API_KEY="your_dashvector_api_key"
$env:DASHVECTOR_ENDPOINT="your_dashvector_endpoint"
```

说明：

- `OPENAI_BASE_URL` 需要指向 OpenAI 兼容接口地址
- 聊天模型名、Embedding 模型名、DashVector collection 名都可以在 [application.yml](./rag-bilibili-server/src/main/resources/application.yml) 中调整
- DashVector collection 的向量维度需要与 Embedding 模型维度一致，当前配置为 `1024`

### 3. 启动后端

```powershell
cd rag-bilibili-server
mvn spring-boot:run
```

启动成功后默认访问：

```text
http://localhost:8080
```

## 前端配置

前端示例环境文件为 [rag-bilibili-front/.env.example](./rag-bilibili-front/.env.example)：

```env
VITE_API_BASE_URL=/api
VITE_PROXY_TARGET=http://localhost:8080
```

推荐在前端目录复制一份本地环境文件：

```powershell
cd rag-bilibili-front
Copy-Item .env.example .env
```

然后安装依赖并启动：

```powershell
cd rag-bilibili-front
npm install
npm run dev
```

默认访问地址：

```text
http://localhost:5173
```

Vite 开发服务器会把 `/api/*` 代理到 `VITE_PROXY_TARGET`，默认是 `http://localhost:8080`。

## 快速使用

1. 先启动 MySQL、后端和前端。
2. 进入 `http://localhost:5173`，注册并登录。
3. 在“导入视频”页面输入 BV 号或视频 URL。
4. 填写 B 站访问凭证：`SESSDATA`、`bili_jct`、`buvid3`。
5. 等待导入完成后，在“视频列表”中查看状态。
6. 创建单视频会话或全视频会话。
7. 在聊天页发起问题，查看基于检索结果的流式回答。

## 主要页面

- `/login`：登录
- `/register`：注册
- `/import`：导入视频
- `/videos`：视频列表
- `/sessions`：会话列表
- `/chat/:sessionId`：流式问答

## 后端接口概览

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/logout`
- `GET /api/auth/current`
- `POST /api/videos`
- `GET /api/videos`
- `GET /api/videos/{id}`
- `DELETE /api/videos/{id}`
- `POST /api/sessions`
- `GET /api/sessions`
- `GET /api/sessions/{id}`
- `DELETE /api/sessions/{id}`
- `POST /api/sessions/{sessionId}/messages/stream`
- `GET /api/sessions/{sessionId}/messages`

## CI / CD

仓库已配置 GitHub Actions 工作流：

- 前端：在 `push` / `pull_request` 到 `master` 时自动执行 `npm ci` 和 `npm run build`
- 后端：在 `push` / `pull_request` 到 `master` 时自动执行 Maven 打包
- 后端交付：`push` 到 `master` 时会将构建产物 JAR 自动上传到 ECS 服务器
- 协作辅助：新建 issue 后会自动生成摘要评论

相关工作流文件：

- [前端 CI](./.github/workflows/node.js.yml)
- [后端 CI](./.github/workflows/maven.yml)
- [Issue Summary](./.github/workflows/summary.yml)

## 当前实现说明

- 认证链路使用 JWT Bearer Token，不是服务端 Session/Cookie 登录态
- 视频导入依赖 B 站字幕读取能力；如果目标视频无字幕，导入会失败
- 注册开关由后端配置项 `register.enabled` 控制，默认开启
- 仓库内部分历史文档与当前代码实现存在少量差异，建议以实际工程配置与源码为准

## 测试

后端包含控制器、配置、异常处理和部分服务测试，位于：

```text
rag-bilibili-server/src/test/java/com/example/ragbilibili/
```

可执行：

```powershell
cd rag-bilibili-server
mvn test
```

前端可执行构建校验：

```powershell
cd rag-bilibili-front
npm run build
```

## 参考文档

- [后端 README](./rag-bilibili-server/README.md)
- [后端启动配置文档](./rag-bilibili-server/后端启动配置文档.md)
- [前端快速启动文档](./rag-bilibili-front/前端快速启动文档.md)
- [前端具体实现文档](./rag-bilibili-front/前端具体实现文档.md)
- [系统需求规约](./系统需求规约.md)
- [概要设计](./概要设计.md)
- [详细设计](./详细设计.md)
- [向量化实现说明](./向量化实现说明.md)


## 写在最后
rag-bilibili 并非传统意义上的纯敏捷开发，而是**AI 时代特有的混合开发模式**：
以**轻量化瀑布模型**完成前期设计与架构奠基，以**精益敏捷思想**支撑迭代协作与快速交付，以**AI 辅助高效编码**提升核心开发效率。三者深度融合，最终完成了这个项目从 0 到 1 的高效落地。
