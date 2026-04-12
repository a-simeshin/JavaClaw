# Plan: Manual CDP Validation — Multi-Dialect Persistence

## Objective

12 user scenarios x 2 profiles (sqlite, postgresql) = 24 runs through real Chrome via CDP.
Validates that the multi-dialect persistence layer does not break any user-facing functionality.

## Prerequisites

- `OPENROUTER_API_KEY` set in environment (for LLM-dependent scenarios 2, 3)
- Ports 8080 and 8081 free
- Chrome browser running with DevTools accessible
- All automated tests green (`./mvnw clean verify` passed — Task #19)
- `javaclaw-core` installed to local m2: `./mvnw -pl javaclaw-core -am install -DskipTests`

## Execution Order

Run **sqlite first** (new, critical), then **postgresql** (regression baseline).

### Per-Profile Setup

```bash
# 1. Kill any leftover processes
lsof -ti:8080 -ti:8081 | xargs kill -9 2>/dev/null || true

# 2. Clean sqlite file (sqlite profile only)
rm -f /tmp/javaclaw-manual-sqlite.db*

# 3. Start application
# For sqlite:
./mvnw -pl javaclaw-app spring-boot:run \
  -Dspring-boot.run.profiles=sqlite \
  -Dspring-boot.run.arguments="--SQLITE_FILE=/tmp/javaclaw-manual-sqlite.db"

# For postgresql:
./mvnw -pl javaclaw-app spring-boot:run \
  -Dspring-boot.run.profiles=postgres

# 4. Wait for "Started Application" in logs
# 5. Verify: curl http://localhost:8080/actuator/health -> {"status":"UP"}
```

### Per-Profile Teardown

```bash
# Stop spring-boot:run (Ctrl+C or kill PID)
# Wait for port release: lsof -ti:8080 | wc -l == 0
```

## Scenarios

### S1: Login Flow

- **Action**: Open `http://localhost:8080/`, see React SPA login form, login as `admin/admin`
- **Assert**: Redirect to main page, session cookie set
- **CDP**: `navigate` -> `take_snapshot` -> `form_input` username/password -> `click` login -> `take_snapshot`
- **Validates**: Spring Security cookie-session auth, React SPA serves on `/`

### S2: Chat Conversation + LLM Response

- **Action**: Create new conversation, send one message, wait for LLM response
- **Assert**: HTTP 200 with non-empty assistant content; UI shows assistant message; `GET /api/chat/conversations/{id}/messages` returns 2 messages (user + assistant)
- **CDP**: `click` new conversation -> `form_input` message -> `click` send -> `wait_for` assistant response (up to 30s) -> `take_snapshot`
- **Validates**: ConversationEnsurer, JdbcChatMemoryRepository (sqlite: SqliteChatMemoryRepositoryDialect), LLM integration
- **Requires**: `OPENROUTER_API_KEY`. If unavailable, mark SKIP (not PASS/FAIL)

### S3: Chat Memory Persistence

- **Action**: Reload conversation page, verify chat history loaded
- **Assert**: Previously sent messages appear after reload
- **CDP**: `navigate` to same conversation URL -> `wait_for` message content visible -> `take_snapshot`
- **Validates**: JdbcChatMemoryRepository read path, Spring AI chat memory on sqlite/postgres

### S4: Ad-Hoc Task Creation

- **Action**: Create ad-hoc task via UI, verify appearance in list, wait for JobRunr execution
- **Assert**: Task appears in list; after execution, `GET /api/tasks/{id}` -> status `COMPLETED`
- **CDP**: Navigate to tasks -> `click` create -> `form_input` task details -> `click` submit -> `wait_for` status change (up to 60s) -> `take_snapshot`
- **Validates**: Task entity + BeforeConvertCallback UUID generation, JobRunr SqLiteStorageProvider / PostgresStorageProvider

### S5: Recurring Task

- **Action**: Create recurring task (every minute cron), wait 90s, verify >= 1 execution
- **Assert**: `task_executions` count >= 1; recurring task visible in list
- **CDP**: Create recurring task -> wait 90s -> check executions -> delete recurring task -> `take_snapshot`
- **Validates**: RecurringTask + TaskExecution, JobRunr recurring on both storage providers

### S6: Skill CRUD

- **Action**: Open skills page, create new skill with markdown body, verify save
- **Assert**: Skill appears in list with correct title; `GET /api/skills/{id}` returns created skill
- **CDP**: Navigate to skills -> `click` create -> `form_input` name + body -> `click` save -> `take_snapshot`
- **Validates**: Skill entity + SkillIdGeneratorCallback, JSON converters (if skill has metadata)

### S7: Virtual File CRUD

- **Action**: Create file via UI/API, read content, delete
- **Assert**: File created with UUID id, content readable, deletion successful
- **CDP**: Navigate to workspace -> create file -> verify content -> delete -> `take_snapshot`
- **Validates**: VirtualFile + VirtualFileIdGeneratorCallback, UUID round-trip on sqlite TEXT

### S8: MCP Server CRUD

- **Action**: Add test MCP server entry (no real connection), verify save and list display
- **Assert**: Server record saved, appears in list with correct name
- **CDP**: Navigate to MCP settings -> `click` add -> `form_input` name + URL -> `click` save -> `take_snapshot`
- **Validates**: McpServer + McpServerIdGeneratorCallback, Map<String,String> headers JSON converter

### S9: User Management (ADMIN)

- **Action**: Open admin panel, view seed users
- **Assert**: `admin` and `user` visible; on sqlite profile, UUID literals `00000000-...-000000000001`/`...0002` display correctly
- **CDP**: Navigate to admin/users -> `take_snapshot` -> verify user list content
- **Validates**: AppUser entity, V10 seed data, UUID display

### S10: SSE Streaming

- **Action**: During chat scenario (S2), verify SSE connection established
- **Assert**: `list_network_requests` shows SSE connection to `/api/chat/stream/*` with event data
- **CDP**: `read_network_requests` during active chat -> filter for EventSource/text-event-stream -> `take_snapshot` of network panel
- **Validates**: SSE transport works with both storage backends
- **Note**: Can be combined with S2 execution

### S11: JobRunr Dashboard

- **Action**: Open `http://localhost:8081/dashboard`
- **Assert**: Dashboard renders, shows job list without exceptions
- **CDP**: `navigate` to `:8081/dashboard` -> `take_snapshot` -> verify no error banners
- **Validates**: JobRunr dashboard works with SqLiteStorageProvider / PostgresStorageProvider

### S12: Logout/Login Cycle

- **Action**: Logout, verify client state cleared, login again, verify clean conversation list
- **Assert**: After logout — no stale conversations in UI (regression fix-chat-state-leak-on-logout-login); after re-login — own conversations only
- **CDP**: `click` logout -> `take_snapshot` (should show login form) -> login as `user/user` -> `take_snapshot` (should show user's conversations, not admin's)
- **Validates**: Session invalidation, client state wipe, per-user isolation post re-auth

## Result Template

File: `specs/manual-validation-report.md`

```markdown
# Manual CDP Validation Report

Date: YYYY-MM-DD
Profiles tested: sqlite, postgresql

## Coverage Notes

Entities without direct UI surface (validated by automated tests only):
- AppConfig — no settings UI
- RoleAgentConfig / role-based routing — SqliteRepositoryIntegrationTest
- AuditLog — no public audit UI

## Results

| # | Scenario | sqlite | postgresql | Screenshots | Console Errors |
|---|----------|--------|------------|-------------|----------------|
| S1 | Login Flow | PASS/FAIL | PASS/FAIL | link | none/list |
| S2 | Chat + LLM | PASS/FAIL/SKIP | PASS/FAIL/SKIP | link | ... |
| S3 | Chat Memory | ... | ... | ... | ... |
| S4 | Ad-Hoc Task | ... | ... | ... | ... |
| S5 | Recurring Task | ... | ... | ... | ... |
| S6 | Skill CRUD | ... | ... | ... | ... |
| S7 | Virtual File | ... | ... | ... | ... |
| S8 | MCP Server | ... | ... | ... | ... |
| S9 | User Mgmt | ... | ... | ... | ... |
| S10 | SSE Streaming | ... | ... | ... | ... |
| S11 | JobRunr Dashboard | ... | ... | ... | ... |
| S12 | Logout/Login | ... | ... | ... | ... |

## Verdict

PASS: all 24 runs green (or SKIP for LLM-dependent without key)
FAIL: list blocking issues
```

## Task Breakdown

### Task 1: CDP sqlite profile (S1-S12)

- **Stack**: Java Spring Boot jdbc chrome cdp browser mcp e2e
- **Agent Type**: general-purpose
- **Steps**: setup sqlite -> run S1-S12 -> teardown -> screenshots
- **Time estimate**: ~45-60 min (S5 alone needs 90s wait)

### Task 2: CDP postgresql profile (S1-S12)

- **Stack**: Java Spring Boot jdbc chrome cdp browser mcp e2e
- **Agent Type**: general-purpose
- **Steps**: setup postgres -> run S1-S12 -> teardown -> screenshots
- **Time estimate**: ~45-60 min

### Task 3: Compile report

- **Agent Type**: general-purpose
- **Steps**: Merge results from Task 1 + Task 2 into `specs/manual-validation-report.md`

## Acceptance Criteria

- [ ] All 12 scenarios x 2 profiles = 24 runs completed (PASS/FAIL/SKIP)
- [ ] PASS only if all 24 green (SKIP allowed only for S2/S3/S10 without LLM key)
- [ ] Any FAIL -> blocking bug report in separate spec + return to implementation
- [ ] GIF recordings for S2, S5, S12 (complex multi-step scenarios)
- [ ] `specs/manual-validation-report.md` with full table + screenshots

