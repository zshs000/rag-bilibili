# Findings

## Repository Review

- Review session started on 2026-03-28 to inspect the whole `rag-bilibili` repository, not to change product behavior.
- Existing planning files described a previous subtitle-cleaning refactor, so they were replaced for this project-wide inspection.
- The repository is a front-back separated Bilibili subtitle RAG system: Spring Boot backend in `rag-bilibili-server`, Vue 3 frontend in `rag-bilibili-front`, plus extensive Chinese design documents in the repo root.
- Core backend ingest flow lives in `VideoServiceImpl`: parse BV, read subtitles via a custom `BilibiliDocumentReader`, clean subtitle text, split with `TokenTextSplitter`, store chunks in MySQL, then write vectors to DashVector.
- The custom `BilibiliDocumentReader` is not a thin wrapper; it directly calls Bilibili web APIs, computes WBI signatures, reads subtitle JSON, and merges multi-page transcripts into one document.
- Core chat flow lives in `ChatServiceImpl`: validate session, persist the user message, retrieve vector results by `userId` and optionally `bvid`, build a context block, call the LLM with recent history, and stream the answer through `SseEmitter`.
- Frontend auth is JWT-based, stored in `localStorage`; normal REST traffic uses Axios interceptors, while streaming chat uses `fetch` and manually injects `Authorization: Bearer ...`.
- Recent commits show a clear stabilization sequence: migrate from Session/Cookie to JWT+ThreadLocal, fix cross-origin and SSE auth issues, then fix video import failure status persistence with `REQUIRES_NEW`.
- Git history shows `master` as the main line, plus side branches `jwt` and `Memory`; the most active contributor is `zshs000`.
- The repository currently has uncommitted local changes related to subtitle cleaning and planning notes, so the workspace is not clean.
- Documentation is unusually complete for a student/project repo: requirements spec, high-level design, detailed design, vectorization notes, backend summary, and use-case diagrams are all present.
- CI/CD is present but pragmatic: frontend workflow builds Vue assets, backend workflow packages the JAR and uploads it to ECS on push to `master`, and a separate workflow auto-summarizes new GitHub issues with AI.
- Re-verified backend runtime boundary: WebConfig applies the JWT-based LoginInterceptor to all /api/** routes except /api/auth/login and /api/auth/register, with permissive CORS on /api/**.
- Re-verified auth flow: AuthController performs IP-based rate limiting on login/register, returns JWT on login, and treats logout as stateless token discard.
- Re-verified config defaults in pplication.yml: MySQL on 127.0.0.1:3306/rag_bilibili, chat model deepseek-v3.2, embedding model 	ext-embedding-v4 with 1024 dimensions, DashVector collection ilibili, and registration enabled by default.
- Re-verified controller surface: VideoController, SessionController, and MessageController are thin adapters that consistently derive userId from UserContext and delegate business logic into services.
- Re-verified import pipeline in VideoServiceImpl: parse BV, reject duplicates, pull subtitles through BilibiliDocumentReader, clean subtitles first, create ideo in IMPORTING state, split text into chunks, batch insert chunk, push vectors to DashVector, create ector_mapping, then mark the video SUCCESS.
- Re-verified failure handling for imports: non-business exceptions are normalized into VIDEO_IMPORT_FAILED, while VideoStatusWriter is responsible for preserving a FAILED status + reason even when the outer import transaction rolls back.
- Re-verified delete pipeline in VideoServiceImpl: delete vectors first, then related messages/sessions, vector mappings, chunks, and finally the video record.
- Re-verified chat pipeline in ChatServiceImpl: save user message first, create SseEmitter, retrieve top-5 documents from DashVector, build a context block, include up to 10 prior messages as history, stream LLM output chunk-by-chunk, then persist the full assistant reply at stream completion.
- Re-verified retrieval scope: SINGLE_VIDEO sessions filter vectors by both userId and vid, while ALL_VIDEOS sessions filter only by userId.
- Re-verified session model in SessionServiceImpl: only SINGLE_VIDEO and ALL_VIDEOS are accepted; single-video sessions require the referenced video to belong to the current user.
- Re-verified BilibiliDocumentReader: it directly calls Bilibili info/pagelist/player/nav APIs, computes the WBI signature, uses subtitle cookies from SESSDATA/ili_jct/uvid3, fetches the first available subtitle track per page, and merges all page transcripts into one Spring AI Document.
- Re-verified transaction trap fix in VideoStatusWriter: FAILED status writes use REQUIRES_NEW, so import failures survive the rollback of the outer transaction.
- Re-verified frontend routing/auth guard: routes cover login/register/import/videos/sessions/chat, protected pages require auth, and initial navigation eagerly calls etchCurrentUser() before deciding redirects.
- Re-verified frontend auth state: JWT is stored in localStorage, logout is client-side token removal, and there is also a built-in developer mode path that bypasses backend auth with a local mock profile.
- Re-verified REST transport in src/api/http.js: Axios injects Authorization: Bearer ..., expects backend payloads in { code, message, data }, and redirects to /login on NOT_LOGGED_IN unless developer mode is enabled.
- Re-verified SSE transport in src/api/messages.js: chat streaming intentionally uses etch instead of Axios so the token header is added manually while the response body is parsed incrementally by consumeSseStream.
- Re-verified chat page behavior in ChatView.vue: it preloads session + message history, optimistically inserts the user/assistant placeholders, throttles streamed assistant deltas before repaint, supports aborting an in-flight stream, and refreshes the canonical message list from the server after completion.
- Re-verified import page behavior in ImportView.vue: it requires BV/URL plus three Bilibili credentials, stores credentials only in sessionStorage, and shows import status/result feedback inline.
- Re-verified frontend SSE protocol in src/utils/sse.js: events are split on blank lines, event: + JSON data: are decoded, plain message events are treated as content deltas, and synthetic start/end events are emitted when the server stream lacks them.
- Re-verified session list page in SessionsView.vue: it supports both SINGLE_VIDEO and ALL_VIDEOS session creation, limits single-video choices to videos whose status is SUCCESS, and routes directly into /chat/:sessionId after creation.
- Re-verified video library page in VideosView.vue: it lists imported videos with status/chunk count/import time, exposes detail view + delete + create-single-session actions, and surfaces failed import reasons inline.
- Re-verified current git state: local workspace is dirty with subtitle-cleaning related backend changes, updated lock/config/test files, planning notes, and docs; latest visible commits include the REQUIRES_NEW import-failure fix plus README/test/package adjustments.
- Re-verified CI/CD/workflow setup from .github/workflows: backend workflow packages the Spring Boot jar and uploads it to ECS on push to master, frontend workflow runs 
pm ci + 
pm run build, and summary.yml uses GitHub Models to auto-comment concise summaries on newly opened issues.
- Re-verified auxiliary documentation assets: docs/subtitle-cleaning-v2-design.md plus several use-case diagrams under 功能用例图/ complement the root-level requirements/design documents.

## Subtitle Cleaning Review
- The current subtitle-cleaning path is explicitly heuristic: SubtitleCleaningTransformer only uses regex/keyword rules, 1~3 line sliding windows, and adjacent-duplicate removal.
- Its main strength is cheap deterministic filtering for obvious ad segments, but its main weakness is that it simulates semantics by concatenating nearby lines instead of actually understanding meaning.
- The current test file SubtitleCleaningTransformerTest confirms the intended scope is narrow: remove obvious sponsor/call-to-action phrases, support 2~3 line cross-line ads, and preserve short meaningful technical lines.
- The design doc docs/subtitle-cleaning-v2-design.md already acknowledges V1 weaknesses (low recall, poor generalization, false-positive risk) and proposes replacing the current window-heavy heuristic with a rule-first + LLM second-stage pipeline.
- So this code reads like a transition prototype / local experiment rather than a finished architecture: useful enough to reduce obvious noise, but not robust enough to be the final submitted solution.

## DashVector Compatibility Review
- New analysis task: inspect why the local DashVector Spring AI adaptation feels awkward or non-idiomatic relative to Spring AI abstractions, using the code in this repository as primary evidence.
- DashVector adaptation mismatch evidence is concrete in code: the store must manually map Spring AI Document.text into a synthetic content metadata field, manually convert loat[] to List<Float>, manually convert Spring AI filter AST into DashVector string filters, manually convert raw DashVector scores/distances into Spring AI similarity semantics, and explicitly reject filter-based deletion.
- The newer starter in D:\MyProjects\spring-ai-extensions\vector-stores\spring-ai-alibaba-starter-dashvector-store shows the same core mismatch even after being made more Spring-AI-like via builder/observation/batching/auto-configuration; it improves packaging, but cannot remove the underlying impedance mismatch.
- The biggest abstraction friction points are: lifecycle/schema initialization is not truly supported (initializeSchema only logs), delete-by-filter is unsupported, score semantics are metric-dependent and need adapter-side reinterpretation, and filter semantics require a custom AST-to-string compiler with incomplete operator fidelity.
- Added 项目初心.md in the repository root to capture the project's origin story, component motivation, DashVector adaptation reflections, and engineering-growth context.
