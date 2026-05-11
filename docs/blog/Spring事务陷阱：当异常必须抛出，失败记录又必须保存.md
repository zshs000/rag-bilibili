# Spring 事务陷阱：当异常必须抛出，失败记录又必须保存

## 前言：事务失效的五种经典场景

在 Spring 开发中，`@Transactional` 声明式事务失效是一个老生常谈的话题。常见的失效场景包括：

### 1. 非 public 方法

```java
@Transactional
private void doSomething() {  // 事务不生效
    // ...
}
```

**原因**：Spring AOP 基于动态代理实现，代理只能拦截 public 方法。private 方法只能在对象内部通过 `this` 调用，不会经过代理。

### 2. final / static 方法

```java
@Transactional
public final void doSomething() {  // 事务不生效
    // ...
}
```

**原因**：AOP 通过子类化（CGLIB）或接口代理（JDK Proxy）实现，无法覆盖 final 方法。static 方法属于类而非对象，同样无法被代理。

### 3. 同类内部调用

```java
public class MyService {
    public void methodA() {
        this.methodB();  // methodB 的事务不生效
    }

    @Transactional
    public void methodB() {
        // ...
    }
}
```

**原因**：`this.methodB()` 是对象内部调用，绕过了 Spring 代理，直接调用了目标方法，`@Transactional` 注解失效。

### 4. 异常被 catch 吞掉

```java
@Transactional
public void doSomething() {
    try {
        riskyOperation();
    } catch (Exception e) {
        logger.error(e);  // 异常被吞掉，事务不回滚
    }
}
```

**原因**：Spring 代理只能看到从方法边界逃逸出来的异常。异常被 catch 住且没有重新抛出，代理认为方法正常返回，提交了不该提交的数据。

### 5. rollbackFor 配置错误

```java
@Transactional(rollbackFor = RuntimeException.class)
public void doSomething() throws IOException {
    throw new IOException();  // 不会回滚
}
```

**原因**：`@Transactional` 默认只回滚 `RuntimeException` 和 `Error`。如果抛出 checked exception（如 `IOException`），需要显式指定 `rollbackFor = Exception.class`。

---

## Spring 事务的 AOP 代理原理

Spring 的 `@Transactional` 是通过 **AOP 代理**实现的。当你调用 `service.doSomething()` 时，实际调用的是 Spring 生成的代理对象，大致等价于：

```java
// Spring 代理自动生成的逻辑（伪代码）
Object proxy_doSomething() {
    TransactionStatus tx = transactionManager.begin();  // 开启事务
    try {
        Object result = target.doSomething();  // 调用真实方法
        transactionManager.commit(tx);         // 正常返回 → 提交
        return result;
    } catch (RuntimeException e) {
        transactionManager.rollback(tx);       // 抛出异常 → 回滚
        throw e;
    }
}
```

**关键点**：Spring 代理只能看到从方法边界逃逸出来的异常。方法内部你自己 catch 住的异常，代理完全不知道。

---

## 一个更隐蔽的问题

以上五种场景都是"事务该生效却没生效"的问题，开发者写错了代码或配置，Spring 被骗了。但在实际开发中，我遇到了一个更隐蔽的情况：**Spring 完全正常工作，事务也正确回滚了，却依然产生了 bug**。

在我的 RAG-Bilibili 项目中（一个基于 B 站视频字幕的检索增强问答系统），有一个视频导入功能。导入流程包括：

1. 从 B 站读取字幕
2. 切分文本并写入 MySQL
3. 向量化后写入 DashVector 向量库

如果向量写入失败，我希望：
- **主流程数据**（INSERT video、INSERT chunks）应该回滚，避免留下垃圾数据
- **失败记录**（UPDATE status=FAILED）必须保存，让用户知道发生了什么

### 原始代码（有 Bug）

