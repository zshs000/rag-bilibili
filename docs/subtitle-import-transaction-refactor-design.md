# 字幕导入事务边界重构方案

## 1. 背景
当前 `VideoServiceImpl#importVideo()` 同时承担了两类职责：

1. **慢速 I/O 编排**
   - 调 B 站接口读取字幕
   - 调用 Playwright 探测页面字幕按钮
   - 失败后重试读取字幕
   - 清洗字幕、分片、构造向量文档

2. **数据库持久化**
   - 插入视频记录
   - 插入分片记录
   - 插入向量映射记录
   - 更新视频状态

当前方法整体挂了 `@Transactional`，导致慢速 I/O 和数据库事务被包在同一个方法里。

---

## 2. 当前实现存在的问题

### 2.1 事务范围过大
当前 `importVideo()` 在进入慢速 I/O 之前已经执行了数据库访问：

- `videoMapper.selectByUserIdAndBvid(userId, bvid)`

后续又继续执行：

- `readDocuments(resource)`
- `subtitleProbeService.probe(...)`
- `retryReadDocuments(...)`
- `Thread.sleep(...)`

这意味着事务生命周期覆盖了大量网络请求、外部进程调用和等待时间。

### 2.2 并发下会放大连接池压力
在高并发导入场景下，长事务会直接带来几个问题：

- 数据库连接占用时间变长
- 连接池可用连接数下降
- 其他请求容易被阻塞
- 系统吞吐量下降

问题的关键不在于事务代理是否“立刻拿连接”，而在于：

> 当前代码路径在慢 I/O 之前已经发生数据库访问，因此连接会被提前引入，并随着事务范围一起被拉长。

### 2.3 外部副作用放在事务内部
当前事务内部还包含：

- `dashVectorStore.add(indexedDocuments)`

这属于外部系统写入，不适合直接放在数据库事务边界内。否则会带来两个问题：

1. 外部 I/O 变慢时，数据库事务一起被拖长
2. 一旦数据库提交和向量写入之间出现异常，状态一致性处理会比较别扭

### 2.4 当前方法职责过重
当前 `importVideo()` 同时负责：

- 输入解析
- 字幕读取
- probe 探测
- 重试补偿
- 清洗与分片
- 向量构造
- 数据库落库
- 状态更新
- 失败处理

方法已经具备明显的“编排器 + 持久化器 + 补偿器”混合特征，不利于后续继续演进。

---

## 3. 重构目标
本次重构的目标有三个：

### 3.1 收缩事务边界
把事务收缩到真正需要数据库一致性的步骤：

- 创建视频导入记录
- 批量插入 chunk
- 批量插入 vector mapping
- 更新视频状态

### 3.2 把慢 I/O 统一挪到事务外
事务外完成：

- 字幕读取
- 页面字幕按钮探测
- 有限重试
- 清洗
- 分片
- 构造向量文档

### 3.3 显式补偿外部副作用
对向量库写入建立补偿路径，避免出现：

- 向量已写入，但数据库落库失败
- 数据库标记失败，但向量脏数据仍然保留

---

## 4. 推荐方案：编排层 + 事务持久化层 + 补偿层

### 4.1 编排层
保留在 `VideoServiceImpl`。

职责：

- 解析 `bvid`
- 构造 `BilibiliCredentials`
- 读取字幕
- probe 探测
- 失败后重试
- 清洗字幕
- 分片
- 生成向量文档
- 调事务服务
- 调向量库
- 处理失败补偿

这里不挂事务。

### 4.2 事务持久化层
新增：`VideoImportTxService`

职责：

- 创建 `IMPORTING` 视频记录
- 批量插入 `chunk`
- 批量插入 `vector_mapping`
- 更新 `SUCCESS`

这里的方法使用短事务。

### 4.3 补偿层
继续复用：`VideoStatusWriter`

职责：

- 在独立事务中把视频状态写成 `FAILED`

同时在编排层负责：

- 如果 DashVector 已写入但数据库最终落库失败，调用 `dashVectorStore.delete(vectorIds)` 删除脏向量

---

## 5. 调整后的调用链

### 第一步：事务外准备导入数据
由编排层完成：

- `parse bvid`
- `readDocuments`
- `probe`
- `retryReadDocuments`
- `subtitleCleaningTransformer.apply`
- `tokenTextSplitter.apply`
- 构造 `indexedDocuments`
- 生成 `vectorIds`
- 生成待落库的 chunk payload

