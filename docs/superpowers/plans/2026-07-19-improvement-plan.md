# Workflow Engine — Improvement Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Address identified gaps and improvement areas in the workflow engine codebase, covering test coverage, security, frontend completeness, and developer experience.

**Date:** 2026-07-19
**Branch:** 5.1.0
**Status:** In Progress

---

## Progress Summary

| Task | Priority | Status | Commit |
|------|----------|--------|--------|
| Task 1: JDBC Layer Test Coverage | 🔴 High | ✅ Done | `455956a` |
| Task 2: candidateGroups SQL Fix | 🔴 High | ✅ Done | `455956a` |
| Task 3: X-User-Id Header Enforcement | 🔴 High | ✅ Done | (prior commit) |
| Task 4: Dashboard Stats Panel | 🟡 Medium | ✅ Done | `8ee05c0` et al. |
| Task 5: Timer Node Designer Support | 🟡 Medium | ⬜ Pending | — |
| Task 6: InclusiveGateway Integration Test | 🟡 Medium | ✅ Done | `455956a` |
| Task 7: Frontend Error Handling & Loading | 🟡 Medium | ⬜ Pending | — |
| Task 8: Draft API Frontend Integration | 🟡 Medium | ✅ Done | `d8104ca` |
| Task 9: README.md | 🟢 Low | ✅ Done | `9f5521d` et al. |
| Task 10: i18n Completeness | 🟢 Low | ✅ Done | `8c9f00a` et al. |
| Task 11: Swagger/OpenAPI Integration | 🟢 Low | ✅ Done | `4c953ea` |
| Task 12: Frontend E2E Tests | 🟢 Low | ⬜ Pending | — |

**Completed: 8 / 12** | **Remaining: 4**

---

## 🔴 High Priority

### Task 1: JDBC Layer Test Coverage ✅

**Problem:** `JdbcProcessRepository`, `JdbcInstanceRepository`, `JdbcTaskRepository` have zero tests. All 81 existing tests use InMemory repositories. JDBC is the production default profile — any SQL mapping bug, schema drift, or serialization issue goes undetected until runtime.

**Scope:**
- Create `JdbcProcessRepositoryTest` — CRUD for definitions, versioned lookups (`findById`, `findLatestById`, `findByIdAndVersion`, `findAllVersions`)
- Create `JdbcInstanceRepositoryTest` — instance lifecycle, execution CRUD, history recording, parent linkage (CallActivity)
- Create `JdbcTaskRepositoryTest` — task CRUD, query filtering (assignee, candidateGroups, status, instanceId)
- Use H2 in-memory database for test isolation (already a project dependency)
- Each test class should use `@BeforeEach` to initialize schema and `@AfterEach` to clean up

**Files:**
- Create: `workflow-engine-memory/src/test/java/com/github/wf/memory/JdbcProcessRepositoryTest.java`
- Create: `workflow-engine-memory/src/test/java/com/github/wf/memory/JdbcInstanceRepositoryTest.java`
- Create: `workflow-engine-memory/src/test/java/com/github/wf/memory/JdbcTaskRepositoryTest.java`

- [x] **Step 1:** Create test infrastructure — shared H2 datasource setup, schema initialization helper
- [x] **Step 2:** Write `JdbcProcessRepositoryTest` — save, findById, findLatestById, findByIdAndVersion, findAllVersions, multi-version scenarios
- [x] **Step 3:** Write `JdbcInstanceRepositoryTest` — save/find/update instance, execution lifecycle, findActiveExecutions, findExecutionsByParentId, historic activity, parentInstanceId/parentExecutionId persistence
- [x] **Step 4:** Write `JdbcTaskRepositoryTest` — save/find/update task, query by assignee, candidateGroups, status, instanceId, DELEGATED exclusion
- [x] **Step 5:** Run all JDBC tests and verify PASS
- [x] **Step 6:** Commit

---

### Task 2: candidateGroups SQL Injection / LIKE Pattern Bug ✅

