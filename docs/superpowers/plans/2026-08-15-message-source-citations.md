# Message Source Citations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist the subtitle evidence cited by each assistant answer and let users jump to the matching Bilibili timestamp from inline references or source cards.

**Architecture:** Resolve retrieved DashVector IDs back to authoritative chunk rows, label candidates in the prompt, parse only valid `[n]` references from the completed answer, and transactionally snapshot those sources beside the assistant message. Extend historical message and SSE end payloads with the same source DTO; the frontend renders trusted server-built links with a 2.5-second pre-roll.

**Tech Stack:** Java 17, Spring Boot, Spring AI, MyBatis, MySQL/Flyway, SSE, Vue 3, marked, DOMPurify

---

## Task 1: Define the REST and SSE contract

**Files:**
- Modify: `docs/API.md`

- [ ] **Step 1: Extend MessageResponse**

Document `sources` as an always-present array. Each item contains `index`, `bvid`, `videoTitle`, `pageNumber`, `startTimeMs`, `endTimeMs`, `snippet`, and `jumpUrl`. User messages and legacy assistant messages return an empty array.

- [ ] **Step 2: Extend the end event**

Document that `end` retains its existing order and fields and adds the same `sources` array. State that this is backward-compatible additive JSON.

- [ ] **Step 3: Check the contract diff**

Run: `git diff --check -- docs/API.md`

Expected: exit code 0.

## Task 2: Add source snapshot persistence

**Files:**
- Create: `rag-bilibili-server/src/main/resources/db/migration/V3__add_message_source.sql`
- Create: `rag-bilibili-server/src/main/java/com/example/ragbilibili/entity/MessageSource.java`
- Create: `rag-bilibili-server/src/main/java/com/example/ragbilibili/mapper/MessageSourceMapper.java`
- Create: `rag-bilibili-server/src/main/resources/mapper/MessageSourceMapper.xml`
- Create: `rag-bilibili-server/src/test/java/com/example/ragbilibili/mapper/MessageSourceMapperContractTest.java`

- [ ] **Step 1: Add the schema contract test**

Read the migration as UTF-8 and assert it defines `message_source`, unique key `(message_id, citation_index)`, and index `message_id`. The test guards the required schema without requiring a live MySQL service.

- [ ] **Step 2: Run the contract test and observe failure**

Run: `mvn -Dtest=MessageSourceMapperContractTest test`

Expected: FAIL because the migration does not exist.

- [ ] **Step 3: Add V3 migration**

Create snapshot columns: `id`, `message_id`, `citation_index`, `vector_id`, `bvid`, `video_title`, nullable `cid`, `page_number`, `start_time_ms`, `end_time_ms`, `snippet`, and `create_time`. Do not add a `video_id` foreign key.

- [ ] **Step 4: Add entity and mapper operations**

Expose:

```java
int batchInsert(@Param("sources") List<MessageSource> sources);
List<MessageSource> selectByMessageIds(@Param("messageIds") List<Long> messageIds);
int deleteBySessionId(@Param("sessionId") Long sessionId);
int deleteBySessionIds(@Param("sessionIds") List<Long> sessionIds);
int deleteByVideoIdSessions(@Param("videoId") Long videoId);
```

Select in `(message_id, citation_index)` order. Delete through joins to `message`/`session`, so source snapshots for ALL_VIDEOS messages are not removed merely because their referenced video is deleted.

- [ ] **Step 5: Run the contract test**

Run: `mvn -Dtest=MessageSourceMapperContractTest test`

Expected: PASS.

## Task 3: Resolve retrieved chunks and build safe citations

**Files:**
- Create: `rag-bilibili-server/src/main/java/com/example/ragbilibili/service/RetrievedSourceCandidate.java`
- Create: `rag-bilibili-server/src/main/java/com/example/ragbilibili/service/CitationService.java`
- Create: `rag-bilibili-server/src/main/java/com/example/ragbilibili/util/BilibiliJumpUrlBuilder.java`
- Modify: `rag-bilibili-server/src/main/java/com/example/ragbilibili/mapper/VectorMappingMapper.java`
- Modify: `rag-bilibili-server/src/main/resources/mapper/VectorMappingMapper.xml`
- Create: `rag-bilibili-server/src/test/java/com/example/ragbilibili/service/CitationServiceTest.java`
- Create: `rag-bilibili-server/src/test/java/com/example/ragbilibili/util/BilibiliJumpUrlBuilderTest.java`

