# Plan: Task Architecture Gap Closure

## Task Description

Закрыть оставшиеся ~10% верифицированных пробелов в реализации `specs/task-architecture-spec.md` — 7 gap-задач: 4 новых backend integration-теста, 1 расширение Playwright E2E, 1 scaling integration-тест с Maven профилем, 1 manual verification checklist. Все gap-ы определены по результатам критического ревью (plan-reviewer) исходного плана `/Users/artemsimeisn/.claude/plans/snoopy-puzzling-quasar.md`. Функциональный код не пишется — только тесты и документация; все упомянутые в тестах сервисы (`ApprovalService`, `ChatAuditService`, `PgNotificationTransport`, `TelegramChannel`, `ChannelContextService`, `DeliveryRecovery`) уже реализованы в gnhf #1–#49.

Type: **chore** (тестовое покрытие) / Complexity: **medium**.

## Objective

После выполнения плана:
- Backend: **~730+ зелёных тестов** (добавится ~14–17 новых integration-тестов), `mvn test` всё ещё зелёный.
- Scaling E2E (2 кейса) запускается изолированно через `mvn verify -Pscaling-e2e` и не ломает обычный CI.
- Frontend Playwright: `task-advanced.e2e.ts` расширен сценарием E18 chat audit viewer.
- Документ `docs/manual-verification-task-architecture.md` содержит чеклист S1–S20 + E31 для ручного прогона на staging.
- `specs/task-architecture-spec.md` обновлён секцией `## Status: Implemented 2026-04-10`.
- Auto-memory `project_task_architecture.md` переведена в `DONE (100%)`.

## Problem Statement

