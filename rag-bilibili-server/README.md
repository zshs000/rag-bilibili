# RAG-Bilibili 后端服务

基于 Spring Boot 的 B 站视频字幕 RAG 问答系统后端。

## 技术栈

| 组件 | 版本 | 用途 |
|------|------|------|
| Java | 17 | 运行环境 |
| Spring Boot | 3.2.0 | 应用框架 |
| Spring AI | 1.1.2 | AI 能力集成 |
| MyBatis | 3.0.3 | ORM |
| MySQL | 8.0+ | 业务数据存储 |
| DashVector | - | 向量数据库 |
| Sa-Token | 1.44.0 | 鉴权框架 |

## 环境要求

- JDK 17+
- Maven 3.6+
- MySQL 8.0+
- 阿里云 DashVector 账号
- 阿里云百炼 API Key

## 快速启动

### 1. 配置环境变量

复制环境变量模板：

```bash
cp .env.example .env
```

配置以下变量：

```bash
# 阿里云百炼 API
DASHSCOPE_API_KEY=your_dashscope_api_key

# 阿里云 DashVector 向量数据库
DASHVECTOR_API_KEY=your_dashvector_api_key
DASHVECTOR_ENDPOINT=your_dashvector_endpoint
```

### 2. 启动服务

```bash
mvn spring-boot:run
```

或打包后运行：

```bash
mvn clean package -DskipTests
java -jar target/rag-bilibili-server-1.0.0-SNAPSHOT.jar
```

**数据库会自动创建**：连接地址带 `createDatabaseIfNotExist=true`，Flyway 自动执行迁移建表。

## 核心流程

```
视频导入：输入 BV 号 → 解析 → 读取字幕 → [失败则探测重试] → 清洗 → 分片 → 向量化 → 入库

RAG 对话：用户提问 → 向量检索 → 构建上下文 → 调用 LLM → SSE 流式返回
```

## 测试

```bash
mvn test
```

## 相关文档

- [后端启动配置](./后端启动配置文档.md)
- [字幕导入事务边界重构设计](../docs/backend/字幕导入事务边界重构设计.md)
- [字幕清洗多行窗口设计与实现说明](../docs/backend/字幕清洗多行窗口设计与实现说明.md)