```java
@Service
public class VideoServiceImpl {

    @Transactional
    public VideoResponse importVideo(ImportVideoRequest request, Long userId) {

        // 1. 插入视频记录（status=IMPORTING）
        videoMapper.insert(video);

        try {
            // 2. 读取字幕、切分、写入 MySQL
            chunkMapper.batchInsert(chunks);

            // 3. 写入向量库
            dashVectorStore.add(documents);  // ← 这里可能抛异常

            // 4. 更新状态为成功
            video.setStatus(VideoStatus.SUCCESS.getCode());
            videoMapper.update(video);

        } catch (Exception e) {
            log.error("视频导入失败", e);

            // 5. 更新状态为失败
            video.setStatus(VideoStatus.FAILED.getCode());
            video.setFailReason(e.getMessage());
            videoMapper.update(video);  // ← 我以为这会保存

            throw new BusinessException(ErrorCode.VIDEO_IMPORT_FAILED);  // ← 重新抛出
        }

        return convertToResponse(video);
    }
}
```

### 实际发生了什么

```
Spring 代理开启事务
    │
    ├─ SQL1: INSERT video (status=IMPORTING)   在事务缓冲区
    ├─ SQL2: INSERT chunks                     在事务缓冲区
    ├─ dashVectorStore.add() → 抛异常
    │
    └─ catch 块:
         SQL3: UPDATE video SET status=FAILED  在事务缓冲区（我以为会保存）
         throw BusinessException               ← 异常逃出方法边界
                                                  ↓
Spring 代理捕获到异常 → ROLLBACK
    │
    ├─ SQL1 被撤销（INSERT video 消失）
    ├─ SQL2 被撤销（INSERT chunks 消失）
    └─ SQL3 被撤销（UPDATE status=FAILED 也消失）← Bug 所在
```

**最终结果**：数据库里什么都没有。用户看到报错，但视频列表里查不到任何导入记录，既不是 FAILED，也不是 IMPORTING。

---

## 问题的本质：矛盾

这个问题和前面五种"事务失效"都不一样：

| | 经典问题：异常被吞 | 我的问题 |
|---|---|---|
| 现象 | 操作失败了，但数据没回滚，脏数据留在库里 | 操作失败了，失败记录也没保存，什么都查不到 |
| 原因 | 异常没逃出方法边界，Spring 以为成功了，提交了不该提交的 | 异常逃出了方法边界，Spring 正确回滚了，但把"故意要留"的记录也一起回滚了 |
| 本质 | 事务提交了**不该提交**的 | 事务回滚了**不该回滚**的 |
| Spring 有没有错 | Spring 被骗了 | Spring 没有错，是需求本身有矛盾 |

### 矛盾的核心

> **"操作失败"这件事，同时需要两种完全相反的数据库行为。**

- **主流程的数据**（INSERT video、INSERT chunks）——失败了，**不应该保留**，必须回滚
- **失败记录本身**（UPDATE status=FAILED）——失败了，**必须保留**，否则用户永远不知道发生了什么

用一句话说：

> **同一次失败，一部分数据要撤销，另一部分数据要保留。**

而事务的定义就是"要么全提交，要么全回滚"。**一个事务做不到这件事**，这才是矛盾的核心。

### 为什么不能把异常吞掉？

有人可能会问：既然重新抛异常会导致回滚，那为什么不在 catch 里把异常吞掉，让方法正常返回？

```java
} catch (Exception e) {
    video.setStatus(FAILED);
    videoMapper.update(video);
    // 不再 throw，方法正常返回
}
```

这样确实能让 FAILED 状态保存，但会产生新的问题：

1. **Controller 无法感知失败**：Controller 依赖 `BusinessException` 才能返回错误响应给前端
2. **前端会误以为成功**：用户点击导入，前端显示"导入成功"，但实际上失败了
3. **业务语义错误**：导入失败是一个异常情况，应该通过异常机制传播，而不是正常返回

所以这个 bug 的本质是**两个正确需求之间的冲突**：

- 需求1：导入失败时，前端要收到错误提示 → 必须抛异常
- 需求2：导入失败时，数据库要有 FAILED 记录 → 必须在事务提交前写入

两个需求都合理，但同一个事务满足不了两者。

---

## 解决方案对比

### 方案 A：REQUIRES_NEW 独立事务（推荐）

新建一个独立的 Bean，将失败状态更新放进 `@Transactional(propagation = REQUIRES_NEW)` 的方法里，让它在独立事务中提交，不受外层事务回滚影响。

**新增 VideoStatusWriter.java**