Исходная спецификация task architecture была реализована за 46 коммитов (gnhf #1–#49) и достигла ~90% покрытия. После ревью исходного gap-closure плана `plan-reviewer` нашёл **3 критических блокера** (некорректный threading в T50, WireMock-подход для E26–E28, отсутствующая зависимость `testcontainers-compose` для E29–E31) и 5 важных замечаний. Без их исправления план нельзя было выполнять. Текущий team-plan вобрал все правки: T50 с конкретным `doAnswer` паттерном, E26–E28 через mocked `TelegramClient` вместо WireMock, E29–E30 через 2 Spring context в одном JVM вместо docker-compose. Пробелы отобраны после парной Explore-разведки (проверено какие тесты реально существуют) — ложные тревоги (T27, T28, T57, T60) сняты.

## Solution Approach

Семь независимых gap-задач, каждая — отдельный builder-агент. Большинство параллельны: P0 (Gap1+Gap2) → P1 (Gap3+Gap4) → P2 (Gap5+Gap6+Gap7), но внутри каждого уровня параллельность полная. Финальная задача — `validator`, который прогоняет `./mvnw test`, `mvn verify -Pscaling-e2e`, `pnpm test:e2e`, обновляет статус в спеке и memory.

Ключевые технические решения (унаследованы из review-approved плана):
- **T50** threading: mock `Agent` через `@TestBean.doAnswer` вызывает `approvalService.requestApproval()` изнутри, тест запускает task в `ExecutorService`, ждёт `approvalRequestRepository.findByTaskIdAndStatus(taskId, ApprovalRequest.Status.pending)` через Awaitility, затем `approvalService.submitApproval(conversationId, userResponse)` из main thread (**2 параметра: conversationId + userResponse**).
- **T59** sync: `Awaitility` polling по `chat_audit_log_repository.findLatest()` для обхода `@Async`.
- **T56** cleanup: `@AfterEach` вызывает `destroy()` на обоих `PgNotificationTransport` инстанциях.
- **E26–E28** mock: `@TestBean TelegramClient` + `ArgumentCaptor<SendMessage>`, без WireMock.
- **E29–E30** infra: 2 `SpringApplicationBuilder` в одном JVM, shared Testcontainers PG, профиль `scaling-e2e` + `@Tag("scaling-e2e")` в Failsafe.
- **E31** (pod kill + SSE reconnect) — сознательно перенесён в **manual checklist**, автоматизация flaky.

## Relevant Files

### Существующие (прочитать перед работой)

- `specs/task-architecture-spec.md` — исходная спецификация (секции Unit/Integration/E2E Tests, строки ~777–1020).
- `/Users/artemsimeisn/.claude/plans/snoopy-puzzling-quasar.md` — review-approved gap-closure план (источник истины по подходу к каждому gap).
- `javaclaw-app/src/test/java/ai/javaclaw/integration/TaskExecutionFlowIntegrationTest.java` — **эталонный паттерн** Testcontainers + `@TestBean` для T50/T59/E26–E28.
- `javaclaw-app/src/test/java/ai/javaclaw/integration/ApprovalApiIntegrationTest.java` — частично покрывает T50 на REST-слое; не дублировать.
- `javaclaw-app/src/test/java/ai/javaclaw/integration/TaskAuditApiIntegrationTest.java` — fixtures + паттерн для T59.
- `javaclaw-app/src/test/java/ai/javaclaw/integration/DeliveryIntegrationTest.java` — покрывает T55, T51, T32. Не дублировать для T57.
- `javaclaw-core/src/main/java/ai/javaclaw/tasks/ApprovalService.java` — `requestApproval()` / `submitApproval()` / `findByTaskIdAndStatus` в репозитории.
- `javaclaw-core/src/main/java/ai/javaclaw/tasks/TaskHandler.java` — `executeTask()`, интеграция с `Agent` и `ApprovalService`.
- `javaclaw-core/src/main/java/ai/javaclaw/agent/audit/ChatAuditService.java` — `logSuccess(...)` перегрузки (простая + extended с user_id/tool_calls_detail/token_usage), `@Async`.
- `javaclaw-core/src/main/java/ai/javaclaw/agent/audit/ChatAuditLogRepository.java` — `findByConversationIdOrderByCreatedAtDesc(conversationId)` для queries в тестах.
- `javaclaw-core/src/main/java/ai/javaclaw/agent/pipeline/ChatService.java` — `stream(conversationId, userContent)` / `call(conversationId, userContent)` / `call(conversationId, userContent, resultType)` (нет `sendMessage`).
- `javaclaw-core/src/main/java/ai/javaclaw/agent/audit/ChatAuditLog.java` — `toolCallsDetail()` accessor.
- `javaclaw-core/src/main/java/ai/javaclaw/delivery/DeliveryQueue.java` (строки 130–157) — `withFailed` / `exponentialBackoff` (подтверждение закрытости T27/T28/T57).
- `javaclaw-api/javaclaw-api-chat/src/main/java/ai/javaclaw/api/chat/delivery/PgNotificationTransport.java` — `broadcast()` / `subscribe()` / listener-thread lifecycle.
- `javaclaw-api/javaclaw-api-chat/src/test/java/ai/javaclaw/api/chat/delivery/PgNotificationTransportTest.java` — unit (mock Connection). Не дублировать.
- `javaclaw-api/javaclaw-api-chat/src/main/java/ai/javaclaw/api/chat/controller/ChatAuditController.java` — verify URL `@GetMapping` перед написанием E18.
- `javaclaw-channel/javaclaw-channel-telegram/src/main/java/ai/javaclaw/channels/telegram/TelegramChannel.java` — `execute()` через `TelegramClient`.
- `javaclaw-channel/javaclaw-channel-telegram/src/test/java/ai/javaclaw/channels/telegram/TelegramChannelTest.java` — паттерн mocking `TelegramClient`.
- `javaclaw-core/src/main/java/ai/javaclaw/channels/ChannelContextService.java` — V13 per-channel routing.
- `javaclaw-core/src/main/java/ai/javaclaw/delivery/DeliveryRecovery.java` — startup recovery.
- `javaclaw-e2e/src/test/java/ai/javaclaw/e2e/playwright/MultiInstanceE2ETest.java` — существующий частичный multi-instance тест (только shared DB).
- `javaclaw-frontend/e2e/task-advanced.e2e.ts` — файл для расширения E18.
- `javaclaw-frontend/src/api/audit.ts` — verify endpoint paths.
- `javaclaw-e2e/pom.xml` — место для профиля `scaling-e2e`.

### Новые файлы (создаются задачами)

Перечислены в соответствующих разделах "Step by Step Tasks" (пути в описаниях тасков), не в Relevant Files.

## Implementation Phases

### Phase 1: Foundation (P0 integration tests)

Написать `ApprovalFlowIntegrationTest` (T50) и `ChatAuditToolCallsIntegrationTest` (T59). Эти тесты закрывают самые болезненные пробелы в core-логике (approval resume, async chat audit) и дают уверенность в фундаменте.

### Phase 2: Core Implementation (P1 integration + frontend E2E)

`PgNotificationTransportIntegrationTest` (T56) верифицирует multi-pod transport через 2 независимые JDBC connections. `task-advanced.e2e.ts` расширяется сценарием E18 chat audit viewer.

### Phase 3: Integration & Polish (P2 multi-channel + scaling + docs)

`TelegramMultiChannelIntegrationTest` (E26–E28) — per-channel routing изоляция. `ScalingIntegrationTest` (E29–E30) + Maven profile `scaling-e2e` + `pom.xml` правка — horizontal scaling. `manual-verification-task-architecture.md` — ручной чеклист. Финальная валидация: `./mvnw test` + `mvn verify -Pscaling-e2e` + `pnpm test:e2e` + обновление статуса в спеке и memory.

## Team Orchestration

- Я выступаю team lead: оркеструю `builder` и `validator` через `Task*` инструменты, не пишу код напрямую.
- 7 gap-тасков размечены параллельно по фазам. Каждый builder получает свой `Task ID` и контекст из соответствующего раздела этого плана + ссылку на `snoopy-puzzling-quasar.md` за деталями паттернов.
- После всех builders — один `validator` прогон, агрегирующий результаты.
- Session ID каждого агента запоминается для возможного resume (уточнение флаки, правки после ревью).

### Team Members

- Builder
  - Name: `builder-t50-approval`
  - Role: написать `ApprovalFlowIntegrationTest` — end-to-end approval lifecycle с threading-паттерном через `@TestBean Agent.doAnswer`.
  - Agent Type: `builder`
  - Resume: true
- Builder
  - Name: `builder-t59-chat-audit`
  - Role: написать `ChatAuditToolCallsIntegrationTest` — tool_calls_detail integration с `@Async` синхронизацией через Awaitility.
  - Agent Type: `builder`
  - Resume: true
- Builder
  - Name: `builder-t56-pg-notify`
  - Role: написать `PgNotificationTransportIntegrationTest` — 2 независимых transport инстанции над одним Testcontainers PG.
  - Agent Type: `builder`
  - Resume: true
- Builder
  - Name: `builder-e18-audit-playwright`
  - Role: расширить `task-advanced.e2e.ts` сценарием E18 chat audit viewer (pre-step: grep endpoint URL).
  - Agent Type: `builder`
  - Resume: true
- Builder
  - Name: `builder-e26e28-telegram`
  - Role: написать `TelegramMultiChannelIntegrationTest` — mock `TelegramClient` через `@TestBean` + `ArgumentCaptor<SendMessage>`.
  - Agent Type: `builder`
  - Resume: true
- Builder
  - Name: `builder-e29e30-scaling`
  - Role: добавить Maven профиль `scaling-e2e` в `javaclaw-e2e/pom.xml` + написать `ScalingIntegrationTest` с 2 `SpringApplicationBuilder`.
  - Agent Type: `builder`
  - Resume: true
- Builder
  - Name: `builder-manual-checklist`
  - Role: создать `docs/manual-verification-task-architecture.md` (S1–S20 + E31 пункты).
  - Agent Type: `builder`
  - Resume: false
- Validator
  - Name: `validator-final`
  - Role: прогнать `./mvnw test`, `mvn verify -Pscaling-e2e`, `pnpm test:e2e` + обновить `specs/task-architecture-spec.md` Status секцию и memory `project_task_architecture.md`.
  - Agent Type: `validator`
  - Resume: false

## Testing Strategy

**Примечание**: Этот план сам по себе — test-writing initiative. Все 6 builder-задач пишут тесты. Test pyramid здесь **"инвертирован"**: новая продуктовая логика не пишется, покрываются уже существующие сервисы. Распределение тестов **по количеству новых кейсов**:

- **0% новых unit тестов** — unit-покрытие уже сделано в gnhf #1–#49.
- **~88% integration / API тестов** (15 кейсов из 17): T50 ×3, T59 ×3, T56 ×3, E26–E28 ×3, E29–E30 ×2, плюс 1 правка `pom.xml` как integration-scaffolding.
- **~12% UI e2e** (2 кейса): E18 Playwright chat audit viewer.

Пропорция 80/15/5 неприменима — это **тестовое покрытие для уже написанного кода**, а не новая фича. Формально план соответствует требованию через dedicated test-writing задачи + финальную `validate-all`.

### Unit Tests (0%)

Не требуются. Существующие unit: `ApprovalServiceTest`, `ChatAuditLogExtendedTest`, `AuditToolTest`, `PgNotificationTransportTest`, `DeliveryQueueTest`, `TaskHandlerTest`, `TelegramChannelTest`. Не дублировать.

### Integration / API Tests (88%)

- `ApprovalFlowIntegrationTest` (T50, 3 кейса):
  - `t50a_approvalGranted_taskResumesAndCompletes` — happy path, `submitApproval(conversationId, "approved")`.
  - `t50b_approvalDenied_taskContinuesWithDeniedContext` — `submitApproval(conversationId, "denied")`, agent получает denial строку.
  - `t50c_approvalWithTextReply_taskReceivesCustomResponse` — `submitApproval(conversationId, "только эконом")`, agent получает текст как ответ.
  - Assert: `task.status == completed`, `task_audit_log` содержит `APPROVAL_REQUESTED` + `APPROVAL_GRANTED`, `approval_requests.status`.
- `ChatAuditToolCallsIntegrationTest` (T59, 3 кейса):
  - `t59a_singleToolCall_detailsPopulated` — один tool call, assert имя + args в `toolCallsDetail` JSON.
  - `t59b_multipleToolCalls_allDetailsSerialized` — несколько tool calls в одном turn.
  - `t59c_errorPath_auditStillLogged` — `agent.prompt()` throws → `logError()` вызван, запись в audit есть.
- `PgNotificationTransportIntegrationTest` (T56, 3 кейса):
  - `t56a_crossInstanceBroadcast` — podA publish → podB receive в пределах 2s.
  - `t56b_filterByConversationId` — publish в conv1, subscribe на conv2 → timeout (нет события).
  - `t56c_orderPreserved` — 5 последовательных broadcast → podB получает в том же порядке.
  - `@AfterEach` вызывает `destroy()` на обоих транспортах.
- `TelegramMultiChannelIntegrationTest` (E26–E28, 3 кейса):
  - `e26_taskFromTelegram_notificationRoutedToTelegram` — `ArgumentCaptor<SendMessage>` захватил chat_id.
  - `e27_taskFromWebChat_notificationNotSentToTelegram` — `verifyNoInteractions(telegramClient)`, `NotificationTransport.broadcast()` вызван.
  - `e28_perChannelPeerMode_separateHistories` — один user, 2 канала, `chat_messages` содержит разные `conversation_id`.
- `ScalingIntegrationTest` (E29–E30, 2 кейса, `@Tag("scaling-e2e")`):
  - `e29_twoPodsSharedDb_taskVisibleFromBoth` — podA POST → podB GET → task видна.
  - `e30_crossPodSseViaPgNotify` — subscribe на podA, broadcast на podB → событие приходит.

### UI E2E Tests (12%)

- `task-advanced.e2e.ts` → добавить describe `E18 — chat audit viewer`:
  - `fetches last 5 chat audit entries with correct fields` — mock endpoint, assert массив из 5 записей с `timestamp`, `durationMs`, `tokenUsage`, `toolCallsDetail`.
  - `filter by user_id returns user-scoped list` — mock 200 с отфильтрованным массивом.

## Step by Step Tasks

Перед стартом — `TaskCreate` для всех 8 задач, затем `TaskUpdate` с `addBlockedBy`, потом последовательный `Task` деплой builders.

### 1. T50 — ApprovalFlowIntegrationTest

- **Task ID**: `gap1-t50-approval-flow-it`
- **Depends On**: none
- **Assigned To**: `builder-t50-approval`
- **Agent Type**: `builder`
- **Stack**: `Java Spring Boot integration test Testcontainers mockito spy exception error handling`
- **Parallel**: true
- **Tests**: Integration: `ApprovalFlowIntegrationTest` — 3 кейса (t50a/b/c), approval lifecycle с threading через `@TestBean Agent`. Self-contained (этот таск сам пишет тесты).
- Создать `javaclaw-app/src/test/java/ai/javaclaw/integration/ApprovalFlowIntegrationTest.java`.
- Использовать паттерн из `TaskExecutionFlowIntegrationTest`: `@SpringBootTest` + `@Testcontainers` PostgreSQL + `@TestBean` для `Agent`.
- Настроить `doAnswer` на `agent.prompt(...)` так, чтобы внутри он вызывал `approvalService.requestApproval(taskId, "Купить билет?", Duration.ofSeconds(30))` и возвращал её результат.
- Threading: `ExecutorService executor = Executors.newSingleThreadExecutor(); Future<Void> future = executor.submit(() -> taskHandler.executeTask(taskId));`.
- Ожидание pending: `Awaitility.await().atMost(5, SECONDS).until(() -> approvalRequestRepository.findByTaskIdAndStatus(taskId, ApprovalRequest.Status.pending).isPresent());`.
- Submit (сигнатура 2 параметра): `approvalService.submitApproval(conversationId, userResponseText);` из main thread. Для grant передать `"approved"`, для deny — `"denied"`, для text-reply — произвольную строку (она служит и маркером, и содержанием ответа).
- Assert после `future.get(10, SECONDS)`: `task.status == completed`, `task_audit_log` events, `approval_requests.status == approved`.
- Async audit: Awaitility polling по `taskAuditLogRepository.findByTaskId(taskId)` size.
- Запустить локально `./mvnw -pl javaclaw-app test -Dtest=ApprovalFlowIntegrationTest` — до зелёного.

### 2. T59 — ChatAuditToolCallsIntegrationTest

- **Task ID**: `gap2-t59-chat-audit-it`
- **Depends On**: none
- **Assigned To**: `builder-t59-chat-audit`
- **Agent Type**: `builder`
- **Stack**: `Java Spring Boot integration test Testcontainers mockito database test repository test`
- **Parallel**: true
- **Tests**: Integration: `ChatAuditToolCallsIntegrationTest` — 3 кейса (t59a/b/c), tool_calls_detail персистенция с `@Async` синхронизацией.
- Создать `javaclaw-app/src/test/java/ai/javaclaw/integration/ChatAuditToolCallsIntegrationTest.java`.
- `@TestBean` `ChatClient` — вернуть `ChatResponse` с заполненным списком `tool_calls` (mocked AssistantMessage.toolCalls).
- Вызвать `chatService.call(conversationId, "test prompt")` (НЕ `sendMessage` — такого метода нет).
- Awaitility polling для `@Async` `logSuccess`: `await().atMost(3, SECONDS).until(() -> { var list = chatAuditLogRepository.findByConversationIdOrderByCreatedAtDesc(conversationId); return !list.isEmpty() && list.get(0).toolCallsDetail() != null; });`.
- Assert `list.get(0).toolCallsDetail()` содержит имена tools (JSON substring check или десериализация).
- Error path: `doThrow(new RuntimeException("LLM timeout")).when(chatClient).prompt(any())`, `assertThrows` + Awaitility polling по `chat_audit_log` с `status='failed'`, assert `error_message != null`.
- Запустить локально `./mvnw -pl javaclaw-app test -Dtest=ChatAuditToolCallsIntegrationTest` — до зелёного.

### 3. T56 — PgNotificationTransportIntegrationTest

- **Task ID**: `gap3-t56-pg-notify-it`
- **Depends On**: none
- **Assigned To**: `builder-t56-pg-notify`
- **Agent Type**: `builder`
- **Stack**: `Java Spring Boot integration test Testcontainers jdbc test database test`
- **Parallel**: true
- **Tests**: Integration: `PgNotificationTransportIntegrationTest` — 3 кейса (t56a/b/c), cross-instance через реальный NOTIFY/LISTEN.
- Создать `javaclaw-api/javaclaw-api-chat/src/test/java/ai/javaclaw/api/chat/delivery/PgNotificationTransportIntegrationTest.java`.
- `@Testcontainers` PostgreSQL контейнер. Создать **две независимые** `HikariDataSource` инстанции на один контейнер.
- Инстанцировать `PgNotificationTransport podA = new PgNotificationTransport(dsA);` и `podB = new PgNotificationTransport(dsB);` + вызвать init (`@PostConstruct` если есть или вручную `startListenLoop`).
- `podA.broadcast(conversationId, event); StepVerifier.create(podB.subscribe(conversationId).next()).expectNextMatches(...).verifyComplete();` (или Awaitility если не Reactor-based).
- `@AfterEach`: вызвать `destroy()` или `close()` на обоих (проверить наличие `@PreDestroy` в `PgNotificationTransport.java`; если нет — добавить или использовать рефлексию для остановки listener-thread).
- Запустить локально `./mvnw -pl javaclaw-api/javaclaw-api-chat test -Dtest=PgNotificationTransportIntegrationTest` — до зелёного.

### 4. E18 — Playwright chat audit viewer

- **Task ID**: `gap4-e18-playwright-audit`
- **Depends On**: none
- **Assigned To**: `builder-e18-audit-playwright`
- **Agent Type**: `builder`
- **Stack**: `React hook tsx vite react-router`
- **Parallel**: true
- **Tests**: UI e2e: 2 кейса в `task-advanced.e2e.ts` (fetch last 5 + user-scoped filter).
- **Pre-step**: `grep -rn '@GetMapping.*audit' javaclaw-api/javaclaw-api-chat/src/main/` + `cat javaclaw-frontend/src/api/audit.ts` — verify фактический URL (`/api/chat/audit` vs `/api/audit/chat`).
- Расширить `javaclaw-frontend/e2e/task-advanced.e2e.ts` — добавить `test.describe('E18 — chat audit viewer', () => { ... })` с 2 тестами.
- Mock endpoint через `page.route(urlPattern, (route) => route.fulfill({ json: mockAuditList }))`.
- Assert: direct `page.evaluate(() => fetch(...))` или, если есть UI компонент chat-audit-viewer, assert DOM-элементы.
- Запустить локально: `cd javaclaw-frontend && pnpm test:e2e task-advanced` — до зелёного.

### 5. E26–E28 — Telegram multi-channel integration

- **Task ID**: `gap5-e26e28-telegram-it`
- **Depends On**: none
- **Assigned To**: `builder-e26e28-telegram`
- **Agent Type**: `builder`
- **Stack**: `Java Spring Boot integration test Testcontainers mockito spy controller`
- **Parallel**: true (совместно с Gap3/Gap4 после Gap1)
- **Tests**: Integration: `TelegramMultiChannelIntegrationTest` — 3 кейса (e26/e27/e28), per-channel routing.
- Создать `javaclaw-app/src/test/java/ai/javaclaw/integration/TelegramMultiChannelIntegrationTest.java`.
- `@SpringBootTest` + `@Testcontainers` + `@TestBean TelegramClient` (mock).
- `ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class); verify(telegramClient, times(1)).execute(captor.capture());`.
- e26: создать task через `ChatService.sendMessage` с `RoutingContext{sourceChannel="telegram", telegramChatId=123L}` → assert captor.getValue().getChatId() == "123".
- e27: создать task с `sourceChannel="web_chat"` → `verifyNoInteractions(telegramClient)` + `verify(notificationTransport).broadcast(anyString(), any())`.
- e28: симулировать одного userId с двумя conversations (`telegram` + `web_chat`) → query `chat_messages` → assert `conversation_id` разные.
- Запустить локально `./mvnw -pl javaclaw-app test -Dtest=TelegramMultiChannelIntegrationTest` — до зелёного.

### 6. E29–E30 — Scaling integration + Maven profile

- **Task ID**: `gap6-e29e30-scaling-it`
- **Depends On**: `gap3-t56-pg-notify-it`
- **Assigned To**: `builder-e29e30-scaling`
- **Agent Type**: `builder`
- **Stack**: `Java Spring Boot integration test Testcontainers maven surefire failsafe jdbc test`
- **Parallel**: false (начинает после Gap3)
- **Tests**: Integration: `ScalingIntegrationTest` — 2 кейса (e29/e30), `@Tag("scaling-e2e")` изолированный профиль.
- Отредактировать `javaclaw-e2e/pom.xml`: добавить `<profile><id>scaling-e2e</id><build><plugins><plugin>maven-failsafe-plugin</plugin></plugins></build></profile>` с конфигурацией `<groups>scaling-e2e</groups>`, `<includes>**/*IntegrationTest.java</includes>` и `<argLine>-Xmx2g</argLine>` (2 Spring контекста + Testcontainers требуют больше heap).
- Создать `javaclaw-e2e/src/test/java/ai/javaclaw/e2e/ScalingIntegrationTest.java`:
  - `@Testcontainers` один PostgreSQL контейнер.
  - 2 `ConfigurableApplicationContext` через `new SpringApplicationBuilder(JavaClawApplication.class).properties(...).run("--server.port=0")` с разными портами, shared `spring.datasource.url` из контейнера, профиль `scaling` (или `e2e-scaling`) для форса `PgNotificationTransport`.
  - `@BeforeAll` — старт обоих контекстов, `@AfterAll` — close.
  - e29: `podA.getBean(TaskRepository.class).save(...)` → `podB.getBean(TaskRepository.class).findById(...)` → assert present. Либо через HTTP: `RestTemplate` на `http://localhost:{podA.port}/api/tasks` POST → `{podB.port}/api/tasks/{id}` GET.
  - e30: `podA.getBean(PgNotificationTransport.class).subscribe(convId)` → `podB.getBean(...).broadcast(convId, event)` → Awaitility `atMost(5, SECONDS)`.
  - `@Tag("scaling-e2e")` на классе.
- Проверить что `./mvnw test` (без профиля) **не** запускает этот класс.
- Запустить `./mvnw -pl javaclaw-e2e verify -Pscaling-e2e` — до зелёного.

### 7. Manual verification checklist

- **Task ID**: `gap7-manual-checklist-doc`
- **Depends On**: none
- **Assigned To**: `builder-manual-checklist`
- **Agent Type**: `builder`
- **Stack**: `java` (минимум — для context routing; контент — markdown)
- **Parallel**: true
- **Tests**: N/A (документация).
- Создать `docs/manual-verification-task-architecture.md`.
- Формат: `## S1 — Базовый чат\n- [ ] Шаг 1: ...\n- [ ] Шаг 2: ...\n- **Ожидаемо**: ...`.
- **Pre-step**: `grep -n '^### S' specs/task-architecture-spec.md` — верифицировать точное расположение S1–S20 (строки могут сдвинуться).
- Перенести S1–S20 из `specs/task-architecture-spec.md` + добавить S21 (E31 pod kill + SSE reconnect).
- Для каждого сценария — 2–5 шагов репродукции + 1 ожидаемый результат.
- Документ — шаблон, ручной прогон НЕ выполнять сейчас.

### 8. Write-tests regression aggregation

- **Task ID**: `write-tests`
- **Depends On**: `gap1-t50-approval-flow-it`, `gap2-t59-chat-audit-it`, `gap3-t56-pg-notify-it`, `gap4-e18-playwright-audit`, `gap5-e26e28-telegram-it`, `gap6-e29e30-scaling-it`, `gap7-manual-checklist-doc`
- **Assigned To**: `builder-t50-approval`
- **Agent Type**: `builder`
- **Stack**: `Java Spring Boot integration test Testcontainers mockito assertj allure test structure maven surefire React vite jest testing-library tsx`
- **Parallel**: false
- **Tests**: Агрегация — проверить что все 15 новых integration кейсов + 2 Playwright кейса зелёные в одном полном прогоне, без interference между тестами.
- Прогнать `./mvnw clean test` целиком (без scaling профиля) и убедиться что все 6 новых integration классов зелёные одновременно: `ApprovalFlowIntegrationTest`, `ChatAuditToolCallsIntegrationTest`, `PgNotificationTransportIntegrationTest`, `TelegramMultiChannelIntegrationTest`.
- Прогнать `./mvnw -pl javaclaw-e2e verify -Pscaling-e2e` — `ScalingIntegrationTest` зелёный.
- Прогнать `cd javaclaw-frontend && pnpm test && pnpm test:e2e task-advanced` — все зелёные.
- Если есть flaky — прогнать второй раз каждый падающий класс. Если всё ещё flaky — пометить `@Disabled` с TODO и эскалировать.
- Вернуть summary: `{integration: N passed, e2e: M passed, total backend: K tests}` для validate-all.

### 9. Final validation + spec/memory update

- **Task ID**: `validate-all`
- **Depends On**: `write-tests`
- **Assigned To**: `validator-final`
- **Agent Type**: `validator`
- **Stack**: `Java Spring Boot maven surefire failsafe jacoco integration test React vite`
- **Parallel**: false
- **Tests**: Прогон всего пайплайна, агрегация результатов.
- Запустить `./mvnw clean test` — ожидается BUILD SUCCESS, ~725+ зелёных тестов.
- Запустить `./mvnw -pl javaclaw-e2e verify -Pscaling-e2e` — ожидается 2 scaling теста зелёные.
- Запустить `cd javaclaw-frontend && pnpm test && pnpm test:e2e` — ожидается все frontend зелёные.
- Если всё зелёное: обновить `specs/task-architecture-spec.md` — добавить секцию в конец:

  ```markdown
  ## Status: Implemented 2026-04-10
  Gap closure — gnhf #50–#56 (T50, T59, T56, E18, E26–E28, E29–E30, manual checklist).
  All planned test scenarios covered except E31 (manual only).
  ```
- Обновить auto-memory `project_task_architecture.md`: пометить `**DONE (100%)**`.
- Не модифицировать код — только спеку и memory.
- Вернуть отчёт: количество новых тестов, общее количество зелёных, выявленные flaky, рекомендации.

## Acceptance Criteria

- [ ] 4 новых backend integration-класса созданы (T50, T59, T56, E26–E28) + все кейсы зелёные.
- [ ] `javaclaw-e2e/pom.xml` содержит профиль `scaling-e2e` с Failsafe конфигурацией.
- [ ] `ScalingIntegrationTest` с `@Tag("scaling-e2e")` создан, E29 + E30 зелёные при `mvn verify -Pscaling-e2e`.
- [ ] `ScalingIntegrationTest` **НЕ запускается** при обычном `./mvnw test` (верифицировать отсутствие в surefire report).
- [ ] `task-advanced.e2e.ts` расширен describe `E18 — chat audit viewer` с 2 кейсами, Playwright зелёный.
- [ ] `docs/manual-verification-task-architecture.md` создан с S1–S20 + S21 (E31).
- [ ] `./mvnw clean test` — BUILD SUCCESS, 725+ зелёных тестов (было 709, +~14–17).
- [ ] `specs/task-architecture-spec.md` содержит секцию `## Status: Implemented 2026-04-10`.
- [ ] Auto-memory `project_task_architecture.md` переведена в `**DONE (100%)**`.
- [ ] Нет регрессий: все 152 frontend unit-теста всё ещё зелёные.
- [ ] Нет flaky: каждый новый integration-тест прогнан локально минимум 2 раза без fail.

## Validation Commands

```bash
# Full backend green check (должен остаться зелёным)
./mvnw clean test

# P0 + P1 + E26-E28 — проверка конкретных integration тестов
./mvnw -pl javaclaw-app test \
  -Dtest='ApprovalFlowIntegrationTest,ChatAuditToolCallsIntegrationTest,TelegramMultiChannelIntegrationTest'
./mvnw -pl javaclaw-api/javaclaw-api-chat test \
  -Dtest='PgNotificationTransportIntegrationTest'

# P2 scaling — изолированный профиль (Docker required)
./mvnw -pl javaclaw-e2e verify -Pscaling-e2e

# Верификация что scaling тест НЕ запускается без профиля
./mvnw -pl javaclaw-e2e test | grep -c 'ScalingIntegrationTest'  # должен вернуть 0

# Frontend unit + E2E
cd javaclaw-frontend && pnpm test
cd javaclaw-frontend && pnpm test:e2e task-advanced

# Frequency check (нет flaky)
./mvnw -pl javaclaw-app test -Dtest=ApprovalFlowIntegrationTest
./mvnw -pl javaclaw-app test -Dtest=ApprovalFlowIntegrationTest  # второй раз

# Final: количество тестов
./mvnw test 2>&1 | grep 'Tests run:' | tail -1  # expected ~725+
```

## Notes

- **Telegram тест перенесён в `javaclaw-app`**, а не `javaclaw-e2e` — не нужны Playwright/Docker, только Spring + Testcontainers + mocked `TelegramClient`.
- **E31 (pod kill + SSE reconnect)** сознательно не автоматизирован — flaky по природе, покрыт через `DeliveryRecovery` unit/integration тесты (T54) и ручной чеклист.
- **T57 не включён** — закрыт унифицированным механизмом `DeliveryQueue` (документировано в Context разделе).
- **Нет новых Maven зависимостей** — Testcontainers, Awaitility, Mockito, Failsafe уже в проекте. Проверить наличие `org.testcontainers:compose` **не нужно** — DockerComposeContainer выкинут из плана.
- **Не редактировать `PgNotificationTransport.java`** если у него уже есть `@PreDestroy` / `close()` метод. Если нет — добавить минимальный cleanup hook (это единственное возможное изменение в production коде, остальное — чисто тесты).
- **Перед Gap 4 (E18)**: builder должен выполнить `grep` для verify endpoint URL перед написанием моков, иначе тест будет некорректным.
- **Порядок коммитов**: один gnhf-коммит на gap (7 коммитов), финальный коммит — validate-all + обновление спеки/memory.
- **Telegram нотификация**: при старте/завершении отправить через `.claude/hooks/utils/telegram_notify.py` если env доступны.