建议抽成：

- `prepareImportData(...)`
- `loadDocumentsWithProbeAndRetry(...)`
- `buildPreparedImportData(...)`

### 第二步：短事务创建视频记录
调用：

- `videoImportTxService.createImportingVideo(...)`

只做：

- 再查一次 `(userId, bvid)` 是否重复
- 插入 `video`
- 状态置为 `IMPORTING`

### 第三步：事务外写入 DashVector
调用：

- `dashVectorStore.add(indexedDocuments)`

### 第四步：短事务落最终数据库结果
调用：

- `videoImportTxService.finalizeImportSuccess(...)`

只做：

- 批量插入 `chunk`
- 批量插入 `vector_mapping`
- 更新 `video.status = SUCCESS`

### 第五步：失败补偿
如果第三步或第四步失败：

- 若视频记录已创建：调用 `videoStatusWriter.markFailed(...)`
- 若向量已成功写入但数据库最终落库失败：调用 `dashVectorStore.delete(vectorIds)` 做补偿删除

---

## 6. 建议新增的数据对象
为避免事务前准备阶段和事务落库阶段之间出现大量参数传递，建议新增：

### `PreparedVideoImportData`
建议字段：

- `bvid`
- `title`
- `description`
- `cleanedDocuments`
- `indexedDocuments`
- `vectorIds`
- `chunkPayloads`

### `PreparedChunkData`
建议字段：

- `chunkIndex`
- `totalChunks`
- `chunkText`
- `vectorId`

这样事务层只负责“把准备好的结果落库”，不再重复做清洗、切分、组装。

---

## 7. 推荐的方法划分

### `VideoServiceImpl`
建议保留或新增：

- `importVideo(...)`
- `prepareImportData(...)`
- `loadDocumentsWithProbeAndRetry(...)`
- `buildPreparedImportData(...)`
- `handleImportFailure(...)`

### `VideoImportTxService`
建议新增：

- `createImportingVideo(...)`
- `finalizeImportSuccess(...)`

### `VideoStatusWriter`
继续保留：

- `markFailed(...)`

---

## 8. 状态流转建议
推荐使用下面这条状态流转：

- `IMPORTING`
- `SUCCESS`
- `FAILED`

### 成功路径
1. 事务外准备字幕和分片
2. 事务内创建 `IMPORTING` 视频记录
3. 事务外写 DashVector
4. 事务内插入 chunk / mapping，更新 `SUCCESS`

### 失败路径 A：向量库写失败
1. 视频记录已创建
2. DashVector 写失败
3. 独立事务更新 `FAILED`

### 失败路径 B：向量库成功，数据库 finalize 失败
1. DashVector 已写入
2. 数据库 finalize 事务失败
3. 删除已写入的向量
4. 独立事务更新 `FAILED`

---

## 9. 常见疑问说明

### 9.1 旧实现的真实执行顺序是什么
旧实现的 `importVideo()` 是一个大事务，主链路顺序如下：

1. `parse bvid`
2. `selectByUserIdAndBvid(...)` 查重
3. `reader.get()` 读取字幕
4. 清洗字幕
5. 插入 `video`，状态置为 `IMPORTING`
6. 批量插入 `chunk`
7. `dashVectorStore.add(indexedDocuments)`
8. 批量插入 `vector_mapping`
9. 更新 `video.status = SUCCESS`

这里的关键点是：`dashVectorStore.add(...)` 位于数据库主链路中间，但它本身并不受 MySQL 事务控制。

### 9.2 旧实现里的独立事务解决了什么
旧实现里只有 `VideoStatusWriter.markFailed(...)` 使用了 `REQUIRES_NEW` 独立事务。

它解决的是：

- 外层 `importVideo()` 抛异常时
- `FAILED` 状态不要跟着外层数据库事务一起回滚

它没有解决下面这些事：

- 向量库写入和 MySQL 一起原子提交
- 向量库写入后的自动回滚
- 向量脏数据清理

也就是说，独立事务只负责“失败状态要落库”，并不负责“向量一致性”。

### 9.3 旧实现为什么会产生孤儿向量
旧实现里，如果流程执行到下面这个位置：

1. `video` 已插入
2. `chunk` 已插入
3. `dashVectorStore.add(...)` 已成功
4. 后续 `vectorMappingMapper.batchInsert(...)` 或 `videoMapper.update(...)` 失败