```java
package com.example.ragbilibili.service.impl;

import com.example.ragbilibili.entity.Video;
import com.example.ragbilibili.enums.VideoStatus;
import com.example.ragbilibili.mapper.VideoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 视频状态写入器
 *
 * 使用独立事务（REQUIRES_NEW）更新视频失败状态，确保即使外层事务回滚，
 * 失败记录也能持久化。这解决了 @Transactional + catch-rethrow 场景下
 * 失败状态被一同回滚的问题。
 */
@Component
public class VideoStatusWriter {

    @Autowired
    private VideoMapper videoMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Video video, String reason) {
        video.setStatus(VideoStatus.FAILED.getCode());
        video.setFailReason(reason);
        videoMapper.update(video);
    }
}
```

**修改 VideoServiceImpl.java**

```java
@Service
public class VideoServiceImpl {

    @Autowired
    private VideoStatusWriter videoStatusWriter;

    @Transactional
    public VideoResponse importVideo(ImportVideoRequest request, Long userId) {

        videoMapper.insert(video);

        try {
            chunkMapper.batchInsert(chunks);
            dashVectorStore.add(documents);
            video.setStatus(VideoStatus.SUCCESS.getCode());
            videoMapper.update(video);

        } catch (Exception e) {
            log.error("视频导入失败", e);

            if (video != null) {
                // 用独立事务写入失败状态，防止被外层事务回滚
                videoStatusWriter.markFailed(video, e.getMessage());
            }

            throw new BusinessException(ErrorCode.VIDEO_IMPORT_FAILED);
        }

        return convertToResponse(video);
    }
}
```

**事务流程对比**

```
修复前：
  外层事务 [ INSERT video → INSERT chunks → UPDATE status=FAILED ] → ROLLBACK 全部

修复后：
  外层事务 [ INSERT video → INSERT chunks ] → ROLLBACK
  独立事务 [ UPDATE status=FAILED ] → COMMIT（不受外层影响）
```

**`REQUIRES_NEW` 的含义**：挂起外层事务，开启一个全新的独立事务，提交后再恢复外层事务。所以它的 commit 不受外层 rollback 影响。

**注意事项**：
- 必须拆成独立的 Bean，不能在同一个类里调用 `this.markFailed()`（会绕过代理，`REQUIRES_NEW` 不生效）
- 独立事务会立即提交，即使外层事务还在运行

---

### 方案 B：编程式事务

去掉 `@Transactional`，用 `TransactionTemplate` 手动控制事务边界。

```java
@Service
public class VideoServiceImpl {

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private PlatformTransactionManager txManager;

    // 外层方法不加 @Transactional，手动控制
    public VideoResponse importVideo(ImportVideoRequest request, Long userId) {

        // 主流程放在一个事务里
        Video video = transactionTemplate.execute(status -> {
            videoMapper.insert(v);
            chunkMapper.batchInsert(chunks);
            return v;
        });

        try {
            dashVectorStore.add(documents);
        } catch (Exception e) {
            // 失败记录用独立事务提交
            TransactionTemplate newTx = new TransactionTemplate(txManager);
            newTx.setPropagationBehavior(PROPAGATION_REQUIRES_NEW);
            newTx.execute(status -> {
                video.setStatus(FAILED);
                videoMapper.update(video);
                return null;
            });
            throw new BusinessException(ErrorCode.VIDEO_IMPORT_FAILED);
        }
    }
}
```

**优点**：
- 事务边界完全可见，不依赖 AOP，没有"同类内调用失效"的坑
- 适合事务边界动态变化、或在循环里精细控制的场景

**缺点**：
- 代码冗长，事务逻辑和业务逻辑混在一起
- 可读性差，不如声明式直观

---

### 方案选择建议

| 场景 | 推荐 |
|------|------|
| 逻辑简单，能拆独立 Bean | 声明式 + `REQUIRES_NEW` |
| 事务边界动态变化，或在循环里控制 | 编程式 |
| 需要在同一个类里调用，又不想拆 Bean | 编程式（避开 AOP 代理的限制） |
| 对事务行为要求极精细，不想依赖注解魔法 | 编程式 |

