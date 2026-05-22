# Satoken Auth Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the backend's custom JWT authentication with Satoken 1.44.0 while keeping the frontend contract unchanged.

**Architecture:** Keep username/password validation in `UserService`. Introduce a small backend auth-session wrapper around Satoken, use Satoken for login/current-user/logout, and configure the MVC auth boundary to protect `/api/**` except login/register. Keep `Authorization: Bearer <token>`, `data.token`, and `NOT_LOGGED_IN(1004)` compatible with the current frontend.

**Tech Stack:** Spring Boot 3.2.0, Spring MVC, Maven, Satoken `cn.dev33:sa-token-spring-boot3-starter:1.44.0`, JUnit 5, MockMvc, Mockito.

---

### Task 1: Test the New Auth Session Contract

**Files:**
- Modify: `rag-bilibili-server/src/test/java/com/example/ragbilibili/controller/AuthControllerTest.java`
- Modify: `rag-bilibili-server/src/test/java/com/example/ragbilibili/controller/AuthControllerSecurityTest.java`
- Modify: `rag-bilibili-server/src/test/java/com/example/ragbilibili/controller/RegisterDisabledTest.java`

- [ ] Replace controller tests so they mock an `AuthSessionManager` instead of `JwtUtil`.
- [ ] Assert login returns the token produced by `AuthSessionManager.login(userId)`.
- [ ] Assert logout calls the server-side logout path.
- [ ] Run `mvn -q -Dtest=AuthControllerTest,AuthControllerSecurityTest,RegisterDisabledTest test` and verify RED: compilation fails because `AuthSessionManager` does not exist yet.

### Task 2: Implement Satoken Login/Logout/Current User

**Files:**
- Modify: `rag-bilibili-server/pom.xml`
- Create: `rag-bilibili-server/src/main/java/com/example/ragbilibili/auth/AuthSessionManager.java`
- Modify: `rag-bilibili-server/src/main/java/com/example/ragbilibili/controller/AuthController.java`
- Modify: `rag-bilibili-server/src/main/resources/application.yml`
- Modify: `rag-bilibili-server/src/main/resources/application.yml.example`

- [ ] Add `sa-token-spring-boot3-starter` version `1.44.0`.
- [ ] Remove JJWT dependencies if no production code still uses them after the migration.
- [ ] Implement `AuthSessionManager.login`, `logout`, `currentUserId`, and `checkLogin` using `StpUtil`.
- [ ] Change `AuthController` to use `AuthSessionManager` for login token issuance, logout, and current user lookup.
- [ ] Configure Satoken to read `Authorization: Bearer <token>` from headers and use in-memory/default storage.
- [ ] Run the Task 1 tests and verify GREEN.

### Task 3: Replace Request Authentication Boundary

**Files:**
- Modify: `rag-bilibili-server/src/main/java/com/example/ragbilibili/config/WebConfig.java`
- Delete: `rag-bilibili-server/src/main/java/com/example/ragbilibili/interceptor/LoginInterceptor.java`
- Delete: `rag-bilibili-server/src/main/java/com/example/ragbilibili/util/JwtUtil.java`
- Delete: `rag-bilibili-server/src/test/java/com/example/ragbilibili/util/JwtUtilTest.java`
- Modify: controller tests that mock `LoginInterceptor`

- [ ] Configure `/api/**` authentication through Satoken-backed logic.
- [ ] Preserve `OPTIONS` preflight bypass and login/register whitelist.
- [ ] Update `VideoControllerTest`, `SessionControllerTest`, `MessageControllerTest`, and exception tests to mock the new auth session wrapper.
- [ ] Remove obsolete `JwtUtilTest`.
- [ ] Run controller tests and verify they pass.

### Task 4: Map Satoken Not-Logged-In Errors

**Files:**
- Modify: `rag-bilibili-server/src/main/java/com/example/ragbilibili/exception/GlobalExceptionHandler.java`
- Modify: `rag-bilibili-server/src/test/java/com/example/ragbilibili/exception/GlobalExceptionHandlerTest.java`

- [ ] Add a failing test proving Satoken's not-login exception returns business code `1004`.
- [ ] Add `GlobalExceptionHandler` mapping for Satoken not-login errors.
- [ ] Run the exception test and verify it passes.

### Task 5: Documentation and Full Verification

**Files:**
- Modify: `docs/backend/Satoken鉴权重构必要性说明.md`
- Modify as needed: `task_plan.md`, `progress.md`, `findings.md`

- [ ] Update the comparison document to state that the implementation uses Satoken `1.44.0`, not the newest version.
- [ ] Run `mvn -q test` from `rag-bilibili-server`.
- [ ] Report any limitations: no Redis means login state is process-local and users re-login after backend restart.