**Problem:** `JdbcTaskRepository` uses `LIKE '%groupName%'` for candidateGroups matching. This can match incorrectly (e.g., searching for group `"hr"` matches `"hr-dept"`, `"shr"`, `"hr_team"`). It may also be vulnerable to SQL injection if group names contain wildcards (`%`, `_`).

**Fix Applied:** Replaced LIKE pattern with `JSON_CONTAINS` for candidateGroups filtering — minimal schema change, correct semantics, MySQL-native.

**Files:**
- Modify: `workflow-engine-memory/src/main/java/com/github/wf/memory/JdbcTaskRepository.java`
- Modify: `workflow-engine-memory/src/test/java/com/github/wf/memory/JdbcTaskRepositoryTest.java`

- [x] **Step 1:** Audit current LIKE query in `JdbcTaskRepository.query()`
- [x] **Step 2:** Replace LIKE pattern with `JSON_CONTAINS` for candidateGroups filtering
- [x] **Step 3:** Add test cases for edge cases: group name is substring of another, special characters
- [x] **Step 4:** Verify InMemory and JDBC query results match for identical datasets
- [x] **Step 5:** Commit

---

### Task 3: X-User-Id Header Enforcement ✅

**Problem:** The REST API documents `X-User-Id` as required on all endpoints, but missing header doesn't block access — it's silently treated as null or empty. Any unauthenticated request can access all tenant data.

**Fix Applied:** Added `UserIdInterceptor` that rejects requests missing `X-User-Id` header with HTTP 401 on all `/api/**` paths. Non-API paths (Swagger UI, static resources) are excluded.

**Files:**
- Created: `workflow-engine-server/src/main/java/com/github/wf/server/config/UserIdInterceptor.java`
- Modified: `workflow-engine-server/src/main/java/com/github/wf/server/config/WebConfig.java`

- [x] **Step 1:** Create `UserIdInterceptor` — check `X-User-Id` header, return 401 if missing/blank
- [x] **Step 2:** Register interceptor in Spring `WebMvcConfigurer`
- [x] **Step 3:** Test with `curl` — verify 401 without header, 200 with header
- [x] **Step 4:** Update CHANGELOG.md
- [x] **Step 5:** Commit

---

## 🟡 Medium Priority

### Task 4: Dashboard Stats Panel Implementation ✅

**Problem:** The Dashboard tab exists in the frontend (`Dashboard.tsx`) but shows placeholder/empty stats. No backend endpoint provides aggregated statistics.

**Fix Applied:** Added `GET /api/dashboard/stats` and `GET /api/dashboard/timeline/{instanceId}` endpoints. Dashboard redesigned with per-definition stats, pending task count, and instance timeline view. Frontend fully wired.

**Files:**
- Created: `workflow-engine-server/src/main/java/com/github/wf/server/controller/DashboardController.java`
- Modified: `workflow-engine-web/src/monitor/Dashboard.tsx`

- [x] **Step 1:** Create `DashboardController` with stats and timeline endpoints
- [x] **Step 2:** Add stats query methods to repositories (SQL aggregation for JDBC, streams for memory)
- [x] **Step 3:** Wire `Dashboard.tsx` to fetch and display stats
- [x] **Step 4:** Test manually — deploy a process, start instances, verify dashboard updates
- [x] **Step 5:** Commit

---

### Task 5: Timer Node — Designer Support ⬜

**Problem:** Timer node exists in the engine (`TimerRunner`, `NodeType.TIMER`) but is missing from the designer's `NodePalette` and `PropertyPanel`. Users cannot visually add timer nodes or configure their delay duration.

**Scope:**
- Add Timer to `NodePalette` with appropriate icon
- Add Timer property editor in `PropertyPanel` (delay duration input)
- Verify `graphToYaml` / `yamlToGraph` handle timer nodes correctly

**Files:**
- Modify: `workflow-engine-web/src/designer/NodePalette.tsx` (or wherever node types are listed for drag-and-drop)
- Modify: `workflow-engine-web/src/designer/PropertyPanel.tsx`
- Verify: `workflow-engine-web/src/designer/graphToYaml.ts`, `yamlToGraph.ts`

