# RAG-Bilibili 后端服务

基于 B 站视频字幕内容的检索增强问答系统后端服务。

## 项目结构

```
rag-bilibili-server/
├── src/main/java/com/example/ragbilibili/
│   ├── RagBilibiliApplication.java          # 主应用类
│   ├── config/                               # 配置类
│   │   ├── SpringAIConfig.java              # Spring AI 配置
│   │   └── WebConfig.java                   # Web MVC 配置
│   ├── controller/                           # 控制器层
│   │   ├── AuthController.java              # 认证控制器
│   │   ├── VideoController.java             # 视频控制器
│   │   ├── SessionController.java           # 会话控制器
│   │   └── MessageController.java           # 消息控制器
│   ├── service/                              # 服务层接口
│   │   ├── UserService.java
│   │   ├── VideoService.java
│   │   ├── SessionService.java
│   │   ├── MessageService.java
│   │   └── ChatService.java
│   ├── service/impl/                         # 服务层实现（待实现）
│   ├── mapper/                               # MyBatis Mapper 接口
│   │   ├── UserMapper.java
│   │   ├── VideoMapper.java
│   │   ├── ChunkMapper.java
│   │   ├── VectorMappingMapper.java
│   │   ├── SessionMapper.java
│   │   └── MessageMapper.java
│   ├── entity/                               # 实体类
│   │   ├── User.java
│   │   ├── Video.java
│   │   ├── Chunk.java
│   │   ├── VectorMapping.java
│   │   ├── Session.java
│   │   └── Message.java
│   ├── dto/                                  # 数据传输对象
│   │   ├── request/                          # 请求 DTO
│   │   └── response/                         # 响应 DTO
│   ├── enums/                                # 枚举类
│   │   ├── SessionType.java
│   │   ├── MessageRole.java
│   │   └── VideoStatus.java
│   ├── exception/                            # 异常处理
│   │   ├── BusinessException.java
│   │   ├── ErrorCode.java
│   │   └── GlobalExceptionHandler.java
│   ├── util/                                 # 工具类
│   │   ├── BVIDParser.java
│   │   ├── VectorIDGenerator.java
│   │   └── PasswordEncoder.java
│   ├── common/                               # 通用类
│   │   └── Result.java
│   └── interceptor/                          # 拦截器
│       └── LoginInterceptor.java
├── src/main/resources/
│   ├── application.yml                       # 应用配置
│   ├── schema.sql                            # 数据库初始化脚本
│   └── mapper/                               # MyBatis XML 映射文件（待创建）
└── pom.xml                                   # Maven 配置

```

## 技术栈

- **Java**: 17
- **Spring Boot**: 3.2.0
- **Spring AI**: 1.0.0-M4
- **MyBatis**: 3.0.3
- **MySQL**: 8.0+
- **DashVector**: 阿里云向量数据库
- **jBCrypt**: 0.4（密码加密）

## 环境要求

- JDK 17+
- Maven 3.6+
- MySQL 8.0+
- DashVector 账号
- 阿里云百炼 API Key
- OpenAI 兼容接口

## 配置说明

### 1. 配置文件

复制配置文件模板：

```bash
cp src/main/resources/application.yml.example src/main/resources/application.yml
```

修改 `application.yml` 中的数据库密码：

```yaml
spring:
  datasource:
    password: your_actual_password
```

### 2. 环境变量

复制环境变量模板：

```bash
cp .env.example .env
```

配置以下环境变量（在 `.env` 文件中或系统环境变量）：

```bash
# OpenAI API
OPENAI_API_KEY=your_openai_api_key
OPENAI_BASE_URL=https://api.openai.com

# 阿里云 DashScope API
DASHSCOPE_API_KEY=your_dashscope_api_key

# 阿里云 DashVector 向量数据库
DASHVECTOR_API_KEY=your_dashvector_api_key
DASHVECTOR_ENDPOINT=your_dashvector_endpoint
```

### 3. 初始化数据库

创建数据库：

```sql
CREATE DATABASE rag_bilibili CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

执行 `src/main/resources/schema.sql` 创建数据库表。

## 构建和运行

### 构建项目

```bash
mvn clean package
```

### 运行项目

```bash
mvn spring-boot:run
```

或者：

```bash
java -jar target/rag-bilibili-server-1.0.0.jar
```

## API 接口

### 认证接口

- `POST /api/auth/register` - 用户注册
- `POST /api/auth/login` - 用户登录
- `POST /api/auth/logout` - 用户登出
- `GET /api/auth/current` - 获取当前用户信息

### 视频管理接口

- `POST /api/videos` - 导入视频
- `GET /api/videos` - 获取视频列表
- `GET /api/videos/{id}` - 获取视频详情
- `DELETE /api/videos/{id}` - 删除视频

### 会话管理接口

- `POST /api/sessions` - 创建会话
- `GET /api/sessions` - 获取会话列表
- `GET /api/sessions/{id}` - 获取会话详情
- `DELETE /api/sessions/{id}` - 删除会话

### 对话接口

- `POST /api/sessions/{id}/messages/stream` - 流式发送消息（SSE）
- `GET /api/sessions/{id}/messages` - 获取消息列表

## 开发状态

### 已完成

- ✅ 项目基础结构搭建
- ✅ 实体类定义
- ✅ DTO 类定义
- ✅ Mapper 接口定义
- ✅ Service 接口定义
- ✅ Service 层实现
- ✅ Controller 层实现
- ✅ MyBatis XML 映射文件
- ✅ 全局异常处理
- ✅ 登录拦截器
- ✅ 工具类实现
- ✅ 单元测试（Controller 层）
- ✅ Spring AI Alibaba 组件集成

## 测试

运行单元测试：

```bash
mvn test
```

测试覆盖：
- AuthControllerTest: 4 个测试用例
- VideoControllerTest: 4 个测试用例
- SessionControllerTest: 5 个测试用例
- MessageControllerTest: 2 个测试用例

## 安全说明

- 所有敏感信息（API Keys、数据库密码）使用环境变量配置
- `.gitignore` 已配置忽略 `.env` 和本地配置文件
- 请勿将包含真实密钥的文件提交到 Git
- 生产环境建议使用系统环境变量而非 `.env` 文件

## 参考文档

- [系统需求规约](../系统需求规约.md)
- [概要设计](../概要设计.md)
- [详细设计](../详细设计.md)
- [向量化实现说明](../向量化实现说明.md)