这时会出现：

- MySQL 外层事务回滚，`video / chunk / mapping` 一起撤销
- DashVector 中已经写入的向量不会跟着回滚

于是就会留下“数据库里没有记录，但向量库里还有数据”的孤儿向量。

### 9.4 现在的实现为什么叫补偿删除，不叫回滚
因为向量库不是 Spring 数据库事务的一部分。

当前实现里：

1. 事务外准备字幕、分片和向量数据
2. 短事务创建 `IMPORTING` 视频记录
3. 事务外调用 `dashVectorStore.add(...)`
4. 短事务落 `chunk / vector_mapping / SUCCESS`

如果第 3 步成功了，但第 4 步失败了，系统不会对向量库做“自动回滚”，而是显式执行一段补偿逻辑：

- 调用 `dashVectorStore.delete(vectorIds)` 删除刚刚写入的向量
- 再调用 `videoStatusWriter.markFailed(...)` 把视频状态写成 `FAILED`

所以这里的语义是“失败后补偿清理”，不是“跨资源自动回滚”。

### 9.5 现在的失败处理具体分几种
可以按是否已经创建视频记录、是否已经写入向量来理解：

#### 情况 A：准备阶段失败
例如：

- 字幕读取失败
- probe 后仍读取失败
- 清洗后无有效内容

这时还没有创建 `video`，也没有写向量，直接返回失败即可。

#### 情况 B：`video` 已创建，但向量写入失败
例如：

- `createImportingVideo(...)` 成功
- `dashVectorStore.add(...)` 失败

这时没有成功写入的向量需要删除，但会通过独立事务执行：

- `videoStatusWriter.markFailed(...)`

#### 情况 C：向量已写入，但数据库 finalize 失败
例如：

- `dashVectorStore.add(...)` 成功
- `finalizeImportSuccess(...)` 失败

这时会执行两步：

1. `dashVectorStore.delete(vectorIds)` 删除脏向量
2. `videoStatusWriter.markFailed(...)` 写入 `FAILED`

这正是新方案用来收敛孤儿向量问题的关键补偿路径。

### 9.6 “短事务创建 IMPORTING 视频”是什么意思
短事务的意思是：

- 事务里只保留很快的数据库操作
- 做完就提交
- 不把外部网络请求、Playwright 探测、重试等待、向量库写入这些慢操作包进去

`createImportingVideo(...)` 这个短事务只做三件事：

1. 再查一次 `(userId, bvid)` 是否重复
2. 插入 `video`
3. 把状态置为 `IMPORTING`

执行完立即提交，不再继续持有数据库事务。

同理，`finalizeImportSuccess(...)` 也是一个短事务，只负责：

- 插入 `chunk`
- 插入 `vector_mapping`
- 更新 `video.status = SUCCESS`

这种拆法的目的，就是让数据库事务只覆盖真正需要数据库一致性的那一小段代码。

---

## 10. 还需要补的数据库兜底
建议为 `(user_id, bvid)` 增加唯一约束。

原因：

- 事务外准备阶段会拉长“请求开始到真正落库”的时间窗口
- 两个并发请求可能同时通过业务层重复校验
- 最终仍需要数据库唯一索引兜底

推荐策略：

- 业务层先查一次，保留友好提示
- 数据库唯一约束做最终兜底
- 撞库时把异常转换成 `VIDEO_ALREADY_EXISTS`

---

## 11. 方案收益

### 并发收益
- 显著缩短数据库事务时间
- 降低连接池占用压力
- 改善并发导入时的系统稳定性

### 结构收益
- `VideoServiceImpl` 变成流程编排器
- 事务边界清晰
- 向量库副作用有明确补偿路径

### 演进收益
后续如果要继续做：

- 异步导入
- MQ 解耦
- 导入任务状态机
- 更细粒度的失败恢复

这套结构都更容易继续演进。

---

## 12. 一句话总结
当前实现的问题在于：

> 慢速字幕读取链路和数据库事务绑定在了一起，事务范围过大，外部副作用又直接放在事务内部，并发下会放大连接池压力，也会让导入链路的一致性处理变得笨重。

推荐改法是：

> 把导入流程拆成“事务外准备 + 短事务落库 + 外部副作用补偿”三段式结构，让事务只负责数据库一致性，把慢 I/O 和补偿逻辑显式收拢到编排层。