- [ ] **Step 1:** Add Timer entry to NodePalette
- [ ] **Step 2:** Add Timer property editor in PropertyPanel (delay field)
- [ ] **Step 3:** Verify graphToYaml serializes timer delay correctly
- [ ] **Step 4:** Verify yamlToGraph deserializes timer node correctly
- [ ] **Step 5:** Build frontend and test manually
- [ ] **Step 6:** Commit

---

### Task 6: InclusiveGateway Integration Test ✅

Integration Test ✅

**Problem:** InclusiveGateway has no integration test. The runner exists and is registered, but no end-to-end scenario validates that it correctly evaluates conditions and forks matching branches.

**Fix Applied:** Created `InclusiveGatewayIntegrationTest` with scenarios for multiple matching branches and zero-match fallback.

**Files:**
- Created: `workflow-engine-core/src/test/java/com/github/wf/engine/InclusiveGatewayIntegrationTest.java`

- [x] **Step 1:** Write test YAML with InclusiveGateway + multiple conditional branches
- [x] **Step 2:** Write test — multiple branches match, verify parallel execution
- [x] **Step 3:** Write test — zero branches match, verify fallback behavior
- [x] **Step 4:** Run test and verify PASS
- [x] **Step 5:** Commit

---

### Task 7: Frontend Error Handling and Loading States ⬜

**Problem:** Frontend API calls use `fetch()` without error handling — network failures, 401s, 500s are silently ignored. No loading indicators; UI appears frozen during requests.

**Scope:**
- Create a shared `apiFetch()` wrapper that:
  - Adds `X-User-Id` header automatically
  - Throws on non-2xx responses with user-friendly error messages
  - Supports loading state management
- Add loading spinners/skeletons to key components (DesignerPage, MonitorPage, Dashboard)
- Add toast/notification for API errors

**Files:**
- Modify: `workflow-engine-web/src/api/client.ts` (shared fetch wrapper)
- Modify: All components that call `fetch()` directly (DesignerPage, MonitorPage, Dashboard, PropertyPanel)

- [ ] **Step 1:** Create/update `api/client.ts` with `apiFetch()` wrapper
- [ ] **Step 2:** Add error toast component (or use a lightweight library)
- [ ] **Step 3:** Migrate DesignerPage API calls to `apiFetch()`
- [ ] **Step 4:** Migrate MonitorPage API calls to `apiFetch()`
- [ ] **Step 5:** Migrate Dashboard API calls to `apiFetch()`
- [ ] **Step 6:** Add loading states to key components
- [ ] **Step 7:** Build and test manually
- [ ] **Step 8:** Commit

---

### Task 8: Draft API Frontend Integration Audit ✅

**Problem:** The backend has a `DraftRepository` and draft-related REST endpoints, but it's unclear whether the frontend designer fully integrates with the draft API.

**Fix Applied:** Draft persistence fully integrated via backend API. localStorage fallback removed. Draft CRUD operations (create, list, load, save, delete, copy, import) all working.

**Files:**
- Modified: `workflow-engine-web/src/designer/DesignerPage.tsx`
- Modified: `workflow-engine-web/src/api/client.ts`

- [x] **Step 1:** Audit current draft API usage in frontend
- [x] **Step 2:** List missing features (auto-save, conflict handling, etc.)
- [x] **Step 3:** Implement missing draft integration
- [x] **Step 4:** Test draft lifecycle manually
- [x] **Step 5:** Commit

---

## 🟢 Low Priority

### Task 9: README.md ✅

**Problem:** No `README.md` exists at the project root. New developers (or the user returning after a break) have no quick-start guide.

**Fix Applied:** Comprehensive README created with project description, architecture overview, build/test commands, quick-start (memory mode), module descriptions, configuration options, and extension points.

**Files:**
- Created: `README.md`

- [x] **Step 1:** Write README.md
- [x] **Step 2:** Commit

---

### Task 10: i18n Completeness ✅

