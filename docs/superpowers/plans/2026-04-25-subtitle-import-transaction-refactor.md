# Subtitle Import Transaction Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Shrink `importVideo` transaction scope so slow subtitle I/O, probe, and retry logic run outside database transactions while preserving status updates and failure compensation.

**Architecture:** Keep `VideoServiceImpl` as the orchestration layer, add a dedicated transactional persistence service for short database transactions, and introduce explicit compensation for vector writes that succeed before database finalize fails. Reuse `VideoStatusWriter` for independent failed-state writes.

**Tech Stack:** Spring Boot, Spring Transaction, MyBatis, JUnit 5, Mockito

---

### Task 1: Add transactional persistence service

**Files:**
- Create: `rag-bilibili-server/src/main/java/com/example/ragbilibili/service/impl/VideoImportTxService.java`
- Modify: `rag-bilibili-server/src/main/java/com/example/ragbilibili/service/impl/VideoServiceImpl.java`
- Test: `rag-bilibili-server/src/test/java/com/example/ragbilibili/service/impl/VideoServiceImplTest.java`

- [ ] Add `VideoImportTxService` with short transactional methods for creating IMPORTING video records and finalizing successful imports.
- [ ] Move database-only persistence logic out of `VideoServiceImpl#importVideo` into the new service.
- [ ] Update tests to mock the new service and verify orchestration behavior.

### Task 2: Introduce prepared import data model

**Files:**
- Create: `rag-bilibili-server/src/main/java/com/example/ragbilibili/service/impl/PreparedVideoImportData.java`
- Modify: `rag-bilibili-server/src/main/java/com/example/ragbilibili/service/impl/VideoServiceImpl.java`
- Test: `rag-bilibili-server/src/test/java/com/example/ragbilibili/service/impl/VideoServiceImplTest.java`

- [ ] Add a small data carrier for transaction-external preparation results.
- [ ] Refactor `importVideo` so subtitle loading / cleaning / splitting / vector-id generation happen before any transactional persistence call.
- [ ] Update tests to assert prepared data is persisted through the new service path.

### Task 3: Add explicit compensation path

**Files:**
- Modify: `rag-bilibili-server/src/main/java/com/example/ragbilibili/service/impl/VideoServiceImpl.java`
- Test: `rag-bilibili-server/src/test/java/com/example/ragbilibili/service/impl/VideoServiceImplTest.java`

- [ ] Track whether DashVector write has succeeded.
- [ ] If final database persistence fails after vector write succeeds, delete written vector IDs and mark the video failed.
- [ ] Cover compensation behavior in tests.

### Task 4: Verify and document

**Files:**
- Modify: `docs/subtitle-import-transaction-refactor-design.md` (only if implementation details need aligning)
- Test: `rag-bilibili-server/src/test/java/com/example/ragbilibili/service/impl/VideoServiceImplTest.java`

- [ ] Run focused tests for `VideoServiceImplTest`.
- [ ] Keep design doc aligned if any implementation naming differs from the design.