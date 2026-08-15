# Batch Video Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 支持用户通过多行视频链接或 BV 号创建可恢复的异步导入批次，并查看、重试批次任务。

**Architecture:** 使用 MySQL 持久化批次和明细状态，独立的两线程执行器通过条件更新领取任务，实际视频处理复用 `VideoService`。前端在现有导入页增加批量模式，通过 REST 轮询进度并展示最近 20 个批次。

**Tech Stack:** Java 17、Spring Boot、MyBatis、Flyway、MySQL、AES-GCM、Vue 3、Element Plus、Axios。

---

## Task 1: 固化 HTTP 契约

**Files:**
- Modify: `docs/API.md`

- [ ] 在视频接口章节定义 `POST /api/video-import-batches`，请求字段为 `inputs`、`sessdata`、`biliJct`、`buvid3`，说明最多 50 个非空输入。
- [ ] 定义 `GET /api/video-import-batches`、`GET /api/video-import-batches/{id}` 和 `POST /api/video-import-batches/{id}/retry-failed` 的认证、响应字段、状态枚举和错误语义。
- [ ] 运行 `git diff --check -- docs/API.md`，预期无空白错误。
- [ ] 提交 `docs(api): 定义批量视频导入接口`。

## Task 2: 建立批次持久化模型

**Files:**
- Create: `rag-bilibili-server/src/main/resources/db/migration/V4__add_video_import_batch.sql`
- Create: `rag-bilibili-server/src/main/java/com/example/ragbilibili/entity/VideoImportBatch.java`
- Create: `rag-bilibili-server/src/main/java/com/example/ragbilibili/entity/VideoImportItem.java`
- Create: `rag-bilibili-server/src/main/java/com/example/ragbilibili/enums/VideoImportBatchStatus.java`
- Create: `rag-bilibili-server/src/main/java/com/example/ragbilibili/enums/VideoImportItemStatus.java`
- Create: `rag-bilibili-server/src/main/java/com/example/ragbilibili/mapper/VideoImportBatchMapper.java`
- Create: `rag-bilibili-server/src/main/java/com/example/ragbilibili/mapper/VideoImportItemMapper.java`
- Create: `rag-bilibili-server/src/main/resources/mapper/VideoImportBatchMapper.xml`
- Create: `rag-bilibili-server/src/main/resources/mapper/VideoImportItemMapper.xml`
- Test: `rag-bilibili-server/src/test/java/com/example/ragbilibili/mapper/VideoImportMapperContractTest.java`

- [ ] 编写 Mapper 契约测试，断言用户过滤、最近 20 条排序、`QUEUED -> RUNNING` 条件领取、状态汇总和失败项重置 SQL 均存在。
- [ ] 运行 `mvn -Dtest=VideoImportMapperContractTest test`，预期因文件不存在而失败。
- [ ] 新增 Flyway 表、实体、枚举、Mapper 与 XML；凭证仅在批次表保存密文，明细保存原始输入、规范化 BVID、状态、失败原因、重试次数和关联视频。
- [ ] 再次运行相同测试，预期通过。
- [ ] 提交 `feat(import): 持久化批量导入任务`。

## Task 3: 加密批次凭证

**Files:**
- Create: `rag-bilibili-server/src/main/java/com/example/ragbilibili/config/BatchImportProperties.java`
- Create: `rag-bilibili-server/src/main/java/com/example/ragbilibili/service/BatchImportCredentials.java`
- Create: `rag-bilibili-server/src/main/java/com/example/ragbilibili/service/BatchCredentialCipher.java`
- Modify: `rag-bilibili-server/src/main/resources/application.yml`
- Test: `rag-bilibili-server/src/test/java/com/example/ragbilibili/service/BatchCredentialCipherTest.java`

- [ ] 编写 AES-GCM 往返、随机 IV、错误密钥和缺少密钥测试。
- [ ] 运行 `mvn -Dtest=BatchCredentialCipherTest test`，预期因类不存在而失败。
- [ ] 使用 `BATCH_IMPORT_CREDENTIAL_KEY` 提供的 Base64 256 位密钥实现 AES-GCM；缺少或非法密钥时创建批次返回明确业务错误，任何日志都不输出明文。
- [ ] 运行相同测试，预期通过。
- [ ] 提交 `feat(import): 加密保存批量导入凭证`。

## Task 4: 实现批次应用服务与 API