**Problem:** i18n system exists (Chinese/English) but many UI strings are hardcoded in Chinese. Switching to English leaves parts of the UI in Chinese.

**Fix Applied:** All hardcoded strings moved to i18n translation files. Both Chinese and English render correctly. Language switcher implemented.

**Files:**
- Modified: `workflow-engine-web/src/i18n/` (translation files)
- Modified: Multiple component files (replaced hardcoded strings with i18n calls)

- [x] **Step 1:** Audit components for hardcoded Chinese strings
- [x] **Step 2:** Add missing keys to both zh/en translation files
- [x] **Step 3:** Replace hardcoded strings with `t('key')` calls
- [x] **Step 4:** Test both languages
- [x] **Step 5:** Commit

---

### Task 11: Swagger/OpenAPI Integration ✅

**Problem:** No API documentation beyond the CHANGELOG.md table. Developers must read source code to understand request/response formats.

**Fix Applied:** Added `springdoc-openapi` dependency, created `OpenApiConfig`, and annotated all 6 controller classes (32 endpoints total) with `@Tag`, `@Operation`, `@ApiResponse`, and `@Parameter`. Swagger UI accessible at `/swagger-ui.html`.

**Files:**
- Modified: `workflow-engine-server/pom.xml` (springdoc dependency)
- Created: `workflow-engine-server/src/main/java/com/github/wf/server/config/OpenApiConfig.java`
- Modified: All 6 controller files with annotations

- [x] **Step 1:** Add springdoc-openapi dependency
- [x] **Step 2:** Create OpenApiConfig with metadata
- [x] **Step 3:** Annotate all controller endpoints (DraftController, InstanceController, TaskController, OrgController, DashboardController, DefinitionController)
- [x] **Step 4:** Verify Swagger UI at `/swagger-ui.html`
- [x] **Step 5:** Commit

---

### Task 12: Frontend E2E Tests ⬜

**Problem:** No automated frontend tests. Any frontend refactoring risks regressions that are only caught by manual testing.

**Scope:**
- Add Playwright (or Cypress) for E2E testing
- Write smoke tests for core flows: deploy workflow, start instance, complete task, view monitor
- Run against memory-mode backend for test isolation

**Files:**
- Modify: `workflow-engine-web/package.json` (add Playwright)
- Create: `workflow-engine-web/e2e/` directory with test files
- Create: `workflow-engine-web/playwright.config.ts`

- [ ] **Step 1:** Install and configure Playwright
- [ ] **Step 2:** Write E2E test — deploy workflow via designer
- [ ] **Step 3:** Write E2E test — start instance and complete task via monitor
- [ ] **Step 4:** Write E2E test — verify dashboard stats update
- [ ] **Step 5:** Add E2E test script to package.json
- [ ] **Step 6:** Commit

---

## Remaining Work (4 tasks)

| # | Task | Priority | Effort | Notes |
|---|------|----------|--------|-------|
| 5 | Timer Node Designer Support | 🟡 Medium | Small | Add Timer to palette + property panel |
| 7 | Frontend Error Handling & Loading | 🟡 Medium | Medium | Shared apiFetch wrapper + loading states |
| 12 | Frontend E2E Tests | 🟢 Low | Large | Playwright setup + smoke tests |
| — | (Task 5 & 7 could be done in parallel) | | | |

### Suggested Next Steps

1. **Task 5** (Timer designer) — small scope, quick win, completes engine↔designer parity
2. **Task 7** (Frontend error handling) — medium scope, improves UX across all pages
3. **Task 12** (E2E tests) — large scope, best done after frontend is fully stable

---

## Known Limitations (not in scope)

These issues were identified but are out of scope for this improvement plan:

- **Redis lock reentrancy** — CallActivity sync mode in Redis profile can deadlock on trivial sub-processes. Only affects Redis mode with startEvent→endEvent children. Will be addressed when async CallActivity is implemented.
- **EndEventRunner nesting depth** — 6 levels of nesting in sub-process completion logic. Refactor to early-return pattern in a future cleanup pass.
- **Responsive mobile layout** — Explicitly excluded from MVP scope.