对我的场景来说，拆一个 `VideoStatusWriter` Bean 用声明式最干净。

---

## 深入理解 REQUIRES_NEW

### 性能代价

`REQUIRES_NEW` 会挂起外层事务、开启新事务，这意味着：
- **占用额外的数据库连接**：外层事务的连接被挂起但未释放，新事务又要一个新连接
- **事务切换开销**：挂起-恢复事务有上下文切换成本

**什么时候需要担心？**

| 场景 | 风险 | 建议 |
|------|------|------|
| 偶尔调用（如本文的导入失败） | 低 | 无需担心 |
| 在循环中调用（如批量处理 1000 条记录） | 高 | 改用批量操作或编程式事务 |
| 高并发接口（QPS > 连接池大小 / 事务平均时长） | 中 | 监控连接池使用率 |
| 连接池配置较小（< 20） | 中 | 考虑扩大连接池或优化事务时长 |

**监控建议**：

关注这些指标，如果出现异常说明 `REQUIRES_NEW` 使用过度：
- 数据库连接池使用率（超过 80% 需警惕）
- 连接等待时间（出现等待说明连接不够用）
- 事务平均持续时间（越长越容易耗尽连接）

**经验法则**：
- 如果 `REQUIRES_NEW` 方法在单次请求中只调用 1-2 次，通常没问题
- 如果在循环中调用，或单次请求调用超过 10 次，考虑重构

---

### 同类调用失效问题

这是一个经典的 Spring AOP 陷阱。

**为什么同类内部调用会失效？**

```java
@Service
public class VideoServiceImpl {

    @Transactional
    public void methodA() {
        this.methodB();  // methodB 的 @Transactional 不生效
    }

    @Transactional(propagation = REQUIRES_NEW)
    public void methodB() {
        // ...
    }
}
```

因为 `this` 指向的是**目标对象本身**，不是 Spring 代理对象。Spring 的事务增强逻辑在代理对象上，目标对象没有。

```
外部调用：
  Controller → Spring代理对象.methodA() → 开启事务 → 目标对象.methodA()
                                              ↑ 事务增强在这里

内部调用：
  目标对象.methodA() → this.methodB()
                        ↑ 直接调用目标对象，绕过了代理，没有事务增强
```

**解法 1：拆成独立 Bean（推荐）**

```java
@Component
public class VideoStatusWriter {
    @Transactional(propagation = REQUIRES_NEW)
    public void markFailed(Video video, String reason) {
        // ...
    }
}

@Service
public class VideoServiceImpl {
    @Autowired
    private VideoStatusWriter videoStatusWriter;  // 注入的是代理对象

    @Transactional
    public void importVideo(...) {
        try {
            // ...
        } catch (Exception e) {
            videoStatusWriter.markFailed(video, e.getMessage());  // 走代理
        }
    }
}
```

**解法 2：通过 `AopContext.currentProxy()` 获取代理对象**

如果不想多建一个类，可以用 `AopContext.currentProxy()` 在同一个类里获取代理对象：

```java
@Service
public class VideoServiceImpl {

    @Transactional
    public void importVideo(...) {
        try {
            // ...
        } catch (Exception e) {
            // 获取当前代理对象，调用它的方法
            ((VideoServiceImpl) AopContext.currentProxy()).markFailed(video, e.getMessage());
        }
    }

    @Transactional(propagation = REQUIRES_NEW)
    public void markFailed(Video video, String reason) {
        video.setStatus(VideoStatus.FAILED.getCode());
        video.setFailReason(reason);
        videoMapper.update(video);
    }
}
```

**为什么 `AopContext.currentProxy()` 可以？**

因为它拿到的是**当前线程的代理对象**，而不是 `this`。调用代理对象的方法，就会走 AOP 增强逻辑，`@Transactional` 就生效了。

**但需要开启代理暴露**：

```java
@SpringBootApplication
@EnableAspectJAutoProxy(exposeProxy = true)  // 必须开启
public class Application {
    // ...
}
```

**两种解法对比**：

| | 拆成独立 Bean | AopContext.currentProxy() |
|---|---|---|
| **优点** | 职责清晰，符合单一职责原则 | 不用多建类，代码集中 |
| **缺点** | 多一个类文件 | 耦合 Spring AOP API，可读性差 |
| **推荐** | 强烈推荐 | 可选方案 |