**Files:**
- Create: `rag-bilibili-server/src/main/java/com/example/ragbilibili/dto/request/CreateVideoImportBatchRequest.java`
- Create: `rag-bilibili-server/src/main/java/com/example/ragbilibili/dto/response/VideoImportBatchResponse.java`
- Create: `rag-bilibili-server/src/main/java/com/example/ragbilibili/dto/response/VideoImportItemResponse.java`
- Create: `rag-bilibili-server/src/main/java/com/example/ragbilibili/service/VideoImportBatchService.java`
- Create: `rag-bilibili-server/src/main/java/com/example/ragbilibili/service/impl/VideoImportBatchServiceImpl.java`
- Create: `rag-bilibili-server/src/main/java/com/example/ragbilibili/service/impl/VideoImportBatchTxService.java`
- Create: `rag-bilibili-server/src/main/java/com/example/ragbilibili/controller/VideoImportBatchController.java`
- Modify: `rag-bilibili-server/src/main/java/com/example/ragbilibili/exception/ErrorCode.java`
- Test: `rag-bilibili-server/src/test/java/com/example/ragbilibili/service/impl/VideoImportBatchServiceImplTest.java`
- Test: `rag-bilibili-server/src/test/java/com/example/ragbilibili/controller/VideoImportBatchControllerTest.java`

- [ ] 编写创建批次测试，覆盖混合输入、批内重复、非法输入、超过 50 项、已存在视频和活动任务跳过。
- [ ] 编写查询与重试测试，覆盖最近 20 条、批次详情、仅重试失败项和跨用户不可见。
- [ ] 运行两个测试类，预期因实现缺失而失败。
- [ ] 实现 DTO、事务写入、归属校验、响应转换和四个 Controller 接口；非法单项保存为 `FAILED`，不使整批失败。
- [ ] 运行两个测试类，预期通过。
- [ ] 提交 `feat(import): 提供批量导入任务接口`。

## Task 5: 实现并发二的可恢复调度

**Files:**
- Modify: `rag-bilibili-server/src/main/java/com/example/ragbilibili/config/TaskExecutorConfig.java`
- Create: `rag-bilibili-server/src/main/java/com/example/ragbilibili/service/impl/VideoImportBatchDispatcher.java`
- Modify: `rag-bilibili-server/src/main/java/com/example/ragbilibili/service/impl/VideoImportBatchTxService.java`
- Modify: `rag-bilibili-server/src/main/java/com/example/ragbilibili/service/impl/VideoImportBatchServiceImpl.java`
- Modify: `rag-bilibili-server/src/main/java/com/example/ragbilibili/service/impl/VideoImportTxService.java`
- Test: `rag-bilibili-server/src/test/java/com/example/ragbilibili/service/impl/VideoImportBatchDispatcherTest.java`
- Test: `rag-bilibili-server/src/test/java/com/example/ragbilibili/service/impl/VideoImportTxServiceTest.java`

- [ ] 编写调度测试，覆盖最多两个 worker、原子领取、单项失败隔离、批次汇总、终态清除密文、重试唤醒和启动恢复。
- [ ] 运行相关测试，预期因调度器缺失而失败。
- [ ] 新增独立两线程有界执行器；Dispatcher 每个 worker 循环领取任务并调用现有 `VideoService.importVideo`，完成后刷新批次状态。
- [ ] 允许现有 `FAILED` 视频记录安全恢复为 `IMPORTING`，使失败项重试不会永久命中重复视频错误。
- [ ] 使用应用启动事件把遗留 `RUNNING` 明细恢复为 `QUEUED` 并唤醒调度器；定时扫描补偿丢失的内存通知。
- [ ] 运行相关测试，预期通过。
- [ ] 提交 `feat(import): 异步调度批量视频导入`。

## Task 6: 实现前端批量导入体验

**Files:**
- Create: `rag-bilibili-front/src/api/video-import-batches.js`
- Create: `rag-bilibili-front/src/components/BatchImportPanel.vue`
- Modify: `rag-bilibili-front/src/views/ImportView.vue`
- Modify: `rag-bilibili-front/src/mock/dev-server.js`

- [ ] 在 API 模块实现创建、列表、详情和失败项重试，开发模式转发到本地 mock。
- [ ] 在 `BatchImportPanel.vue` 实现多行输入、Cookie 表单、最多 50 项提示、批次进度、单项状态、失败原因、失败重试和最近 20 个批次。
- [ ] 终态自动停止轮询，组件卸载时清理计时器，刷新页面后可从历史恢复详情。
- [ ] 在现有导入页使用标签页保留单视频模式并接入批量模式；扩展 mock 数据库和任务状态推进逻辑。
- [ ] 运行 `npm run build`，预期构建成功。
- [ ] 提交 `feat(frontend): 支持多行批量导入与进度查看`。

## Task 7: 完整验证与交付

- [ ] 运行 `mvn test`，预期所有后端测试通过。
- [ ] 运行 `npm run build`，预期前端生产构建通过。
- [ ] 运行 `git diff --check master...HEAD`，预期无空白错误。
- [ ] 审查凭证不会进入响应、日志、异常或前端持久化；审查所有批次接口都有用户归属过滤。
- [ ] 检查变更范围，只提交本功能文件，不暂存用户已有未跟踪文件。
- [ ] 推送 `codex/batch-video-import` 并创建以 `master` 为基线的 PR；等待 CI 和仓库 AI 审查，修复合理问题后再次验证。