- [ ] **Step 1: Write URL builder tests**

Cover `130000ms -> t=127.5`, `1000ms -> t=0`, and `pageNumber=2 -> p=2`. Require HTTPS Bilibili video URLs.

- [ ] **Step 2: Write citation tests**

Given candidates 1..3, verify answer `证据[2]，补充[2]，另见[1]` produces sources 1 and 2 once each using citation indices from the answer. Verify `[0]`, `[4]`, `[-1]`, no markers, and candidates belonging to another user are ignored or absent before prompting.

- [ ] **Step 3: Run focused tests and observe failure**

Run: `mvn -Dtest=CitationServiceTest,BilibiliJumpUrlBuilderTest test`

Expected: FAIL because the classes do not exist.

- [ ] **Step 4: Add a batch vector-to-chunk projection query**

Join `vector_mapping` with `chunk`, restricted by `vm.user_id`, and return vector ID, BVID, title, CID, page number, time range and text for the provided vector IDs. Restore DashVector retrieval order in Java rather than relying on SQL `IN` ordering.

- [ ] **Step 5: Implement CitationService**

Provide three explicit operations:

```java
List<RetrievedSourceCandidate> resolveCandidates(List<Document> documents, Long userId);
String buildContext(List<RetrievedSourceCandidate> candidates);
List<MessageSource> extractCitedSources(String answer, List<RetrievedSourceCandidate> candidates);
```

The context labels each candidate as `[来源 n]` and includes structured video/P/time metadata before the untrusted subtitle text. The parser accepts only ASCII `[n]`, keeps candidate order, removes duplicates, and never parses URLs from model output.

- [ ] **Step 6: Implement server-side jump URLs**

Use `max(0, startTimeMs - 2500)`, express seconds without unnecessary trailing zeroes, clamp page number to at least 1, and percent-free validate BVID against the existing BV parser expectations.

- [ ] **Step 7: Run focused tests**

Run: `mvn -Dtest=CitationServiceTest,BilibiliJumpUrlBuilderTest test`

Expected: PASS.

## Task 4: Transactionally write assistant messages and sources

**Files:**
- Create: `rag-bilibili-server/src/main/java/com/example/ragbilibili/service/impl/AssistantMessageTxService.java`
- Create: `rag-bilibili-server/src/test/java/com/example/ragbilibili/service/impl/AssistantMessageTxServiceTest.java`
- Modify: `rag-bilibili-server/src/main/java/com/example/ragbilibili/service/impl/ChatServiceImpl.java`

- [ ] **Step 1: Test atomic write orchestration**

Capture the inserted assistant message, make the mocked mapper assign ID 42, and verify every source receives `messageId=42` before one `batchInsert`. Empty sources must skip the batch call.

- [ ] **Step 2: Run the focused test and observe failure**

Run: `mvn -Dtest=AssistantMessageTxServiceTest test`

Expected: FAIL because the service does not exist.

- [ ] **Step 3: Implement the transactional service**

Annotate the public write method with `@Transactional`. It inserts the assistant message and source snapshots and returns the inserted message.

- [ ] **Step 4: Integrate citations into ChatServiceImpl**

Resolve candidates immediately after retrieval, build the labelled context, and update the system prompt to require `[n]` citations only for claims supported by those candidates. On stream completion, extract valid citations, call `AssistantMessageTxService`, and send the persisted source responses in the end event.

- [ ] **Step 5: Run chat-related tests**

Run: `mvn -Dtest=AssistantMessageTxServiceTest,ChatServiceImplTest,SseEventSerializationTest test`

Expected: PASS.

## Task 5: Return historical sources and preserve deletion consistency