**建议**：优先用解法 1（拆 Bean）。`AopContext.currentProxy()` 虽然可行，但代码可读性差，且耦合了 Spring 的 API。拆一个 Bean 只是多一个文件，但职责更清晰，更符合面向对象设计原则。

---

### 什么时候绝对不要用 REQUIRES_NEW

`REQUIRES_NEW` 解决了"主流程回滚、失败记录保留"的问题，但它不是万能的。以下场景的写操作**必须**和主事务一起提交或一起回滚，**千万不要**拆成独立事务：

#### 1. 审计日志 / 操作轨迹表

记录"谁在什么时间做了什么"的日志，必须和业务操作同生共死。

**错误示例**：

```java
@Transactional
public void deleteUser(Long userId) {
    auditLogger.log("删除用户 " + userId);  // 用 REQUIRES_NEW 独立提交
    userMapper.delete(userId);  // 主事务
}
```

**问题**：如果 `userMapper.delete()` 失败回滚，审计日志已经提交，会留下"删除用户"的记录，但用户实际没被删除。审计日志失去了可信度。

**正确做法**：审计日志和业务操作在同一个事务里，要么都成功，要么都失败。

#### 2. 支付差错记录、退款流水

支付相关的记录必须和资金变动严格一致。

**错误示例**：

```java
@Transactional
public void refund(Long orderId) {
    refundLogMapper.insert(log);  // 用 REQUIRES_NEW 独立提交
    accountMapper.addBalance(userId, amount);  // 主事务
}
```

**问题**：如果加余额失败，退款日志已经提交，会出现"有退款记录、但钱没退"的情况，财务对账会炸。

#### 3. 风控命中记录、黑名单变更日志

风控决策和记录必须同步。

**错误示例**：

```java
@Transactional
public void blockUser(Long userId) {
    riskLogMapper.insert("拉黑用户 " + userId);  // 用 REQUIRES_NEW 独立提交
    blacklistMapper.insert(userId);  // 主事务
}
```

**问题**：如果拉黑失败，风控日志已经提交，会出现"有拉黑记录、但用户没被拉黑"的情况，风控系统失效。

#### 4. 关键业务状态变更的"见证记录"

例如订单状态机变更历史，必须和状态变更同步。

**错误示例**：

```java
@Transactional
public void cancelOrder(Long orderId) {
    orderHistoryMapper.insert("订单取消");  // 用 REQUIRES_NEW 独立提交
    orderMapper.updateStatus(orderId, CANCELLED);  // 主事务
}
```

**问题**：如果状态更新失败，历史记录已经提交，会出现"有取消记录、但订单没取消"的情况，状态机混乱。

---

### 总结：REQUIRES_NEW 的适用边界

| 场景 | 是否用 REQUIRES_NEW | 原因 |
|------|---------------------|------|
| 失败状态记录（本文场景） | 用 | 主流程失败，但失败记录要保留，让用户知道发生了什么 |
| 审计日志、操作轨迹 | 不用 | 必须和业务操作同生共死，否则日志失去可信度 |
| 支付流水、退款记录 | 不用 | 必须和资金变动严格一致，否则财务对账炸 |
| 风控记录、黑名单日志 | 不用 | 必须和风控决策同步，否则风控系统失效 |
| 状态机变更历史 | 不用 | 必须和状态变更同步，否则状态机混乱 |

**判断标准**：

- 如果这条记录是"业务操作的见证"（证明操作发生了），必须和业务操作在同一个事务
- 如果这条记录是"业务操作的失败通知"（告诉用户操作失败了），可以用 `REQUIRES_NEW` 独立提交

**一句话**：`REQUIRES_NEW` 适合"失败通知"，不适合"成功见证"。

---

## 扩展：Spring 事务的七种传播行为

`REQUIRES_NEW` 只是 Spring 事务传播行为的一种。完整的七种传播行为如下：

