# Plan: Chat Runtime Bug Fixes + E2E Coverage (21 bugs + 10 new Playwright tests)

## Task Description

Runtime-верификация 10 чат-сценариев через Chrome DevTools MCP на ветке `develop` (commit 8edffad) выявила **21 баг** в chat/RBAC/audit/summarization/fallback pipeline:

- **Security/RBAC (4):** model allowlist обходится для default модели (#19, #20), DELETE endpoint падает на slash в modelId (#29), `chat_audit_log.user_id` всегда NULL (#25).
- **Chat lifecycle (5):** cancel теряет partial content и audit (#11), upstream abort к OpenRouter не подтверждён (#12), нет SRE-логов cancel (#3), recurring task `joke-every-15-seconds` спамит каждые 15с (#13), duplicate USER messages в `chat_audit_log.history` (#24).
- **Summarization (5):** нет chunking → HTTP 400 на >200K токенов (#20_sum), `messagesCovered` не кумулятивный при merge (#17), `delete+save` не атомарны (#18), `conversationId='current'` литерал в recurring (#21), `conversationId='unknown'` литерал в другом path (#22).
- **Fallback chain (3):** `streamWithFallback` игнорирует `maxRetriesPerModel` (#14), `withModel` теряет `ChatOptions` (#15), runtime не проверен (#16).
- **Data/audit (3):** `token_usage`/`tool_calls_detail`/`error_*` не заполняются (#26), `AGENT.md` default содержит literal placeholders `<your full name>`/`{ENVIRONMENT_INFO}` (#27), reasoning events не эмитятся для OpenAI-совместимых провайдеров (#2_thinking — **DONE 2026-04-11** через `specs/openai-compat-reasoning-events.md`: `ReasoningAwareChatModel` decorator в `javaclaw-provider-openai`, автоконфигурация с `@ConditionalOnBean(OpenAiChatModel.class)`, парсинг `reasoning_details[]`/`reasoning`/`reasoning_content`, signature chunk перед первым non-reasoning токеном, 22 новых теста + E2E с WireMock).
- **Feature orphan (1):** Few-shot examples — нет admin API/UI/seed (#23), таблица `tool_examples` всегда пустая.

Параллельно выявлено: **10 user-scenario тестов не покрыты E2E** или мокируют `/api/auth/me` через `bypassAuth()`, что не валидирует реальный backend. Missing scenarios: login flow, conversation sharing (V35), admin user/role management, file upload/download, role model allowlist enforcement, chat cancel lifecycle, auto-summarization runtime, few-shot admin CRUD, AGENT.md sanity, recurring task conversation tracking.

## Objective

1. Починить все 21 бага, приоритизируя security/compliance/data-loss.
2. Ввести **admin REST API** для `tool_examples` (закрытие orphan feature).
3. Написать **10 новых Java Playwright E2E-тестов**, каждый из которых падает до фикса и зеленеет после — через реальный login (Spring Security form), реальный backend, PostgreSQL Testcontainer.
4. Добавить helper `realLogin(page, username, password)` и `JdbcAssertions` в `PlaywrightE2ETestBase`.
5. Обеспечить runtime: `/tmp/javaclaw.log` чистый (без WARN/ERROR от recurring spam), все 10 ручных чек-листов из `docs/user-functionality-verification.md` проходят под admin и user без регрессий.

## Problem Statement

Phase 8-15 закрыла 1187+ unit-тестов зелёными, но runtime-прогон живого чата под admin и user показал, что часть фич **помечены DONE в коммитах, но фактически не работают** или имеют критичные дыры:

- **Phase 13 Task 15.6.4 "Model access per role"** (commit 8edffad) — RoleModelAllowlistService не проверяет default модель, когда у роли нет `role_agent_config`. USER с allowlist=[m2.5] спокойно использует default m2.7 через дефолтную модель.
- **Phase 8.2 Cancel** — UI вызывает `/api/chat/cancel/{id}`, но серверный `doOnComplete`/`doOnError` не срабатывают на cancel → partial assistant content теряется, `chat_audit_log` не пишется, upstream HTTP к OpenRouter не обрывается (потенциальная трата токенов).
- **Phase 13 Task 15.3.4 "Skill usage audit"** — `chat_audit_log` схема V14 содержит колонки `user_id`, `token_usage`, `tool_calls_detail`, `error_*`, но при записи все эти поля NULL. Нет связи кто задал запрос, сколько потратил токенов, какие tools вызвал.
- **Phase 8.3 Auto-summarization** — логика работает для малых диалогов, но при большой истории `callLlmForSummary()` падает HTTP 400 (`context length exceeded`) без chunking. Плюс recurring task `joke-every-15-seconds` спамит каждые 15 секунд с `conversationId='current'` (литерал) и зацикливается на тех же 240K токенов.
- **Phase 8.4 Few-shot examples** — V25, Provider, MessageAssembler, unit-тесты — всё есть и работает, НО нет admin API, seed, CLI, UI. Таблица `tool_examples` пуста на fresh install → end-users не могут использовать фичу.

Дополнительно, существующая E2E-инфраструктура (71 TS Playwright + 56 Java Playwright) мокирует Spring Security через route interception `page.route("**/api/auth/me", ...)`, поэтому ни один тест не прогоняет реальный auth-flow и не ловит compliance/RBAC баги уровня "user_id NULL в audit".

## Solution Approach

Исполнение **7 фаз** линейно (каждая фаза не блокирует следующую, но merge-конфликты минимизируются последовательным порядком):

- **Phase 0 (prep):** kill low-budget backend PID 53462, рестарт с normal config, остановить recurring spam task.
- **Phase 1 (security/RBAC):** fix model allowlist bypass, audit user_id, DELETE modelId endpoint.
- **Phase 2 (chat lifecycle):** `doFinally` для persist/audit на cancel, token_usage/tool_calls persist, убрать history duplicates, SRE log на cancel.
- **Phase 3 (summarization):** hierarchical chunked summary, cumulative counter, atomic upsert, fix literal conversationIds.
- **Phase 4 (fallback):** retry loop внутри stream fallback, ChatOptions copy preserve.
- **Phase 5 (feature completeness):** `ToolExampleController` CRUD admin API, sanitize default `AGENT.md`.
- **Phase 6 (test infra):** `realLogin()` helper + `JdbcAssertions` helper в `PlaywrightE2ETestBase`, 10 новых Java E2E тестов.
- **Phase 7 (validation):** полный regression — unit + E2E + ручной CDP-прогон.

**Ключевое архитектурное решение:** для bug #11/#12/#24/#26 **убрать Reactor из бизнес-кода** `ChatService.stream()` и `SseStreamingService.runStream()`. Reactor в проекте присутствует только потому что Spring AI API сигнатура `chatModel.stream(prompt)` возвращает `Flux<ChatResponse>` — сам стек servlet-based (Tomcat + `RestClient`, не Netty, не WebFlux). Заменить `doOnComplete`/`doOnError`/`takeUntilOther`/`blockLast` на **`Flux.toStream() + try-with-resources + AtomicBoolean cancelled flag`**. Это даёт линейный control flow, try/finally persist+audit без реактивных колбэков, детерминированный upstream cancel через stream close → `Disposable.dispose()` → `RestClient` connection close. `ChatAuditService` получает единый метод `log(String method, ...)` где `method ∈ {stream-completed, stream-cancelled, stream-error}`. Партиальный текст (`assistantContent.toString()`) сохраняется в `chat_memory` в `finally`-блоке если непустой, независимо от статуса. Никакого `SignalType` switch, никакого `doFinally`, никаких shared `AtomicReference<Throwable>` в реактивной цепочке.

**Ключевое архитектурное решение** для bug #19/#20: не править `RoleModelAllowlistService.isModelAllowed(role, null) = true` в лоб (сломает 15+ тестов), а исправить **точку вызова** в `ChatRestController`. Вычислять `effectiveModel = modelOverride ?? defaultChatModelProperties.getModel()` **до** allowlist check. Добавить `@ConfigurationProperties("spring.ai.openai.chat.options")` bean `DefaultChatModelProperties` для чтения model name из application.yaml.

**Ключевое архитектурное решение** для bug #20_sum (chunking): в `ConversationSummaryService.callLlmForSummary` добавить private метод `summarizeChunked(List<Message> msgs, int maxChunkTokens)` — который реализует hierarchical reduce: split → summarize каждый chunk → concat результаты → если ещё >limit → второй pass. Использовать существующий `TokenEstimator` (уже инжектится). Параметр `maxChunkTokens` = 80000 hardcoded (достаточно для 204K context window minimax).

## Relevant Files

Existing backend files to modify:

- `javaclaw-api/javaclaw-api-chat/src/main/java/ai/javaclaw/api/chat/controller/ChatRestController.java` — строки 55-75: insert `effectiveModel` resolution перед allowlist check (#19, #20).
- `javaclaw-api/javaclaw-api-admin/src/main/java/ai/javaclaw/api/admin/users/RoleController.java` — DELETE endpoint для `/allowed-models/{modelId}` (#29) + existing RBAC pattern reference.
- `javaclaw-core/src/main/java/ai/javaclaw/agent/pipeline/ChatService.java` — строки 135-185: убрать реактивные `doOnComplete/doOnError/doOnNext`, просто вернуть `chatModel.stream(prompt)`. Убрать дубль `chatMemory.add(new UserMessage(userContent))` (#24). Persist/audit ответственность уходит в caller (`SseStreamingService`).
- `javaclaw-api/javaclaw-api-chat/src/main/java/ai/javaclaw/api/chat/service/SseStreamingService.java` — **ключевой файл**: `runStream()` переписать с `blockLast()`/`takeUntilOther`/`Sinks.Empty` на **`Flux.toStream() + try-with-resources + AtomicBoolean cancelFlags`**. Упростить `cancel()` до `cancelFlags.get(cid).set(true) + log.info`. Убрать `cancelSignals` map (#11, #12, #24, #26, #3).
- `javaclaw-core/src/main/java/ai/javaclaw/agent/audit/ChatAuditService.java` — новый унифицированный метод `log(cid, method, prompt, text, duration, userId, usage, toolCalls)` + `logError(..., throwable, ...)`. Заполнение `user_id`, `token_usage`, `tool_calls_detail`, `error_*`. Старые overload'ы deprecated (#25, #26).
- `javaclaw-core/src/main/java/ai/javaclaw/agent/pipeline/ConversationSummaryService.java` — hierarchical chunking (#20_sum), cumulative counter (#17), atomic upsert через `@Transactional(REQUIRES_NEW)` (#18).
- `javaclaw-core/src/main/java/ai/javaclaw/agent/pipeline/FallbackChatModel.java` — строки 55-102: retry loop в stream path (#14), `withModel` копирует все опции через `builder.from(originalOptions)` (#15).
- `javaclaw-core/src/main/java/ai/javaclaw/agent/config/RoleModelAllowlistService.java` — оставить null-as-allowed, fix выполнен на уровне caller в ChatRestController.
- `javaclaw-core/src/main/java/ai/javaclaw/agent/SystemPromptProvider.java` — если выбран путь substitution для `{ENVIRONMENT_INFO}` (#27).
- `workspace/AGENT.md` — default content: удалить literal placeholders (#27).
- `javaclaw-app/src/main/resources/application.yaml` — добавить SSE cancel logging level, при необходимости `reactor.netty.http.client=DEBUG` в integration-test profile.

Existing test files to update:

- `javaclaw-e2e/src/test/java/ai/javaclaw/e2e/support/PlaywrightE2ETestBase.java` — добавить `realLogin(Page page, String username, String password)` вместо `bypassAuth()`. Добавить `JdbcAssertions` inner helper для прямых SQL-проверок через Testcontainer DataSource.

Existing reference files (DO NOT modify, read for patterns):

- `javaclaw-core/src/test/java/ai/javaclaw/agent/pipeline/FewShotExamplesProviderTest.java` — паттерн unit-тестов с `ToolExample.create()`.
- `javaclaw-e2e/src/test/java/ai/javaclaw/e2e/playwright/ChatScenarioE2ETest.java` — паттерн Java Playwright теста с thinking/reasoning/cancel/fallback/low-budget — реюзать фикстуры и test base.
- `javaclaw-api/javaclaw-api-admin/src/main/java/ai/javaclaw/api/admin/files/FileController.java` — паттерн `@DeleteMapping("/**")` с `HttpServletRequest` path extraction для bug #29.
- `docs/user-functionality-verification.md` — ручной чек-лист для финальной приёмки.

### New Files

New backend files to create:

- `javaclaw-api/javaclaw-api-admin/src/main/java/ai/javaclaw/api/admin/tools/ToolExampleController.java` — новый REST контроллер CRUD для `tool_examples` (#23).
- `javaclaw-core/src/main/java/ai/javaclaw/agent/pipeline/ToolExampleService.java` — service слой с `@Transactional` CRUD над `ToolExampleRepository` (уже есть).
- `javaclaw-api/javaclaw-api-admin/src/main/java/ai/javaclaw/api/admin/tools/ToolExampleRequest.java` — DTO для create/update.
- `javaclaw-core/src/main/java/ai/javaclaw/agent/config/DefaultChatModelProperties.java` — `@ConfigurationProperties("spring.ai.openai.chat.options")` для чтения default model name.
- `javaclaw-core/src/main/resources/db/migration/V39__enrich_chat_audit_log.sql` — опционально: добавить `status VARCHAR(20)` колонку если нужна для разделения cancelled/completed. Альтернатива: переиспользовать existing `method` колонку со значениями `stream-cancelled`, `stream-error`.

New Java Playwright E2E tests (все в `javaclaw-e2e/src/test/java/ai/javaclaw/e2e/playwright/`):

- `javaclaw-e2e/src/test/java/ai/javaclaw/e2e/playwright/RoleModelAllowlistE2ETest.java` — bug #19, #20. 3 тест-кейса: bypass default model, USER без config, пустой allowlist.
- `javaclaw-e2e/src/test/java/ai/javaclaw/e2e/playwright/AdminRoleManagementE2ETest.java` — missing scenario. CRUD кастомной роли, permissions, model assignment через `/admin/roles` UI.
- `javaclaw-e2e/src/test/java/ai/javaclaw/e2e/playwright/ChatAuditUserIdE2ETest.java` — bug #25. JDBC assertion `chat_audit_log.user_id = admin_uuid` после chat turn.
- `javaclaw-e2e/src/test/java/ai/javaclaw/e2e/playwright/ChatCancelLifecycleE2ETest.java` — bug #11, #12, #24, #26. Stop mid-stream → проверка partial content в `spring_ai_chat_memory`, `chat_audit_log.method='stream-cancelled'`, `token_usage` не NULL, `history` без дубликатов.
- `javaclaw-e2e/src/test/java/ai/javaclaw/e2e/playwright/ChatAuditIntegrityE2ETest.java` — bug #24, #26. Regex на history column. `token_usage IS NOT NULL` после turn.
- `javaclaw-e2e/src/test/java/ai/javaclaw/e2e/playwright/AutoSummarizationE2ETest.java` — bug #17, #18, #20_sum. `@TestPropertySource` с `max-context-tokens=2000`, отправить 6 длинных сообщений → `conversation_summaries` row exists с `messages_covered >= 4`. Mock LLM для проверки chunking path.
- `javaclaw-e2e/src/test/java/ai/javaclaw/e2e/playwright/RecurringTaskConvIdE2ETest.java` — bug #21, #22. Создать recurring task, ждать 1 исполнение, grep log без `for conversation current` и `for conversation unknown`.
- `javaclaw-e2e/src/test/java/ai/javaclaw/e2e/playwright/FallbackChainE2ETest.java` — bug #14, #15, #16. `@TestPropertySource` с `fallback.enabled=true`, `@MockBean ChatModel`: primary fails → secondary success → verify retry count + options preserved.
- `javaclaw-e2e/src/test/java/ai/javaclaw/e2e/playwright/ToolExampleAdminE2ETest.java` — bug #23. `POST /api/tool-examples`, потом chat turn → `chat_audit_log.system_prompt` содержит `FEW-SHOT-MARKER`.
- `javaclaw-e2e/src/test/java/ai/javaclaw/e2e/playwright/AgentMdDefaultE2ETest.java` — bug #27. После fresh DB → chat turn → `chat_audit_log.system_prompt` **не** содержит `<your full name>` или `{ENVIRONMENT_INFO}`.

## Implementation Phases

### Phase 1: Foundation (Phase 0 + Test Infra)

**Goal:** привести окружение в нормальное состояние + подготовить test infra для новых тестов.

1. Kill backend PID 53462 (low-budget), рестарт с normal config без `-D...token-budget=3000`.
2. Остановить recurring task `joke-every-15-seconds` через SQL `UPDATE recurring_tasks SET active=false WHERE name=?` (закрывает #13).
3. Добавить `realLogin(Page page, String username, String password)` в `PlaywrightE2ETestBase` — использует `page.goto("/login"); page.fill("input[name='username']", ...); page.click("button[type='submit']"); page.waitForURL("**/chat")`.
4. Добавить `JdbcAssertions` helper class с методами `assertAuditRowHasUserId(conversationId, expectedUserId)`, `assertSummaryExistsWithCoverage(conversationId, minMessages)`, `assertHistoryNotDuplicated(conversationId)`, etc.
5. Добавить Flyway migration `V39__enrich_chat_audit_log.sql` если требуется `status` колонка, ИЛИ документ что используем existing `method` колонку со значениями `stream-completed`, `stream-cancelled`, `stream-error`.

### Phase 2: Core Implementation (Bug Fixes)

**Phase 1 (RBAC + Audit user_id):**
- Создать `DefaultChatModelProperties` bean, inject в `ChatRestController`.
- Вычислить `effectiveModel` перед allowlist check.
- Заменить DELETE `/api/roles/{name}/allowed-models/{modelId}` на pattern `/**` с `HttpServletRequest.extractPath`.
- Пробросить `userId` в `ChatAuditService.logSuccess`/`logError` (новая сигнатура) → обновить все call sites.

**Phase 2 (Chat lifecycle):**
- В `ChatService.stream()` обернуть reactor chain в `.doFinally(signalType -> ...)`, persist и audit partial.
- Убрать дубль `chatMemory.add(new UserMessage(userContent))` — оставить только в `MessageAssembler.assemble()`.
- В `ChatAuditService` заполнять `token_usage` из `ChatResponse.getMetadata().getUsage()`, `tool_calls_detail` из `output.getToolCalls()`, `error_*` из exception stack.
- Добавить `log.info("Stream cancelled for conversation {}", cid)` в `SseStreamingService.cancel()`.

**Phase 3 (Summarization):**
- Добавить private `summarizeChunked()` в `ConversationSummaryService` с hierarchical reduce.
- Cumulative counter: читать `previous.messagesCovered` и складывать.
- `@Transactional(REQUIRES_NEW)` + upsert логика (findByConvId → update OR insert).
- Grep `'current'` и `'unknown'` в recurring/task code — заменить литералы на реальный cid.

**Phase 4 (Fallback):**
- `streamWithFallback` retry loop: вложенный for на `maxRetriesPerModel`.
- `withModel` копирует все поля ChatOptions через `builder.from(originalOptions).model(model).build()`.

**Phase 5 (Feature completeness):**
- Новый `ToolExampleController` + `ToolExampleService` + DTOs.
- Санитизировать `workspace/AGENT.md` (убрать literal placeholders).

### Phase 3: Integration & Polish (E2E Tests + Validation)

**Goal:** покрыть каждый фикс Playwright-тестом, пройти полный regression, manual CDP smoke.

1. Написать 10 новых `*E2ETest.java` из списка выше.
2. Обновить `bypassAuth` → `realLogin` во всех TS Playwright тестах `javaclaw-frontend/e2e/*.e2e.ts` (опционально, только для тех что проверяют реальное auth поведение).
3. Прогнать `./mvnw clean verify -Pe2e-mock` — full regression.
4. Прогнать `cd javaclaw-frontend && pnpm e2e` — TS Playwright.
5. Manual CDP smoke через Chrome DevTools MCP — пройти все 10 пунктов `docs/user-functionality-verification.md` под admin и user.
6. Grep `/tmp/javaclaw.log` на `WARN|ERROR` за период регрессии → 0 matches от recurring task.
7. SQL-assertions финальные: `SELECT count(*) FROM chat_audit_log WHERE user_id IS NULL AND created_at > NOW() - INTERVAL '10 min'` → 0.

## Team Orchestration

- Ты lead оркестратор. Не пишешь код, не правишь файлы. Все правки — через деплой команды агентов типа `builder`.
- Каждый billder получает уникальное имя для трекинга через `TaskUpdate(owner=...)`.
- Используешь `Task` tool для деплоя с `resume: true` по умолчанию (контекст между вызовами сохраняется).
- Phases 1-5 фиксов можно деплоить параллельно (`run_in_background: true`), но каждая внутренняя задача в фазе sequential — избегаем race conditions на одном файле.
- После каждой фазы — `validator` прогоняет unit-тесты релевантного модуля и пишет краткий verdict.
- Финальная задача `validate-all` блокируется всеми предыдущими.

### Team Members

- **Builder**
  - Name: `builder-env-reset`
  - Role: Phase 0 — kill/restart backend, остановка recurring task, cleanup /tmp/javaclaw.log.
  - Agent Type: `builder`
  - Resume: false (одноразовая задача, контекст не нужен)
- **Builder**
  - Name: `builder-rbac`
  - Role: Phase 1 — fix model allowlist bypass (#19, #20), DELETE modelId (#29), audit user_id (#25). Работает с `ChatRestController`, `RoleController`, `ChatAuditService`.
  - Agent Type: `builder`
  - Resume: true
- **Builder**
  - Name: `builder-chat-lifecycle`
  - Role: Phase 2 — cancel lifecycle (#11, #12, #24, #26, #3). Работает с `ChatService`, `ChatAuditService`, `SseStreamingService`, `MessageAssembler`.
  - Agent Type: `builder`
  - Resume: true
- **Builder**
  - Name: `builder-summarization`
  - Role: Phase 3 — hierarchical chunking (#20_sum), cumulative counter (#17), atomic upsert (#18), fix literal conversationIds (#21, #22). Работает с `ConversationSummaryService`, recurring task code.
  - Agent Type: `builder`
  - Resume: true
- **Builder**
  - Name: `builder-fallback`
  - Role: Phase 4 — `streamWithFallback` retry loop (#14), `withModel` preserve ChatOptions (#15). Работает с `FallbackChatModel`.
  - Agent Type: `builder`
  - Resume: true
- **Builder**
  - Name: `builder-feature`
  - Role: Phase 5 — `ToolExampleController` REST CRUD (#23), sanitize `AGENT.md` (#27). Работает с `javaclaw-api-admin`, `workspace/`.
  - Agent Type: `builder`
  - Resume: true
- **Builder**
  - Name: `builder-test-infra`
  - Role: Phase 6 part 1 — `realLogin()` helper, `JdbcAssertions` helper в `PlaywrightE2ETestBase`.
  - Agent Type: `builder`
  - Resume: true
- **Builder**
  - Name: `builder-e2e-tests`
  - Role: Phase 6 part 2 — 10 новых Java Playwright тестов. Использует helpers от `builder-test-infra`.
  - Agent Type: `builder`
  - Resume: true
- **Validator**
  - Name: `validator-final`
  - Role: Phase 7 — полный regression (unit + E2E + ручной CDP smoke), проверка acceptance criteria, финальный verdict PASS/FAIL.
  - Agent Type: `validator`
  - Resume: false

## Testing Strategy

Test pyramid ratio: **80% unit / 15% integration-API / 5% UI e2e**

Для этого плана проект уже имеет 1187+ unit + 131+ E2E. Мы добавляем точечные unit + **10 новых Java Playwright E2E** (критично для user-facing scenarios, которые невозможно полно покрыть unit). Соотношение для **новых** тестов будет ≈ 60/10/30, с перекосом в UI — это оправдано, т.к. все баги обнаружены только runtime через браузер, unit уже зелёные.

### Unit Tests (80%)

Для каждого фикса — новый/обновлённый unit-тест. Общий принцип: mock репозитории и ChatModel, verify interactions через Mockito, assertions через AssertJ, аннотация `@DisplayName` на каждом тесте.

- **`ChatRestControllerTest.java`** (existing, update): добавить тесты:
  - `whenUserRoleHasAllowlist_andDefaultModelNotAllowed_thenForbidden()`
  - `whenUserRoleHasAllowlistContainingDefault_thenOk()`
  - `whenRoleHasNoAllowlist_thenAnyModelAllowed()` — регрессия: `roleModelAllowlistService.isModelAllowed("USER", "any")` через mock должен вернуть true.
- **`RoleControllerTest.java`** (existing, update): `whenDeleteAllowedModelWithSlash_thenOk()` — модель `openai/gpt-4o`.
- **`SseStreamingServiceTest.java`** (new или update): самая важная зона. Mock `ChatService.stream()` чтобы вернуть `Flux.just(r1, r2, r3)`. Тесты:
  - `whenStreamCompletes_thenAcc_persisted_andAuditMethodStreamCompleted()` — обычный прогон, `acc` в chatMemory, `chatAuditService.log(... "stream-completed" ...)` called.
  - `whenCancelFlagSet_thenPartialAccPersisted_andAuditMethodStreamCancelled()` — mock Flux с `Flux.interval(100ms).take(10).map(...)`, после первого emit ставим `cancelFlags.get(cid).set(true)`, проверяем что цикл `while (it.hasNext() && !cancelled.get())` выходит, `acc.length() > 0`, `chatMemory.add(assistantMessage)` called, `audit.log(... "stream-cancelled" ...)` called.
  - `whenStreamErrors_thenAuditMethodStreamError_andPartialAccPersisted()` — mock Flux `Flux.concat(Flux.just(r1), Flux.error(new RuntimeException("boom")))`, partial acc persisted, `audit.logError(... "stream-error" ...)` called.
  - `whenStreamCompletes_thenNoUserMessageDuplicate()` — verify `chatMemory.add(new UserMessage)` called **zero** times in SseStreamingService path (user msg добавляется только в MessageAssembler).
  - `whenStreamHasUsageMetadata_thenUsagePassedToAudit()` — mock ChatResponse с `getMetadata().getUsage()`, verify `audit.log(...)` получил non-null usage.
  - `whenStreamHasToolCalls_thenToolCallsPassedToAudit()` — verify toolCalls из `output.getToolCalls()` переданы в audit.
- **`ChatServiceTest.java`** (existing, keep mostly): просто убедиться что `stream()` возвращает `chatModel.stream(prompt)` напрямую без reactive операторов. Все тесты complete/cancel/error перенесены в `SseStreamingServiceTest`.
- **`ChatAuditServiceTest.java`** (new): унифицированный `log(cid, method, prompt, text, duration, userId, usage, toolCalls)` заполняет все колонки. `logError` заполняет `error_message` и `error_trace`. Тесты для method values: `stream-completed`, `stream-cancelled`, `stream-error`.
- **`ConversationSummaryServiceTest.java`** (existing, update):
  - `summarizeDroppedMessages_chunked_whenExceedsContextLimit()` — mock TokenEstimator с большими значениями, verify multiple LLM calls (reduce pattern).
  - `summarizeDroppedMessages_cumulativeCounter()` — previous summary has messagesCovered=5, new batch = 3, result should have messagesCovered=8.
  - `summarizeDroppedMessages_atomicUpsert()` — verify `@Transactional` + find-update-insert logic.
- **`FallbackChatModelTest.java`** (existing, update):
  - `streamWithFallback_retriesMaxTimesPerModel()` — `maxRetriesPerModel=3`, primary падает все 3 раза, затем secondary — verify 6 attempts total.
  - `withModel_preservesAllChatOptions()` — build ChatOptions с temperature, topP, maxTokens, verify все скопированы.
- **`RoleModelAllowlistServiceTest.java`** (existing, keep): null handling НЕ меняется — оставить existing `isModelAllowed("USER", null)==true`.
- **`ToolExampleServiceTest.java`** (new): CRUD операции, фильтр по toolName, ownerId isolation.
- **`SystemPromptProviderTest.java`** (existing, update): если выбран substitution path — тест что `{ENVIRONMENT_INFO}` резолвится.

### Integration / API Tests (15%)

Spring Boot `@WebMvcTest` / `@SpringBootTest` + `Testcontainers` PostgreSQL + `MockMvc`:

- **`ToolExampleControllerIT.java`** (new): POST create → GET list → PUT update → DELETE cycle, проверить row в `tool_examples` через JDBC.
- **`ChatAuditRestIT.java`** (new/update): чат turn под admin → assert `chat_audit_log.user_id = admin_uuid`, `token_usage` не NULL.
- **`ConversationSummarySchedulerIT.java`** (new): triggered summarization с low budget → row в `conversation_summaries` с cumulative counter.
- **`RoleAllowlistChatFlowIT.java`** (new): POST `/api/roles/USER/allowed-models` → POST `/api/chat/send` под user с default модель (НЕ в allowlist) → expect 403.
- **`FallbackChainIT.java`** (new): `@TestPropertySource` с fallback enabled, mock primary fails → secondary returns → verify response.

### UI E2E Tests (5% — но критично для этого плана)

10 новых Java Playwright тестов в `javaclaw-e2e/src/test/java/ai/javaclaw/e2e/` (список и файлы указаны в разделе Relevant Files). Каждый тест:

1. Поднимает `PlaywrightE2ETestBase` с Testcontainer PostgreSQL.
2. Использует новый `realLogin(page, username, password)` helper (не mock).
3. Проходит сценарий через реальный browser (`page.goto`, `fill`, `click`).
4. Делает assertions через `JdbcAssertions` helper над DB state.
5. Для сценариев, требующих mock LLM — использует existing `e2e-mock` profile или `@MockBean ChatModel`.

## Step by Step Tasks

### 1. Phase 0 — Environment Reset

- **Task ID**: env-reset
- **Depends On**: none
- **Assigned To**: builder-env-reset
- **Agent Type**: builder
- **Stack**: Java Spring Boot Bash
- **Parallel**: false
- **Tests**: не требует новых тестов (cleanup task).
- Kill `lsof -ti:8080` (PID 53462).
- Рестарт backend: `OPENROUTER_API_KEY=<env> nohup java -jar javaclaw-app/target/javaclaw-app-exec.jar > /tmp/javaclaw.log 2>&1 &`.
- Дождаться `curl http://localhost:8080/actuator/health` → UP.
- `psql` (через pg8000): `UPDATE recurring_tasks SET active=false WHERE name='joke-every-15-seconds'` — закрывает #13.
- Дождать 60 секунд, проверить `/tmp/javaclaw.log | grep "joke-every-15-seconds"` → 0 new matches.

### 2. Phase 1 — RBAC Allowlist Fix

- **Task ID**: fix-rbac-allowlist
- **Depends On**: env-reset
- **Assigned To**: builder-rbac
- **Agent Type**: builder
- **Stack**: Java Spring Boot controller exception error handling
- **Parallel**: false
- **Tests**: Unit: `ChatRestControllerTest` — 3 новых теста (allowlist с default model). Integration: `RoleAllowlistChatFlowIT` — real MockMvc POST allowed-models + chat 403. E2E: `RoleModelAllowlistE2ETest` (будет написан в write-tests).
- Создать `DefaultChatModelProperties` bean с `@ConfigurationProperties("spring.ai.openai.chat.options")`, prop `model`.
- Inject в `ChatRestController` через constructor.
- В методе `send(...)` перед allowlist check:

  ```java
  final String effectiveModel = (modelOverride != null && !modelOverride.isBlank())
      ? modelOverride
      : defaultChatModelProperties.getModel();
  if (!roleModelAllowlistService.isModelAllowed(userRole, effectiveModel)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
  }
  ```
- Обновить existing `ChatRestControllerTest` с новыми mock-поведениями: default model inject, allowlist проверки.
- Запустить `./mvnw -pl javaclaw-api/javaclaw-api-chat test -Dtest=ChatRestControllerTest`.

### 3. Phase 1 — DELETE allowed-models fix (#29)

- **Task ID**: fix-delete-modelid
- **Depends On**: fix-rbac-allowlist
- **Assigned To**: builder-rbac
- **Agent Type**: builder
- **Stack**: Java Spring Boot controller
- **Parallel**: false
- **Tests**: Unit: `RoleControllerTest` — `whenDeleteAllowedModelWithSlash_thenOk()`. E2E: покрывается `RoleModelAllowlistE2ETest`.
- В `RoleController.java` заменить:

  ```java
  @DeleteMapping("/{name}/allowed-models/**")
  public ResponseEntity<Void> removeAllowedModel(@PathVariable String name, HttpServletRequest request) {
      final String modelId = extractPathAfter(request, "/allowed-models/");
      modelAllowlistService.remove(name, modelId);
      return ResponseEntity.noContent().build();
  }
  ```
- Reuse `extractPath` helper из `FileController.java`.
- Add `RoleControllerTest` case с slash in model id.

### 4. Phase 1 — Chat audit user_id plumbing (#25 — ChatAuditService side only)

- **Task ID**: fix-audit-userid
- **Depends On**: fix-delete-modelid
- **Assigned To**: builder-rbac
- **Agent Type**: builder
- **Stack**: Java Spring Boot jdbc
- **Parallel**: false
- **Tests**: Unit: `ChatAuditServiceTest` — `logSuccess_withUserId_persistsUserId`, `logSuccess_withTokenUsage_persistsUsage`. Integration: `ChatAuditRestIT` — чат turn через MockMvc → assert `user_id` не NULL.
- Важно: `ChatAuditService.logSuccess` уже имеет 8-arg overload. Задача НЕ трогает `ChatService.java` — всю работу по переключению ChatService на 8-arg overload делает task 5 (`fix-cancel-lifecycle`) атомарно вместе с `doFinally` рефактором, чтобы избежать race на `ChatService.java` между agents.
- Проверить что `ChatAuditService.logSuccess(conversationId, method, prompt, text, duration, userId)` или 8-arg overload `(cid, method, prompt, text, duration, userId, tokenUsage, toolCallsDetail)` существует. Если отсутствует — добавить.
- Добавить `logPartial(cid, method="stream-cancelled", prompt, partialText, duration, userId, tokenUsage, toolCallsDetail)` метод.
- Добавить `logError(cid, method="stream-error", prompt, throwable, duration, userId)` — заполнять `error_message` и `error_trace`.
- Обновить `ChatAuditServiceTest` с новыми сценариями.
- Не трогать `ChatService.java` в рамках этой задачи.

### 5. Phase 2 — Cancel lifecycle fix (#11, #12, #24, #26, #3, #25 call-sites)

- **Task ID**: fix-cancel-lifecycle
- **Depends On**: fix-audit-userid
- **Assigned To**: builder-chat-lifecycle
- **Agent Type**: builder
- **Stack**: Java Spring Boot controller exception error handling
- **Parallel**: false
- **Tests**: Unit: `ChatServiceTest` — 4 новых теста (cancel → persist, cancel → audit, no duplicate, userId propagated). Integration: `ChatAuditRestIT` — проверка истории без дубликатов + assert user_id не NULL. E2E: `ChatCancelLifecycleE2ETest`, `ChatAuditIntegrityE2ETest`.
- Эта задача — **единственная**, которая трогает `ChatService.java` и `SseStreamingService.runStream()`. Разбить реализацию на 2 атомарных commit:
  - **Commit A (de-reactive refactor):** переписать `ChatService.stream(...)` и `SseStreamingService.runStream(...)` с реактивной цепочки на **`Flux.toStream() + try-with-resources + AtomicBoolean cancelled`**. Удалить `takeUntilOther`, `cancelSignals` `Sinks.Empty` map, `.blockLast()`, `doOnComplete`, `doOnError`. Упростить `SseStreamingService.cancel(conversationId)` до `cancelFlags.get(conversationId).set(true)`. Убрать дубль `chatMemory.add(new UserMessage(userContent))` в `ChatService.stream():148`.
  - **Commit B (audit + metadata wiring):** в цикле вытащить `usage` из `response.getMetadata().getUsage()` и `toolCalls` из `response.getResult().getOutput().getToolCalls()` в локальные переменные, передать в `chatAuditService.log(cid, method, prompt, text, duration, userId, usage, toolCalls)` в `finally`-блоке.
- **Эталонный код для Commit A** (упрощённая версия, полная будет в `runStream`):

  ```java
  final StringBuilder acc = new StringBuilder();
  final AtomicBoolean cancelled = new AtomicBoolean(false);
  final AtomicReference<Usage> usageRef = new AtomicReference<>();
  final AtomicReference<List<ToolCall>> toolsRef = new AtomicReference<>(List.of());
  cancelFlags.put(conversationId, cancelled);
  Throwable lastError = null;
  try (Stream<ChatResponse> stream = chatService
          .stream(conversationId, userId, userContent, modelOverride)
          .toStream()) {
      Iterator<ChatResponse> it = stream.iterator();
      while (it.hasNext() && !cancelled.get()) {
          ChatResponse r = it.next();
          String text = extract(r);
          if (text != null && !text.isEmpty()) acc.append(text);
          if (r.getMetadata().getUsage() != null) usageRef.set(r.getMetadata().getUsage());
          var tools = r.getResult().getOutput().getToolCalls();
          if (tools != null && !tools.isEmpty()) toolsRef.set(tools);
          sendEvent(emitter, lock, formatSseChunk(r));
      }
  } catch (IOException clientGone) {
      log.debug("SSE client disconnected for conversation {}", conversationId, clientGone);
  } catch (Exception e) {
      lastError = e;
      log.warn("Stream failed for conversation {}", conversationId, e);
      trySendError(emitter, lock, e.getMessage());
  } finally {
      cancelFlags.remove(conversationId);
  }

  final String method = cancelled.get() ? "stream-cancelled"
                      : lastError != null ? "stream-error"
                      : "stream-completed";
  if (!acc.isEmpty()) chatMemory.add(conversationId, List.of(new AssistantMessage(acc.toString())));
  long duration = System.currentTimeMillis() - startTime;
  if (lastError != null) {
      chatAuditService.logError(conversationId, method, prompt, lastError, duration, userId);
  } else {
      chatAuditService.log(conversationId, method, prompt, acc.toString(), duration, userId, usageRef.get(), toolsRef.get());
  }
  // closeSseFrames + emitter.complete() только если не было IOException
  ```
- **Почему `toStream()` работает без Netty и без WebFlux:** Spring AI `OpenAiChatModel.stream()` под капотом использует `RestClient` (servlet-based, JDK HttpClient/Apache), когда `spring-webflux` отсутствует на classpath. Reactor Core (`Flux`) — единственная реактивная зависимость, нужная только для типа возвращаемого значения. `Flux.toStream()` превращает `Flux` в блокирующий `java.util.stream.Stream` через внутренний `Iterator`, который подписывается on-demand. Try-with-resources закрывает `Stream` → `Disposable.dispose()` → cancel propagates через Reactor operators → `RestClient` закрывает TCP. Никакого `reactor-netty`, никакого `WebClient`, никаких hybrid-мостов.
- **Bug #12 (upstream abort):** с `toStream()` cancel детерминированный — `Disposable.dispose()` Reactor гарантированно отправляет cancel в upstream, RestClient закрывает connection. Логирование: `log.info("Stream cancelled for conversation {}", cid)` в `SseStreamingService.cancel()` + tests проверяют `cancelFlags.get(cid)` стал `true` и try-with-resources закрыл stream. Grep на reactor.netty не нужен (его нет в проекте).
- **ChatService.stream() API signature остаётся `Flux<ChatResponse>`** (не меняем — это dependency от Spring AI). Но **потребитель** (SseStreamingService) использует `.toStream()` вместо `.blockLast()` + Reactor операторов. Сам `ChatService.stream()` может быть упрощён: убрать `doOnNext/doOnComplete/doOnError` полностью, просто вернуть `chatModel.stream(prompt)` напрямую. Persist + audit делать в caller (SseStreamingService) — это правильное место для side-effects, ответственность разделена.
- **`SseStreamingService.cancel(conversationId)`** — упрощённая версия:

  ```java
  public boolean cancel(final String conversationId) {
      final AtomicBoolean flag = cancelFlags.get(conversationId);
      if (flag != null) {
          flag.set(true);
          log.info("Stream cancelled for conversation {}", conversationId);
          return true;
      }
      return false;
  }
  ```
- **Новый `ChatAuditService.log(cid, method, prompt, text, duration, userId, usage, toolCalls)`** — унифицированный метод. `method` — VARCHAR колонка в `chat_audit_log`, принимает `stream-completed`/`stream-cancelled`/`stream-error`. Отдельного `logPartial` или `logSuccess` не нужно — один метод. Deprecated старые overload'ы оставить для обратной совместимости.
- **`ChatService.stream()` упрощается** — убрать `doOnNext/doOnComplete/doOnError`, просто вернуть `chatModel.stream(buildPrompt(...))`. Persist + audit — ответственность caller (`SseStreamingService.runStream`).
- Нет Flyway migration — `method` колонка уже VARCHAR, принимает любые новые значения.

### 6. Phase 3 — Summarization Fixes (#17, #18, #20_sum, #21, #22)

- **Task ID**: fix-summarization
- **Depends On**: env-reset
- **Assigned To**: builder-summarization
- **Agent Type**: builder
- **Stack**: Java Spring Boot jdbc jpa
- **Parallel**: true
- **Tests**: Unit: `ConversationSummaryServiceTest` — 3 новых теста (chunked, cumulative, atomic). Integration: `ConversationSummarySchedulerIT` с low budget. E2E: `AutoSummarizationE2ETest`, `RecurringTaskConvIdE2ETest`.
- В `ConversationSummaryService` приватный метод:

  ```java
  private String summarizeChunked(String existingSummary, List<Message> dropped) {
      final int maxChunkTokens = 80_000;
      List<List<Message>> chunks = splitByTokenBudget(dropped, maxChunkTokens);
      if (chunks.size() == 1) {
          return callLlmSingle(existingSummary, chunks.get(0));
      }
      // Reduce: summarize each chunk, then summarize-of-summaries
      List<String> partial = chunks.stream()
          .map(chunk -> callLlmSingle(null, chunk))
          .toList();
      return callLlmSingle(existingSummary, asAssistantMessages(partial));
  }
  ```
- **⚠️ Constraint: blocking-in-async.** `callLlmSingle` — синхронный blocking вызов `chatModel.call()`. Метод `summarizeDroppedMessages` аннотирован `@Async`, выполняется на Spring async executor pool — это OK для blocking IO. **НЕ вызывать** `summarizeChunked` из reactor pipeline (WebFlux/Mono/Flux) — это приведёт к block-on-reactor-thread и таймаутам. Добавить JavaDoc с предупреждением `/** Must run on blocking executor; do not call from Reactor scheduler threads. */`.
- Cumulative counter: `int total = previous.map(s -> s.messagesCovered()).orElse(0) + dropped.size();`.
- Атомарный upsert:

  ```java
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void persistSummary(String cid, String summary, int totalCovered) {
      summaryRepository.findByConversationId(cid)
          .ifPresentOrElse(
              existing -> summaryRepository.save(existing.withSummary(summary, totalCovered)),
              () -> summaryRepository.save(ConversationSummary.create(cid, summary, totalCovered))
          );
  }
  ```
- Поиск литералов `"current"` и `"unknown"` в recurring task code (вероятно `TaskHandler`/`RecurringTaskRunner`), заменить на реальный cid = `"task-" + taskId`.
- Обновить существующий `ConversationSummaryServiceTest` с новыми сценариями.

### 7. Phase 4 — Fallback Chain Fixes (#14, #15)

- **Task ID**: fix-fallback
- **Depends On**: env-reset
- **Assigned To**: builder-fallback
- **Agent Type**: builder
- **Stack**: Java Spring Boot
- **Parallel**: true
- **Tests**: Unit: `FallbackChatModelTest` — 2 новых теста (stream retries, options preserved). Integration: `FallbackChainIT`. E2E: `FallbackChainE2ETest`.
- В `FallbackChatModel.streamWithFallback` добавить retry loop:

  ```java
  Flux<ChatResponse> chain = Flux.error(primaryError);
  for (final String model : fallbackModels) {
      for (int attempt = 0; attempt < properties.maxRetriesPerModel(); attempt++) {
          final String m = model;
          chain = chain.onErrorResume(e -> delegate.stream(withModel(original, m)));
      }
  }
  ```
- В `withModel` использовать builder от оригинальных опций:

  ```java
  static Prompt withModel(Prompt original, String model) {
      ChatOptions origOptions = original.getOptions();
      if (origOptions instanceof ToolCallingChatOptions toolOpts) {
          ToolCallingChatOptions newOpts = ToolCallingChatOptions.builder()
              .from(toolOpts)  // copy all fields, fallback to manual copy if .from not available
              .model(model)
              .build();
          return new Prompt(original.getInstructions(), newOpts);
      }
      ChatOptions newOpts = ChatOptions.builder().from(origOptions).model(model).build();
      return new Prompt(original.getInstructions(), newOpts);
  }
  ```
- **Fallback manual field copy (если `Builder.from()` не существует в используемой Spring AI 1.0.x):** скопировать явно полный список полей `ChatOptions`:
  - `model` — override на fallback model id
  - `temperature`
  - `topP`
  - `topK`
  - `maxTokens`
  - `stopSequences` (List<String>)
  - `frequencyPenalty`
  - `presencePenalty`
  - `toolCallbacks` (List<ToolCallback>) — только для `ToolCallingChatOptions`
  - `internalToolExecutionEnabled` (Boolean) — только для `ToolCallingChatOptions`
  - `toolNames` (Set<String>) — только для `ToolCallingChatOptions`
- Проверить наличие `ToolCallingChatOptions.Builder.from(...)` через `mvn dependency:tree` и IDE — если не найден, использовать manual copy выше.

### 8. Phase 5 — ToolExample Admin API (#23) + AGENT.md fix (#27)

- **Task ID**: fix-feature-orphan
- **Depends On**: env-reset
- **Assigned To**: builder-feature
- **Agent Type**: builder
- **Stack**: Java Spring Boot controller jpa
- **Parallel**: true
- **Tests**: Unit: `ToolExampleServiceTest` — CRUD и фильтрация. Integration: `ToolExampleControllerIT` — full CRUD cycle. E2E: `ToolExampleAdminE2ETest`, `AgentMdDefaultE2ETest`.
- Создать `ToolExampleService` в `javaclaw-core/src/main/java/ai/javaclaw/agent/pipeline/` с методами `findAll(toolName)`, `findById`, `create`, `update`, `delete`. Все методы `@Transactional`.
- Создать `ToolExampleController` в `javaclaw-api/javaclaw-api-admin/src/main/java/ai/javaclaw/api/admin/tools/` с endpoints:
  - `GET /api/tool-examples?toolName=...`
  - `GET /api/tool-examples/{id}`
  - `POST /api/tool-examples` — body `ToolExampleRequest` (toolName, ownerId, exampleOrder, userMessage, assistantMessage, toolCall, toolResult).
  - `PUT /api/tool-examples/{id}`
  - `DELETE /api/tool-examples/{id}`
- Security: require permission `ADMIN_TOOL_EXAMPLES` (новое если не хватает), либо reuse `ADMIN`.
- Санитизировать `workspace/AGENT.md`: убрать `<your full name>`, `<your email address>`, `<your work role>`, `<your address>`, `{ENVIRONMENT_INFO}` — оставить generic prompt без placeholders.

### 9. Phase 6 Part 1 — Test Infrastructure (verify login + add JdbcAssertions)

- **Task ID**: build-test-infra
- **Depends On**: none
- **Assigned To**: builder-test-infra
- **Agent Type**: builder
- **Stack**: Java Spring Boot e2e selenide page object testcontainers
- **Parallel**: true
- **Tests**: test infra not tested directly, но все E2E тесты в write-tests будут его использовать.
- **Первый шаг:** прочитать `javaclaw-e2e/src/test/java/ai/javaclaw/e2e/support/PlaywrightE2ETestBase.java` строки 154-175. Там уже существуют helpers `login(username, password)` и `loginViaStorage(...)`. Проверить что `login()` проходит полный form-based Spring Security flow (POST `/login`, wait redirect, cookie) против Testcontainer.
- **Если existing `login()` достаточен** — не создавать `realLogin`. Использовать existing helper во всех новых E2E тестах напрямую.
- **Если existing `login()` мокает** (route interception, localStorage hack) — добавить отдельный метод `loginViaForm(Page, username, password)` который идёт через реальный `/login` endpoint.
- Добавить inner helper `JdbcAssertions` (static или inner class):

  ```java
  protected static class JdbcAssertions {
      private final DataSource ds;
      public void assertAuditUserIdNotNull(String convId, String expectedUserId) { ... }
      public void assertSummaryCovers(String convId, int minCount) { ... }
      public void assertHistoryNoUserDuplicates(String convId) { ... }
      public void assertTokenUsageNotNull(String convId) { ... }
      public void assertToolExampleCount(int expected) { ... }
  }
  protected JdbcAssertions jdbcAssertions = new JdbcAssertions(testContainerDataSource);
  ```
- Expose `testContainerDataSource` или reuse `@Autowired DataSource` если `@SpringBootTest`.

### 10. Phase 6 Part 2 — Write E2E Tests (дедицированный тест-таск по 80/15/5 правилу)

- **Task ID**: write-tests
- **Depends On**: fix-rbac-allowlist, fix-delete-modelid, fix-audit-userid, fix-cancel-lifecycle, fix-summarization, fix-fallback, fix-feature-orphan, build-test-infra
- **Assigned To**: builder-e2e-tests
- **Agent Type**: builder
- **Stack**: Java MockMvc Mockito assertj allure test structure e2e selenide page object integration test testcontainers
- **Parallel**: false
- **Tests**: sам тест-таск. Производит:
- Написать 10 новых Java Playwright тестов, список:
  1. `RoleModelAllowlistE2ETest.java` (3 тест-кейса).
  2. `AdminRoleManagementE2ETest.java` (CRUD кастомной роли через UI).
  3. `ChatAuditUserIdE2ETest.java` (JDBC assertion user_id).
  4. `ChatCancelLifecycleE2ETest.java` (partial persist, method=stream-cancelled, token_usage, no history duplicates).
  5. `ChatAuditIntegrityE2ETest.java` (regex на history, token_usage NOT NULL).
  6. `AutoSummarizationE2ETest.java` (@TestPropertySource max-context-tokens=2000, cumulative counter).
  7. `RecurringTaskConvIdE2ETest.java` (grep log no 'current'/'unknown').
  8. `FallbackChainE2ETest.java` (@MockBean ChatModel, retries verified).
  9. `ToolExampleAdminE2ETest.java` (POST example → chat → system_prompt contains marker).
  10. `AgentMdDefaultE2ETest.java` (fresh start → no placeholders in system_prompt).
- Все тесты extend `PlaywrightE2ETestBase`, используют `realLogin` и `JdbcAssertions`.
- Все — `@Allure` аннотации, `@DisplayName` для каждого теста, AssertJ для assertions.
- Запустить `./mvnw -pl javaclaw-e2e verify -Pe2e-mock -Dtest=*E2ETest` — все 10 зелёные.

### 11. Phase 7 — Final Validation

- **Task ID**: validate-all
- **Depends On**: env-reset, fix-rbac-allowlist, fix-delete-modelid, fix-audit-userid, fix-cancel-lifecycle, fix-summarization, fix-fallback, fix-feature-orphan, build-test-infra, write-tests
- **Assigned To**: validator-final
- **Agent Type**: validator
- **Stack**: Java Spring Boot testcontainers integration test e2e mvn
- **Parallel**: false
- **Tests**: прогоняет все unit + integration + e2e.
- `./mvnw clean verify -Pe2e-mock` — full regression. Все 1187+ unit + 131+ старых E2E + 10 новых E2E зелёные.
- `cd javaclaw-frontend && pnpm e2e` — TS Playwright зелёный.
- `grep -cE "WARN|ERROR" /tmp/javaclaw.log` — значение за последние 10 минут после рестарта < N (порог для baseline, без нарастания от recurring spam).
- SQL через pg8000: `SELECT count(*) FROM chat_audit_log WHERE user_id IS NULL AND created_at > NOW() - INTERVAL '10 min'` → 0.
- Ручной CDP-прогон всех 10 пунктов `docs/user-functionality-verification.md` под admin и user — все checks зелёные.
- Итоговый отчёт: PASS/FAIL + список бага ↔ тест mapping.

## Acceptance Criteria

1. **Все 21 багов закрыты** — каждый исправлен в указанных файлах, каждый покрыт unit-тестом.
2. **10 новых Java Playwright E2E тестов** написаны и зелёные — каждый использует `realLogin` и `JdbcAssertions`.
3. **Admin REST API для tool_examples** создан, закрыт CRUD, unit + integration + e2e зелёные.
4. **`AGENT.md` default санитайзен** — нет literal placeholders, chat_audit_log.system_prompt чистый.
5. **Full regression зелёный:** `./mvnw clean verify -Pe2e-mock` + `pnpm e2e` + manual CDP smoke — 0 регрессий.
6. **Log hygiene:** `grep 'joke-every-15-seconds' /tmp/javaclaw.log` за последние 10 минут = 0 matches. `grep 'for conversation current' /tmp/javaclaw.log` = 0 matches.
7. **SQL integrity:** `chat_audit_log.user_id IS NOT NULL` для всех новых записей. `chat_audit_log.token_usage IS NOT NULL` после turn с успешным LLM call. `chat_audit_log.history` не содержит duplicate USER messages (regex check).
8. **Security:** `curl -u user:user POST /api/chat/send` с дефолтной моделью, если USER allowlist содержит только другую модель → HTTP 403. `curl DELETE /api/roles/USER/allowed-models/{URL-encoded slash}` → HTTP 204.
9. **Commit:** `gnhf #40: fix Phase 13/6 RBAC + chat lifecycle + summarization + fallback + feature orphan (21 bugs) + 10 E2E tests`.

## Validation Commands

Выполнять в указанном порядке:

- `./mvnw -pl javaclaw-core test` — все unit-тесты javaclaw-core зелёные (включая обновлённые `ChatServiceTest`, `ConversationSummaryServiceTest`, `FallbackChatModelTest`, `ChatAuditServiceTest`, `ToolExampleServiceTest`).
- `./mvnw -pl javaclaw-api/javaclaw-api-chat test` — `ChatRestControllerTest` зелёный.
- `./mvnw -pl javaclaw-api/javaclaw-api-admin test` — `RoleControllerTest`, `ToolExampleControllerTest` зелёные.
- `./mvnw clean verify -Pe2e-mock` — полный build + integration + E2E с mock LLM.
- `./mvnw -pl javaclaw-e2e verify -Pe2e-mock -Dtest=*E2ETest` — 10 новых Playwright тестов + existing 56 зелёные.
- `cd javaclaw-frontend && pnpm e2e` — TS Playwright зелёный (существующие 71 тестов).
- `curl -s -u admin:admin http://localhost:8080/actuator/health` → `{"status":"UP"}`.
- `grep -cE "joke-every-15-seconds" /tmp/javaclaw.log` (за новый период) → 0.
- `grep -cE "for conversation (current|unknown)" /tmp/javaclaw.log` → 0.
- `python3 -c "import pg8000; c=pg8000.Connection(...); cur=c.cursor(); cur.execute(\"SELECT count(*) FROM chat_audit_log WHERE user_id IS NULL\"); print(cur.fetchone())"` → 0 для записей после фикса.
- Manual CDP — пройти 10 пунктов `docs/user-functionality-verification.md` под admin и user.

## Notes

- **Reasoning events (#2_thinking) — DONE 2026-04-11.** Вынесено в отдельный план `specs/openai-compat-reasoning-events.md` и реализовано: новый модуль `javaclaw-provider-openai` с `ReasoningAwareChatModel` (decorator поверх Spring AI `OpenAiChatModel`), парсером SSE chunks (поддержка `reasoning_details[]` / legacy `reasoning` / `reasoning_content`), `ReasoningRequestBuilder` с инжектом `reasoning: {effort}` в body, state machine `INIT→REASONING→POST_REASONING→FINISH` с signature chunk перед первым non-reasoning токеном (content или tool_call). Автоконфигурация через `@AutoConfiguration(after=OpenAiChatAutoConfiguration.class)` + `@ConditionalOnBean(OpenAiChatModel.class)` + `@ConditionalOnProperty("javaclaw.reasoning.enabled")`. Приоритизация в `ChatServiceConfiguration.fallbackChatModel()` через marker-интерфейс `ReasoningCapableChatModel`. 22 новых теста (unit + integration + E2E WireMock), все 255+ тестов в затронутых модулях зелёные.
- **Runtime проверка fallback chain (#16)** покрыта полностью через `FallbackChainE2ETest` с `@TestPropertySource`, не требует рестарта production backend.
- **TS Playwright `bypassAuth` рефактор** опционально — если в ходе работы обнаружится, что какие-то TS тесты скрывают баги под моком — переписать их отдельным incremental PR. В текущем плане фокус на Java E2E для backend contract verification.
- **Spring AI Version:** проверить что `ToolCallingChatOptions.Builder.from(...)` существует в используемой версии Spring AI (проект на 1.0.x). Если нет — ручное копирование полей (temperature, topP, maxTokens, stopSequences, frequencyPenalty, presencePenalty).
- **Dependency versions:** pg8000 (Python) используется в плане только для test assertions в CDP-прогонах, не для production кода. Java тесты используют `DataSource` из Testcontainer directly.
- **OpenSpec:** этот план зарегистрирован в `specs/chat-runtime-bug-fixes-e2e-coverage.md`. Если `openspec` будет использован — имя change: `chat-runtime-bug-fixes-e2e-coverage`.