**Files:**
- Create: `rag-bilibili-server/src/main/java/com/example/ragbilibili/dto/response/MessageSourceResponse.java`
- Modify: `rag-bilibili-server/src/main/java/com/example/ragbilibili/dto/response/MessageResponse.java`
- Modify: `rag-bilibili-server/src/main/java/com/example/ragbilibili/dto/sse/SseEndEvent.java`
- Modify: `rag-bilibili-server/src/main/java/com/example/ragbilibili/service/impl/MessageServiceImpl.java`
- Modify: `rag-bilibili-server/src/main/java/com/example/ragbilibili/service/impl/SessionServiceImpl.java`
- Modify: `rag-bilibili-server/src/main/java/com/example/ragbilibili/service/impl/VideoDeleteTxService.java`
- Create: `rag-bilibili-server/src/test/java/com/example/ragbilibili/service/impl/MessageServiceImplTest.java`
- Modify: `rag-bilibili-server/src/test/java/com/example/ragbilibili/service/impl/SessionServiceImplTest.java`
- Modify: `rag-bilibili-server/src/test/java/com/example/ragbilibili/service/impl/VideoServiceImplTest.java`
- Modify: `rag-bilibili-server/src/test/java/com/example/ragbilibili/config/SseEventSerializationTest.java`

- [ ] **Step 1: Test historical grouping**

Mock two messages and sources belonging only to the assistant message. Assert user message sources are `[]`, assistant sources are ordered, and mapper lookup is one batch call. Preserve the existing ownership check before any source query.

- [ ] **Step 2: Test deletion order**

Assert session deletion calls source deletion before message deletion. Assert single-video session cleanup does the same, without deleting source snapshots attached to surviving ALL_VIDEOS messages.

- [ ] **Step 3: Test SSE JSON**

Serialize `SseEndEvent` and assert `sources[0].jumpUrl` and all timestamp fields are present while existing fields remain unchanged.

- [ ] **Step 4: Implement DTO conversion and batched history loading**

`MessageResponse.sources` defaults to an empty list. Build a `messageId -> ordered sources` map from one mapper query and generate `jumpUrl` through `BilibiliJumpUrlBuilder`.

- [ ] **Step 5: Wire explicit source cleanup**

Delete source rows before their parent messages in session and single-video-session deletion paths. Do not delete snapshots by referenced BVID/video alone.

- [ ] **Step 6: Run backend focused tests**

Run: `mvn -Dtest=MessageServiceImplTest,SessionServiceImplTest,VideoServiceImplTest,SseEventSerializationTest test`

Expected: PASS.

## Task 6: Render inline citations and source cards

**Files:**
- Modify: `rag-bilibili-front/src/components/MarkdownContent.vue`
- Create: `rag-bilibili-front/src/components/MessageSources.vue`
- Modify: `rag-bilibili-front/src/views/ChatView.vue`
- Modify: `rag-bilibili-front/src/mock/dev-server.js`

- [ ] **Step 1: Add structured sources to message state**

Historical messages consume `sources` directly. Pending assistant messages start with `sources: []`; the SSE end handler assigns `payload.sources || []` before the existing history refresh.

- [ ] **Step 2: Render inline citations safely**

Pass sources to `MarkdownContent`. Before Markdown parsing, replace only `[n]` markers that have a matching structured source with Markdown links whose href is `source.jumpUrl`. Leave invalid markers unchanged. Sanitize output and ensure external links use `target="_blank"` and `rel="noopener noreferrer"`.

- [ ] **Step 3: Add source cards**

Render cards only for assistant messages with sources. Show `[n]`, video title, optional `P{pageNumber}`, formatted start/end range, and a bounded subtitle snippet. The whole card opens `jumpUrl` in a new tab.

- [ ] **Step 4: Update developer mock data**

Add at least one assistant response containing `[1]` and a matching source object so local developer mode exercises both inline and card rendering.

- [ ] **Step 5: Build the frontend**

Run: `npm run build`

Expected: Vite build succeeds.

## Task 7: Full verification and handoff

**Files:**
- Review all files changed on this branch.

- [ ] **Step 1: Run backend full tests from a clean target**

Run: `mvn clean test`

Expected: all tests pass with 0 failures and 0 errors.

- [ ] **Step 2: Rebuild frontend**

Run: `npm run build`

Expected: Vite production build succeeds.

- [ ] **Step 3: Check repository hygiene**

Run: `git diff --check` and `git status --short`.

Expected: no whitespace errors; only scoped feature files plus pre-existing user-owned dirty/untracked files.

- [ ] **Step 4: Manually verify a real link**

Confirm a source with `startTimeMs=130000`, `pageNumber=1` produces a URL ending in `?p=1&t=127.5`, and that Bilibili opens near 02:07.5.