| 传播行为 | 含义 | 使用场景 |
|---------|------|---------|
| **REQUIRED**（默认） | 如果外层有事务，加入外层事务；否则新建事务 | 最常用，适合大部分场景 |
| **REQUIRES_NEW** | 挂起外层事务，新建独立事务 | 需要独立提交，不受外层回滚影响（如本文场景） |
| **SUPPORTS** | 如果外层有事务，加入；否则以非事务方式运行 | 查询操作，可有可无 |
| **NOT_SUPPORTED** | 挂起外层事务，以非事务方式运行 | 不需要事务的操作（如日志记录） |
| **MANDATORY** | 必须在外层事务中运行，否则抛异常 | 强制要求调用方提供事务 |
| **NEVER** | 必须在非事务环境中运行，否则抛异常 | 明确禁止事务的操作 |
| **NESTED** | 如果外层有事务，创建嵌套事务（Savepoint）；否则等同于 REQUIRED | 需要部分回滚的场景（较少用） |

**`REQUIRES_NEW` vs `NESTED` 的区别**：

- `REQUIRES_NEW`：完全独立的事务，外层回滚不影响它，它回滚也不影响外层
- `NESTED`：嵌套事务（基于 Savepoint），外层回滚会一起回滚，但它自己回滚不影响外层

### 深入理解：REQUIRES_NEW vs NESTED

这两个传播行为经常被混淆，但它们的实现机制和适用场景完全不同：

| | REQUIRES_NEW | NESTED |
|---|---|---|
| **物理实现** | 开启一个新的物理事务（新 Connection 或挂起当前事务） | 在同一物理事务里创建 Savepoint（不新起 Connection） |
| **外层回滚时** | 内层已提交的不受影响 | 内层一起回滚（Savepoint 失效） |
| **内层回滚时** | 不影响外层 | 不影响外层（回滚到 Savepoint） |
| **连接开销** | 高（需要额外连接或挂起-恢复） | 低（同一连接） |
| **数据库支持** | 所有数据库 | 需要数据库支持 Savepoint（MySQL InnoDB 支持） |

**为什么本文场景不能用 NESTED？**

我们的需求是：**外层回滚时，内层必须保留**（失败记录要持久化）。

- `REQUIRES_NEW`：内层独立提交，外层回滚不影响它
- `NESTED`：外层回滚会把 Savepoint 一起回滚，失败记录还是丢失

**NESTED 的适用场景**：

NESTED 适合"部分操作失败不影响主流程"的场景，例如：

```java
@Transactional
public void processOrder(Order order) {
    orderMapper.insert(order);  // 主流程

    try {
        sendNotification(order);  // 次要操作，用 NESTED
    } catch (Exception e) {
        // 发送通知失败，回滚到 Savepoint，但不影响订单插入
        log.warn("通知发送失败", e);
    }

    // 订单插入成功，即使通知失败
}
```

而我们的场景是反过来的：**主流程失败，但失败记录要保留**，所以只能用 `REQUIRES_NEW`。

---

## 扩展：向量库的分布式事务问题

回到本文的案例，还有一个隐藏的问题：向量库（DashVector）和 MySQL 不在同一个事务边界，这就是典型的**分布式事务**问题。

### 问题场景

```
场景 1：向量写入成功，MySQL 回滚
  ├─ INSERT video → 在 MySQL 事务缓冲区
  ├─ INSERT chunks → 在 MySQL 事务缓冲区
  ├─ dashVectorStore.add() → 成功写入向量库（无法回滚）
  └─ 后续操作失败 → MySQL ROLLBACK
  结果：向量库里有孤儿向量，MySQL 里没有对应记录

场景 2：MySQL 提交成功，向量写入失败
  ├─ INSERT video → MySQL 提交
  ├─ INSERT chunks → MySQL 提交
  └─ dashVectorStore.add() → 失败
  结果：MySQL 里有记录，但向量库里没有向量，检索功能失效
```

### 我们的选择：容忍不一致

在 v1 版本中，我们选择**容忍场景 1 的不一致**（孤儿向量），理由是：

- **影响范围可控**：向量库按 `userId` 隔离，孤儿向量不会污染其他用户的检索结果
- **业务影响小**：孤儿向量只会导致检索时多返回几个无效片段，前端可以过滤掉
- **成本收益比**：相比引入 Saga/Seata 等分布式事务方案，容忍少量不一致的成本更低

对于场景 2，我们通过 `REQUIRES_NEW` 保存失败记录，让用户知道导入失败，可以重试。

### 更彻底的解决方案

如果对一致性要求更高，可以考虑：

**1. 补偿任务**

定时扫描 FAILED 状态的视频，清理对应的孤儿向量：

```java
@Scheduled(cron = "0 0 2 * * ?")  // 每天凌晨 2 点
public void cleanOrphanVectors() {
    List<Video> failedVideos = videoMapper.selectByStatus(FAILED);
    for (Video video : failedVideos) {
        dashVectorStore.deleteByVideoId(video.getId());
    }
}
```

**2. 最终一致性**

先写 MySQL，成功后异步写向量库，失败重试：

```java
@Transactional
public void importVideo(...) {
    videoMapper.insert(video);
    chunkMapper.batchInsert(chunks);

    // 异步写向量库
    asyncVectorWriter.write(video.getId(), documents);
}

@Async
public void write(Long videoId, List<Document> documents) {
    try {
        dashVectorStore.add(documents);
    } catch (Exception e) {
        // 写入重试队列
        retryQueue.add(videoId, documents);
    }
}
```

**3. 分布式事务框架**

使用 Seata、Saga 等框架，但工程复杂度高，v1 不推荐。

完整的解决方案包括两阶段提交（2PC）、TCC、Saga、本地消息表等，但这些方案工程复杂度高，超出本文讨论范围。感兴趣的读者可以搜索"分布式事务"深入了解。

---

## 单元测试：暴露 Bug

为了验证这个 bug，我写了一个单元测试：

```java
@Test
void importVideoFailure_failedStatusUpdateIsCalledButWouldBeRolledBackByTransaction() {
    // 模拟向量写入失败
    doThrow(new RuntimeException("DashVector 写入失败")).when(dashVectorStore).add(any());

    // 方法应抛出 BusinessException（catch 块重新抛出）
    assertThrows(BusinessException.class, () -> videoService.importVideo(request, 1L));

    // 验证修复后的行为：catch 块通过 VideoStatusWriter.markFailed() 写入失败状态
    // markFailed() 运行在 REQUIRES_NEW 独立事务中，不受外层事务回滚影响
    ArgumentCaptor<Video> videoCaptor = ArgumentCaptor.forClass(Video.class);
    verify(videoStatusWriter, times(1)).markFailed(
            videoCaptor.capture(),
            eq("DashVector 写入失败")
    );
    assertEquals(100L, videoCaptor.getValue().getId());
}
```

这个测试验证了：
1. `dashVectorStore.add()` 抛异常后，catch 块确实调用了 `videoStatusWriter.markFailed()`
2. 传入的 video 对象和失败原因都正确
3. 在真实 Spring 事务环境下，`markFailed()` 的独立事务会提交，不受外层回滚影响

---

## 总结

### 经典的"事务失效"问题

以上五种是事务**失效**问题——事务本该工作，却没工作。根源是：
- 代码写错了（异常被吞、同类内调用、非 public 方法）
- 配置错了（rollbackFor 不匹配）

### 更隐蔽的"事务边界"问题

但还有一种更隐蔽的情况：**事务完全正常工作，却依然产生了 bug**。这是事务**原子性**与**部分持久化**之间的内在冲突：

> 同一次失败，一部分数据要撤销，另一部分数据要保留。

单个事务天生无法解决这个矛盾，需要用 `REQUIRES_NEW` 引入独立事务来打破这个约束。

### 一句话点题

> 事务失效的经典原因是"异常被吞"。但有一种更隐蔽的情况：异常必须往外抛，失败状态必须往库里写——这两个需求都正确，却天然冲突。单个事务无法同时满足"部分回滚、部分提交"，`REQUIRES_NEW` 正是为此而生。

---

## 参考资料

- [Spring 官方文档 - Transaction Propagation](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html)
- [项目源码 - RAG-Bilibili](https://github.com/yourusername/rag-bilibili)
- [相关 Commit - fix(video): 用 REQUIRES_NEW 修复导入失败状态丢失问题](https://github.com/yourusername/rag-bilibili/commit/xxx)
