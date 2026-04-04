# Plan: PostgreSQL Migration — Scalable MVP

## Task Description

Перевести JavaClaw с embedded H2 + файловой системы на PostgreSQL, чтобы обеспечить горизонтальное масштабирование (N инстансов за load balancer'ом). Включает: замену H2 на PostgreSQL, миграцию Chat Memory на JDBC, миграцию Tasks на Spring Data JDBC, рефакторинг Channel Routing (убрать JVM-local state), настройку JobRunr worker-count и docker-compose для локальной разработки.

## Objective

После выполнения плана JavaClaw может работать как N идентичных инстансов с общей PostgreSQL. Все state (чаты, задачи, фоновые джобы) хранится в БД, нет зависимостей от локальной файловой системы для persistence.

## Problem Statement

Текущая архитектура single-instance:
- **H2 embedded** — один инстанс может подключиться к файлу БД
- **FileSystemChatMemoryRepository** — YAML-файлы в `./workspace/conversations/`
- **FileSystemTaskRepository** — Markdown-файлы в `./workspace/tasks/`
- **ChannelRegistry.getLatestChannel()** — `AtomicReference<ChannelMessageReceivedEvent>` (JVM-local), при запуске задачи уведомление идёт в "последний активный канал" на этом инстансе, а не в канал-источник

Всё это блокирует горизонтальное масштабирование.

## Solution Approach

1. **Flyway** для версионированных миграций — безопасно при N инстансах (DB locking)
2. **Spring Data JDBC** для Tasks (уже в `app/build.gradle` как `spring-boot-starter-data-jdbc`)
3. **JdbcAppendableChatMemoryRepository** — кастомная реализация `AppendableChatMemoryRepository` поверх Spring AI JDBC starter, с нативным INSERT (без перезаписи всех сообщений)
4. **Channel name в Task payload** — убирает JVM-local зависимость
5. **Чистый старт** — без миграции данных из файловой системы
6. **Исследование thread-safety** ChannelRegistry в контексте JobRunr — отдельная задача

## Relevant Files

Используй эти файлы для выполнения задач:

### Gradle (зависимости)

- `app/build.gradle` — заменить `runtimeOnly 'com.h2database:h2'` на PostgreSQL, добавить Flyway
- `base/build.gradle` — убрать `runtimeOnly 'com.h2database:h2'`, добавить `spring-ai-starter-model-chat-memory-repository-jdbc`, добавить `spring-boot-starter-data-jdbc`

### Configuration

- `app/src/main/resources/application.yaml` — datasource → PostgreSQL, jobrunr worker-count, flyway config

### Chat Memory (Этап 2)

- `base/src/main/java/ai/javaclaw/agent/memory/FileSystemChatMemoryRepository.java` — убрать `@Component` (будет заменён)
- `base/src/main/java/org/springframework/ai/chat/memory/AppendableChatMemoryRepository.java` — интерфейс, остаётся
- `base/src/main/java/org/springframework/ai/chat/memory/MessageWindowChatMemory.java` — НЕ ТРОГАТЬ: `DelegatingAppendableChatMemoryRepository` уже корректно обрабатывает `AppendableChatMemoryRepository` через `instanceof` проверку в `appendAll()`. Наш JDBC-репозиторий будет распознан автоматически.

### Tasks (Этап 3)

- `base/src/main/java/ai/javaclaw/tasks/Task.java` — добавить `@Table`, `@Id`, поле `sourceChannelName`
- `base/src/main/java/ai/javaclaw/tasks/RecurringTask.java` — добавить `@Table`, `@Id`
- `base/src/main/java/ai/javaclaw/tasks/TaskRepository.java` — разделить на два Spring Data JDBC интерфейса
- `base/src/main/java/ai/javaclaw/tasks/FileSystemTaskRepository.java` — удалить
- `base/src/main/java/ai/javaclaw/tasks/TaskHandler.java` — заменить `getLatestChannel()` на `getChannel(task.getSourceChannelName())`
- `base/src/main/java/ai/javaclaw/tasks/TaskManager.java` — пробрасывать `channelName` при создании Task

### Channel Routing (Этап 4)

- `base/src/main/java/ai/javaclaw/tools/TaskTool.java` — при создании задачи передавать текущий channelName
- `base/src/main/java/ai/javaclaw/channels/ChannelRegistry.java` — добавить `getChannel(String name)`, deprecated `publishMessageReceivedEvent()`, убрать `AtomicReference` и `getLatestChannel()`
- `base/src/main/java/ai/javaclaw/tasks/RecurringTaskHandler.java` — адаптировать под `RecurringTaskRepository`
- `app/src/main/java/ai/javaclaw/chat/ChatChannel.java` — вызывает `publishMessageReceivedEvent()` (останется deprecated)
- `plugins/discord/src/main/java/ai/javaclaw/channels/discord/DiscordChannel.java` — вызывает `publishMessageReceivedEvent()` (останется deprecated)
- `plugins/telegram/src/main/java/ai/javaclaw/channels/telegram/TelegramChannel.java` — вызывает `publishMessageReceivedEvent()` (останется deprecated)

### New Files

- `base/src/main/resources/db/migration/V1__init_tasks.sql` — Flyway: таблицы tasks, recurring_tasks
- `base/src/main/resources/db/migration/V2__init_chat_memory.sql` — Flyway: таблица ai_chat_memory (если не создаётся автоматически Spring AI JDBC starter'ом)
- `base/src/main/java/ai/javaclaw/agent/memory/JdbcAppendableChatMemoryRepository.java` — кастомная реализация AppendableChatMemoryRepository поверх JdbcChatMemoryRepository
- `docker-compose.dev.yml` — PostgreSQL для локальной разработки
- `base/src/test/java/ai/javaclaw/tasks/TaskRepositoryTest.java` — интеграционный тест
- `base/src/test/java/ai/javaclaw/tasks/TaskManagerTest.java` — unit тест
- `base/src/test/java/ai/javaclaw/agent/memory/JdbcAppendableChatMemoryRepositoryTest.java` — интеграционный тест
- `base/src/test/java/ai/javaclaw/channels/ChannelRegistryTest.java` — unit тест
- `app/src/test/resources/application-openrouter.yaml` — тестовый профиль для OpenRouter (base-url, api-key, model)
- `app/src/test/java/ai/javaclaw/live/LiveTestBase.java` — базовый класс для live-тестов (SpringBootTest + Testcontainers + OpenRouter)
- `app/src/test/java/ai/javaclaw/live/AgentLiveTest.java` — live-тест: Agent.respondTo() с реальной LLM
- `app/src/test/java/ai/javaclaw/live/TaskLiveTest.java` — live-тест: создание и выполнение задач с реальной LLM
- `app/src/test/java/ai/javaclaw/live/ChatMemoryLiveTest.java` — live-тест: persistence chat memory через рестарт
- `app/src/test/java/ai/javaclaw/e2e/E2ETestBase.java` — базовый класс для E2E (Playwright + SpringBootTest + Testcontainers)
- `app/src/test/java/ai/javaclaw/e2e/OnboardingE2ETest.java` — E2E: полный onboarding flow через браузер
- `app/src/test/java/ai/javaclaw/e2e/ChatE2ETest.java` — E2E: отправка сообщения, получение ответа, persistence
- `app/src/test/java/ai/javaclaw/e2e/ConversationSwitchingE2ETest.java` — E2E: переключение между диалогами
- `app/src/test/java/ai/javaclaw/e2e/TaskCreationE2ETest.java` — E2E: создание задачи через чат, уведомление
- `app/src/test/java/ai/javaclaw/e2e/MultiInstanceE2ETest.java` — E2E: 2 инстанса, общая БД, распределение задач

## Implementation Phases

### Phase 1: Foundation (Этапы 1 + 5 из ТЗ)

- PostgreSQL вместо H2 в Gradle и application.yaml
- Flyway для миграций
- Docker Compose для PostgreSQL
- JobRunr worker-count через env var

### Phase 2: Core Implementation (Этапы 2 + 3 + 4)

- JdbcAppendableChatMemoryRepository
- Spring Data JDBC для Tasks и RecurringTask
- Channel name в Task payload
- Рефакторинг ChannelRegistry

### Phase 3: Integration & Polish

- Тесты (unit + integration с Testcontainers)
- Исследование thread-safety ChannelRegistry + JobRunr
- Финальная валидация с 2 инстансами

## Team Orchestration

- You operate as the team lead and orchestrate the team to execute the plan.
- You're responsible for deploying the right team members with the right context to execute the plan.
- IMPORTANT: You NEVER operate directly on the codebase. You use `Task` and `Task*` tools to deploy team members to to the building, validating, testing, deploying, and other tasks.
  - This is critical. You're job is to act as a high level director of the team, not a builder.
  - You're role is to validate all work is going well and make sure the team is on track to complete the plan.
  - You'll orchestrate this by using the Task* Tools to manage coordination between the team members.
  - Communication is paramount. You'll use the Task* Tools to communicate with the team members and ensure they're on track to complete the plan.
- Take note of the session id of each team member. This is how you'll reference them.

### Team Members

- Builder
  - Name: builder-foundation
  - Role: Настройка PostgreSQL, Flyway, docker-compose, build.gradle и application.yaml
  - Agent Type: builder
  - Resume: true
- Builder
  - Name: builder-chat-memory
  - Role: Реализация JdbcAppendableChatMemoryRepository и миграция Chat Memory
  - Agent Type: builder
  - Resume: true
- Builder
  - Name: builder-tasks-jdbc
  - Role: Миграция Tasks/RecurringTask на Spring Data JDBC + Channel Routing
  - Agent Type: builder
  - Resume: true
- Builder
  - Name: builder-tests
  - Role: Написание unit и интеграционных тестов
  - Agent Type: builder
  - Resume: true
- Builder
  - Name: researcher-jobrunr
  - Role: Исследование thread-safety ChannelRegistry в контексте JobRunr worker threads
  - Agent Type: general-purpose
  - Resume: false
- Builder
  - Name: builder-live-tests
  - Role: Интеграционные тесты с реальной LLM через OpenRouter (AgentLiveTest, TaskLiveTest, ChatMemoryLiveTest)
  - Agent Type: builder
  - Resume: true
- Builder
  - Name: builder-e2e-tests
  - Role: Acceptance E2E тесты через Playwright (OnboardingE2ETest, ChatE2ETest, TaskCreationE2ETest, MultiInstanceE2ETest)
  - Agent Type: builder
  - Resume: true
- Validator
  - Name: validator-final
  - Role: Финальная валидация — компиляция, все тесты (unit + integration + live + e2e), проверка критериев приёмки
  - Agent Type: validator
  - Resume: false

## Testing Strategy

Test pyramid ratio: **80% unit / 15% integration-API / 5% UI e2e**

### Unit Tests (80%)

- `TaskManagerTest` — create(), schedule(), scheduleRecurrently(), cancelRecurring(), getRecentTasks()
- `ChannelRegistryTest` — registerChannel(), unregisterChannel(), getChannel(name), fallback to default
- `TaskTest` — newTask(), withStatus(), withFeedback(), withSourceChannelName()
- `RecurringTaskTest` — newRecurringTask(), getters

### Integration / API Tests (15%)

- `TaskRepositoryTest` — CRUD с Testcontainers PostgreSQL, findByCreatedAtBetweenAndStatus()
- `RecurringTaskRepositoryTest` — CRUD с Testcontainers PostgreSQL
- `JdbcAppendableChatMemoryRepositoryTest` — appendAll(), findByConversationId(), saveAll(), deleteByConversationId() с Testcontainers PostgreSQL
- `FlywayMigrationTest` — проверка что все миграции применяются корректно на чистую БД

### Integration Tests с реальной LLM (OpenRouter)

Требуют env var `OPENROUTER_API_KEY` (уже в zshrc). Помечаются `@EnabledIfEnvironmentVariable(named = "OPENROUTER_API_KEY")`.
OpenRouter подключается через OpenAI provider с `spring.ai.openai.base-url=https://openrouter.ai/api/v1`.

- `AgentLiveTest` (@SpringBootTest + Testcontainers PostgreSQL + OpenRouter):
  - `respondTo()` — отправить реальный промпт, получить непустой ответ
  - Chat memory persistence — отправить сообщение, проверить что оно сохранилось в `ai_chat_memory` таблице PostgreSQL
  - Контекст сохраняется — отправить 2 сообщения, во втором сослаться на первое, проверить что ответ релевантен
- `TaskLiveTest` (@SpringBootTest + Testcontainers PostgreSQL + OpenRouter):
  - Создание задачи через TaskManager — задача появляется в таблице `tasks` с правильным статусом
  - Выполнение задачи через TaskHandler — JobRunr выполняет задачу, статус меняется на `completed` или `awaiting_human_input`
  - sourceChannelName сохраняется — задача созданная с channelName="test-channel" имеет его в БД
- `RecurringTaskLiveTest` (@SpringBootTest + Testcontainers PostgreSQL + OpenRouter):
  - Создание recurring task — появляется в `recurring_tasks` таблице
  - Выполнение по расписанию — задача создаётся и выполняется (с коротким cron для теста)
- `ChatMemoryLiveTest` (@SpringBootTest + Testcontainers PostgreSQL + OpenRouter):
  - Полный цикл: отправить сообщение через Agent.respondTo() -> проверить память в БД -> рестартнуть ApplicationContext -> проверить что память сохранилась
  - Несколько conversationId — сообщения из разных каналов не пересекаются

### UI E2E / Acceptance Tests (Playwright)

Полные end-to-end тесты через браузер. Требуют запущенное приложение + PostgreSQL + `OPENROUTER_API_KEY`.

- `OnboardingE2ETest` (Playwright + @SpringBootTest):
  - Открыть http://localhost:8080 -> редирект на /onboarding
  - Пройти все шаги: Welcome -> Provider (выбрать OpenAI) -> Credentials (ввести OpenRouter API key + base-url) -> Agent name -> MCP (skip) -> Complete
  - Проверить редирект на /chat после завершения
- `ChatE2ETest` (Playwright + @SpringBootTest, onboarding уже пройден):
  - Открыть /chat -> WebSocket подключается
  - Отправить сообщение через input -> увидеть user bubble
  - Дождаться ответа агента -> увидеть agent bubble с непустым текстом
  - Отправить второе сообщение, ссылающееся на первый ответ -> проверить контекстуальный ответ
  - Перезагрузить страницу -> история чата сохранилась (bubbles на месте)
- `ConversationSwitchingE2ETest` (Playwright + @SpringBootTest):
  - Открыть /chat -> отправить сообщение в дефолтном канале "web"
  - Переключиться на другой conversationId (если есть selector)
  - Переключиться обратно -> предыдущие сообщения на месте
- `TaskCreationE2ETest` (Playwright + @SpringBootTest):
  - Открыть /chat -> попросить агента "создай задачу: напиши короткий стих"
  - Дождаться ответа агента с подтверждением создания задачи
  - Проверить в БД что задача появилась в таблице `tasks`
  - Дождаться уведомления в чате о завершении задачи (или таймаут)
- `MultiInstanceE2ETest` (Playwright + docker-compose с 2 инстансами + PostgreSQL):
  - Запустить 2 инстанса через docker-compose
  - Открыть Instance 1 в браузере -> отправить сообщение -> получить ответ
  - Открыть Instance 2 в браузере -> история чата видна (та же PostgreSQL)
  - Создать задачу на Instance 1 -> проверить в JobRunr dashboard что задачи распределяются между инстансами

## Step by Step Tasks

- IMPORTANT: Execute every step in order, top to bottom. Each task maps directly to a `TaskCreate` call.
- Before you start, run `TaskCreate` to create the initial task list that all team members can see and execute.

### 1. Setup PostgreSQL + Flyway + Docker Compose

- **Task ID**: setup-postgresql
- **Depends On**: none
- **Assigned To**: builder-foundation
- **Agent Type**: builder
- **Stack**: Java Spring Boot JPA lombok spring datasource postgresql flyway gradle
- **Parallel**: true
- **Tests**: Unit: нет. Integration: FlywayMigrationTest — все миграции применяются на чистой БД.
- В `app/build.gradle`: заменить `runtimeOnly 'com.h2database:h2'` на `runtimeOnly 'org.postgresql:postgresql'`, добавить `implementation 'org.flywaydb:flyway-database-postgresql'`
- В `base/build.gradle`: убрать `runtimeOnly 'com.h2database:h2'`, добавить `implementation 'org.springframework.boot:spring-boot-starter-data-jdbc'` (нужен для `@Table`/`@Id` аннотаций в base-модуле)
- В `app/src/main/resources/application.yaml`: обновить datasource на PostgreSQL с env vars (`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`), добавить `spring.flyway.enabled: true`, обновить jobrunr `worker-count: ${JOBRUNR_WORKER_COUNT:10}`, `dashboard.port: ${JOBRUNR_DASHBOARD_PORT:8081}`
- Создать `docker-compose.dev.yml` с PostgreSQL 17 (порт 5432, база javaclaw)
- Создать `base/src/main/resources/db/migration/V1__init_tasks.sql` с таблицами `tasks` и `recurring_tasks` + индексами
- Убедиться что `spring-boot-starter-data-jdbc` уже подключён в `app/build.gradle` (да, есть)
- Проверить компиляцию: `./gradlew compileJava`

### 2. Implement JdbcAppendableChatMemoryRepository

- **Task ID**: chat-memory-jdbc
- **Depends On**: setup-postgresql
- **Assigned To**: builder-chat-memory
- **Agent Type**: builder
- **Stack**: Java Spring Boot spring-ai JPA entity record jdbc chat memory repository
- **Parallel**: false
- **Tests**: Integration: JdbcAppendableChatMemoryRepositoryTest — appendAll, findByConversationId, saveAll, deleteByConversationId с Testcontainers PostgreSQL.
- В `base/build.gradle`: добавить `implementation 'org.springframework.ai:spring-ai-starter-model-chat-memory-repository-jdbc'`
- Проверить через Context7 какие таблицы создаёт `spring-ai-starter-model-chat-memory-repository-jdbc` автоматически. Если нет — создать Flyway миграцию `V2__init_chat_memory.sql`
- Создать `base/src/main/java/ai/javaclaw/agent/memory/JdbcAppendableChatMemoryRepository.java`:
  - Реализует `AppendableChatMemoryRepository`
  - Оборачивает `JdbcChatMemoryRepository` из Spring AI
  - `appendAll()` — нативный INSERT (прочитать текущие, добавить новые, saveAll). Или если JdbcChatMemoryRepository поддерживает — делегировать
  - Остальные методы делегируются
  - Пометить `@Component`
- В `FileSystemChatMemoryRepository.java`: убрать `@Component` (или удалить файл)
- НЕ трогать `MessageWindowChatMemory` — `DelegatingAppendableChatMemoryRepository` уже корректно обрабатывает `AppendableChatMemoryRepository` через `instanceof` проверку. Наш JDBC-репозиторий будет автоматически распознан как `AppendableChatMemoryRepository` и `appendAll()` будет вызываться напрямую.
- Проверить компиляцию: `./gradlew compileJava`

### 3. Migrate Tasks to Spring Data JDBC

- **Task ID**: tasks-spring-data
- **Depends On**: setup-postgresql
- **Assigned To**: builder-tasks-jdbc
- **Agent Type**: builder
- **Stack**: Java Spring Boot spring entity JPA record spring-data-jdbc repository table id
- **Parallel**: true (параллельно с chat-memory-jdbc)
- **Tests**: Unit: TaskTest — newTask, withStatus, withSourceChannelName. Integration: TaskRepositoryTest, RecurringTaskRepositoryTest с Testcontainers.
- Модифицировать `Task.java`:
  - Добавить `@org.springframework.data.relational.core.mapping.Table("tasks")`
  - Добавить `@org.springframework.data.annotation.Id` на поле `id`
  - Изменить тип `id` с `String` на `String` (UUID как строка)
  - Добавить поле `private final String sourceChannelName` (для Этапа 4)
  - Обновить конструктор, `newTask()`, `withStatus()`, `withFeedback()`
  - Добавить `withSourceChannelName(String channelName)`
- Модифицировать `RecurringTask.java`:
  - Добавить `@Table("recurring_tasks")`, `@Id` на поле `id`
- Создать новый интерфейс `TaskRepository extends ListCrudRepository<Task, String>`:
  - `List<Task> findByCreatedAtBetweenAndStatus(Instant from, Instant to, Task.Status status)`
  - `List<Task> findByCreatedAtBetween(Instant from, Instant to)`
  - ВАЖНО: текущий `TaskRepository` — это интерфейс с методами `save`, `getTaskById`, `getTasks`, `save(RecurringTask)`, etc. Нужно разделить на два Spring Data интерфейса: `TaskRepository extends ListCrudRepository<Task, String>` и `RecurringTaskRepository extends ListCrudRepository<RecurringTask, String>`
- Удалить `FileSystemTaskRepository.java`
- Адаптировать `TaskManager.java`:
  - Заменить `taskRepository.getTaskById(id)` на `taskRepository.findById(id).orElseThrow()`
  - Заменить `taskRepository.getTasks(date, status)` на `taskRepository.findByCreatedAtBetweenAndStatus(...)`
  - Добавить зависимость на `RecurringTaskRepository`
  - Обновить методы работы с recurring tasks
- Адаптировать `TaskHandler.java`:
  - Заменить `taskRepository.getTaskById(taskId)` на `taskRepository.findById(taskId).orElseThrow()`
  - (Channel routing адаптация — в следующем таске)
- Адаптировать `RecurringTaskHandler.java` (`base/src/main/java/ai/javaclaw/tasks/RecurringTaskHandler.java`):
  - Инжектировать `RecurringTaskRepository` вместо использования единого `TaskRepository`
  - Заменить `taskRepository.getRecurringTaskById(recurringTaskId)` на `recurringTaskRepository.findById(recurringTaskId).orElseThrow()`
- **Task ID Generation** — обеспечить генерацию UUID для новых записей:
  - В V1 миграции: `id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()` для обеих таблиц
  - Создать `BeforeConvertCallback<Task>` (и аналогичный для `RecurringTask`) который устанавливает `UUID.randomUUID().toString()` если `id == null` — Spring Data JDBC не использует DB defaults для INSERT
- Проверить компиляцию: `./gradlew compileJava`

### 4. Implement Channel Routing via Task Payload

- **Task ID**: channel-routing
- **Depends On**: tasks-spring-data
- **Assigned To**: builder-tasks-jdbc
- **Agent Type**: builder
- **Stack**: Java Spring Boot controller service channel registry event
- **Parallel**: false
- **Tests**: Unit: ChannelRegistryTest — getChannel(name) lookup, fallback to default.
- В `ChannelRegistry.java`:
  - Добавить метод `public Channel getChannel(String name)` — lookup по `channels` map, fallback на `defaultChannelName` если не найден
  - **Не удалять** `publishMessageReceivedEvent()` сразу — он используется 3 каналами. Вместо этого: оставить метод но пометить `@Deprecated`, удалить `AtomicReference` и `getLatestChannel()` только. Каналы продолжают вызывать `publishMessageReceivedEvent()` (no-op или логирование), удалим в следующей итерации.
  - Удалить `getLatestChannel()` — единственный вызов в `TaskHandler.java`
- Адаптировать вызовы `publishMessageReceivedEvent()` в 3 каналах:
  - `app/src/main/java/ai/javaclaw/chat/ChatChannel.java:129` — оставить вызов (deprecated, no-op)
  - `plugins/discord/src/main/java/ai/javaclaw/channels/discord/DiscordChannel.java:59` — оставить вызов (deprecated, no-op)
  - `plugins/telegram/src/main/java/ai/javaclaw/channels/telegram/TelegramChannel.java:71` — оставить вызов (deprecated, no-op)
- `TaskHandler.java:56`: заменить `channelRegistry.getLatestChannel()` на `channelRegistry.getChannel(task.getSourceChannelName())`
- **Передача channelName в Task** — архитектурное решение:
  - Каждый канал (ChatChannel, DiscordChannel, TelegramChannel) уже вызывает `agent.respondTo(conversationId, message)` после `publishMessageReceivedEvent()`
  - `conversationId` содержит имя канала (e.g., `"web"`, `"telegram-123456789"`, `"discord-456"`)
  - **Подход**: извлекать channelName из conversationId в `TaskManager.create()`. ConversationId формируется как `channelName` или `channelName-suffix`. Добавить в `TaskManager` метод для извлечения канала из conversationId, или передавать channelName явно через `TaskTool`.
  - **Конкретная реализация**: добавить параметр `sourceChannelName` в `TaskTool.createTask()` (LLM будет передавать имя канала из контекста), и пробросить через `TaskManager.create(name, description, sourceChannelName)` → `Task.newTask(name, description, sourceChannelName)`
- Добавить колонку `source_channel_name VARCHAR(50)` в миграцию V1 (уже должна быть там, т.к. Task уже с этим полем)
- Проверить компиляцию: `./gradlew compileJava`

### 5. Research JobRunr Thread-Safety for ChannelRegistry

- **Task ID**: research-thread-safety
- **Depends On**: none
- **Assigned To**: researcher-jobrunr
- **Agent Type**: general-purpose
- **Stack**: Java Spring Boot JobRunr thread concurrency
- **Parallel**: true (запускается параллельно с остальным)
- **Tests**: нет (исследовательская задача)
- Исследовать модель потоков JobRunr:
  - Как JobRunr worker threads создаются и управляются
  - В какие моменты `ChannelRegistry.getChannel()` вызывается из worker thread
  - Может ли `registerChannel()` вызываться после старта воркеров (hot-plug каналов)
  - Безопасен ли текущий HashMap при read-only после инициализации
- Проверить через Context7 документацию JobRunr
- Вывод: рекомендация — менять HashMap на ConcurrentHashMap или оставить как есть
- Записать результат в файл `specs/research-jobrunr-thread-safety.md`

### 6. Write Tests

- **Task ID**: write-tests
- **Depends On**: chat-memory-jdbc, tasks-spring-data, channel-routing
- **Assigned To**: builder-tests
- **Agent Type**: builder
- **Stack**: Java MockMvc Mockito assertj allure test structure testcontainers integration test repository test database test
- **Parallel**: false
- Написать unit тесты (80%):
  - `TaskManagerTest` — create(), schedule(), scheduleRecurrently() с мок-репозиториями (Mockito)
  - `ChannelRegistryTest` — registerChannel(), getChannel(name), getChannel(unknown) → fallback
  - `TaskTest` — newTask(), withStatus(), withFeedback(), withSourceChannelName()
- Написать интеграционные тесты (15%):
  - `TaskRepositoryTest` — @DataJdbcTest + Testcontainers PostgreSQL: save, findById, findByCreatedAtBetweenAndStatus
  - `RecurringTaskRepositoryTest` — @DataJdbcTest + Testcontainers PostgreSQL: save, findAll, deleteById
  - `JdbcAppendableChatMemoryRepositoryTest` — Testcontainers PostgreSQL: appendAll сохраняет новые сообщения, findByConversationId возвращает полную историю, deleteByConversationId удаляет
- Использовать существующий `TestcontainersConfiguration.java` для конфигурации PostgreSQL контейнера
- Следовать паттернам из существующих тестов (`ChatChannelTest`, `OnboardingControllerTest`)
- Запустить тесты: `./gradlew test`

### 7. Write Live Integration Tests (OpenRouter)

- **Task ID**: write-live-tests
- **Depends On**: write-tests
- **Assigned To**: builder-live-tests
- **Agent Type**: builder
- **Stack**: Java Spring Boot testcontainers integration test assertj spring-ai openai postgresql
- **Parallel**: false
- **Tests**: Сами тес��ы являются результатом этой задач��.
- Настроить тестовый профиль для OpenRouter:
  - Создать `app/src/test/resources/application-openrouter.yaml` с:

    ```yaml
    spring.ai.openai.base-url: https://openrouter.ai/api/v1
    spring.ai.openai.api-key: ${OPENROUTER_API_KEY}
    spring.ai.openai.chat.options.model: google/gemini-2.0-flash-001
    agent.onboarding.completed: true
    ```
  - Все live-тесты помечать `@ActiveProfiles("openrouter")` и `@EnabledIfEnvironmentVariable(named = "OPENROUTER_API_KEY", matches = ".+")`
- Создать базовый класс `LiveTestBase` (@SpringBootTest + @Testcontainers PostgreSQL + общая настройка)
- Написать `AgentLiveTest`:
  - `respondToReturnsNonEmptyAnswer()` — Agent.respondTo("test-conv", "What is 2+2?") возв��ащает непустой ответ
  - `chatMemoryPersistsAcrossQuestions()` — отправить "My name is TestBot", затем "What is my name?" — ответ содержит "TestBot"
  - `chatMemorySavedInPostgreSQL()` — после respondTo() проверить через JdbcTemplate что таблица ai_chat_memory содержит записи для conversationId
- Написать `TaskLiveTest`:
  - `taskCreatedAndPersistedInDB()` — TaskManager.create("test-task", "describe something") -> проверить через TaskRepository.findById() что задача существует
  - `taskExecutedByJobRunr()` — создать задачу, дождаться (Awaitility, до 30с) смены статуса на completed/awaiting_human_input
  - `sourceChannelNamePersisted()` — создать задачу с sourceChannelName="test-channel", проверить что сохранилось
- Написать `ChatMemoryLiveTest`:
  - `messagesFromDifferentConversationsDoNotMix()` — отправить сообщения в conv-1 и conv-2, проверить что findByConversationId возвращает только свои
  - `memoryPersistsAfterContextRestart()` — отправить с��общение, перезапустить ApplicationContext (DirtiesContext), проверить что память на месте
- Запустить: `OPENROUTER_API_KEY=$OPENROUTER_API_KEY ./gradlew test`

### 8. Write E2E Acceptance Tests (Playwright)

- **Task ID**: write-e2e-tests
- **Depends On**: write-live-tests
- **Assigned To**: builder-e2e-tests
- **Agent Type**: builder
- **Stack**: Java Spring Boot selenide e2e page object testcontainers playwright
- **Parallel**: false
- **Tests**: Сами тесты являются результатом этой задачи.
- Добавить зависимость Playwright для Java в `app/build.gradle`:
  - `testImplementation 'com.microsoft.playwright:playwright:1.52.0'` (проверить актуальную версию через Context7)
- Все E2E тесты помечать `@EnabledIfEnvironmentVariable(named = "OPENROUTER_API_KEY", matches = ".+")` и `@Tag("e2e")`
- Соз��ать базовый класс `E2ETestBase`:
  - @SpringBootTest(webEnvironment = RANDOM_PORT) + Testcontainers PostgreSQL + @ActiveProfiles("openrouter")
  - @BeforeAll: инициализ��ция Playwright, создание Browser (Chromium, headless)
  - @BeforeEach: новый BrowserContext + Page, навигация на `http://localhost:${port}`
  - @AfterAll: закрытие Playwright
- Написать `OnboardingE2ETest`:
  - `completeOnboardingFlow()` — открыть / -> редирект на /onboarding -> нажать "Get Started" -> выбрать "OpenAI" provider -> ввести API key (OPENROUTER_API_KEY) + base-url (https://openrouter.ai/api/v1) -> ввести model (google/gemini-2.0-flash-001) -> ввести имя агента -> Skip MCP -> Complete -> проверить редирект на /chat
- Написать `ChatE2ETest` (onboarding предварительно пройден через application-openrouter.yaml с `agent.onboarding.completed: true`):
  - `sendMessageAndReceiveResponse()` — дождаться WebSocket connect -> ввести "Hello, what is your name?" в input -> нажать Send -> дождаться появления user bubble -> дождаться появления agent bubble (не пустой, таймаут 30с)
  - `chatHistoryPersistsOnReload()` — отправить сообщение -> получить ответ -> page.reload() -> дождаться загрузки -> проверить что оба bubble (user + agent) на месте
  - `contextualResponseInConversation()` — отправить "Remember the number 42" -> получить ответ -> отправить "What number did I ask you to remember?" -> проверить что ответ содержит "42"
- Написать `ConversationSwitchingE2ETest`:
  - `switchBetweenConversationsPreservesHistory()` — отправить сообщение в "web" -> проверить bubble -> если есть channel-selector: переключить conversationId -> переключить обратно -> bubbles на месте
- Написать `TaskCreationE2ETest`:
  - `agentCreatesTaskOnRequest()` — отправить "Please create a task called 'test-task' to write a haiku about spring" -> дождаться ответа агента с подтверждением -> проверить через JdbcTemplate что задача есть в таблице `tasks`
  - `taskCompletionNotificationAppearsInChat()` — после создания задачи дожд��ться (Awaitility, до 60с) появления нового agent bubble с уведомлением о завершении задачи
- Написать `MultiInstanceE2ETest` (требует docker-compose):
  - `chatVisibleAcrossInstances()` — поднять 2 Spring Boot инстанса на разных портах (через @SpringBootTest двойной или TestPropertySource) с общей Testcontainers PostgreSQL -> отправить сообщение на Instance 1 -> проверить что на Instance 2 через API/direct query данные видны в chat_memory
  - `tasksDistributedBetweenInstances()` — создать 3+ задачи -> проверить через JobRunr StorageProvider что задачи обрабатываются разными воркерами (разные server IDs)
- Запустить: `OPENROUTER_API_KEY=$OPENROUTER_API_KEY ./gradlew test --tests '*E2E*'`

### 9. Final Validation

- **Task ID**: validate-all
- **Depends On**: setup-postgresql, chat-memory-jdbc, tasks-spring-data, channel-routing, write-tests, write-live-tests, write-e2e-tests
- **Assigned To**: validator-final
- **Agent Type**: validator
- **Stack**: Java Spring Boot spring-data-jdbc postgresql flyway testcontainers integration test MockMvc assertj selenide e2e playwright
- **Parallel**: false
- Запустить `./gradlew compileJava` — компиляция без ошибок
- Запустить `./gradlew test` — все unit + integration тесты проходят
- Запустить `OPENROUTER_API_KEY=$OPENROUTER_API_KEY ./gradlew test` — live + e2e тесты проходят (при наличии ключа)
- Проверить что нет зависимости от H2: `grep -r 'h2database\|com.h2' --include='*.gradle' --include='*.yaml' --include='*.properties'` должен вернуть пусто (кроме тестов если нужно)
- Проверить что FileSystemTaskRepository удалён
- Проверить что FileSystemChatMemoryRepository не помечен @Component
- Проверить что ChannelRegistry не содержит AtomicReference и getLatestChannel()
- Проверить что Task содержит поле sourceChannelName
- Проверить Flyway миграции: файлы V1, V2 (если нужен) существуют в `base/src/main/resources/db/migration/`
- Проверить docker-compose.dev.yml существует и валиден
- Проверить что live-тесты skip'аются без OPENROUTER_API_KEY: `./gradlew test` без env var — все skip, остальные зелёные
- Проверить что e2e тесты помечены @Tag("e2e") и @EnabledIfEnvironmentVariable

## Acceptance Criteria

1. **Приложение компилируется** — `./gradlew compileJava` без ошибок
2. **Unit + Integration тесты проходят** — `./gradlew test` без OPENROUTER_API_KEY зелёный (live/e2e skip)
3. **Нет H2 в рантайме** — зависимость `com.h2database:h2` убрана из `base/build.gradle` и `app/build.gradle` (runtimeOnly)
4. **Chat memory в БД** — JdbcAppendableChatMemoryRepository реализует AppendableChatMemoryRepository, помечен @Component
5. **Tasks в БД** — TaskRepository extends ListCrudRepository, RecurringTaskRepository extends ListCrudRepository
6. **FileSystemTaskRepository удалён**
7. **Channel routing через payload** — Task содержит sourceChannelName, TaskHandler использует getChannel(name)
8. **ChannelRegistry without JVM-local state** -- no AtomicReference, no getLatestChannel()
9. **Flyway миграции** — V1__init_tasks.sql создаёт таблицы tasks и recurring_tasks
10. **Docker Compose** — docker-compose.dev.yml с PostgreSQL 17
11. **JobRunr worker-count** — конфигурируемый через env var
12. **Live тесты с OpenRouter** — AgentLiveTest, TaskLiveTest, ChatMemoryLiveTest проходят с OPENROUTER_API_KEY, skip без ключа
13. **E2E Acceptance тесты** — OnboardingE2ETest, ChatE2ETest, ConversationSwitchingE2ETest, TaskCreationE2ETest, MultiInstanceE2ETest проходят через Playwright с реальной LLM
14. **Тесты не ломают CI** — без OPENROUTER_API_KEY все live/e2e тесты gracefully skip через @EnabledIfEnvironmentVariable

## Validation Commands

Execute these commands to validate the task is complete:

- `./gradlew compileJava` — компиляция без ошибок
- `./gradlew test` — все тесты проходят
- `grep -r 'h2database' --include='*.gradle' base/ app/` — должен быть пустым (только test scope допустим)
- `find base/src/main/java -name 'FileSystemTaskRepository.java'` — файл не существует
- `grep -r 'getLatestChannel\|lastChannelMessage' base/src/main/java/` — не найдено
- `grep -r 'sourceChannelName' base/src/main/java/ai/javaclaw/tasks/Task.java` — найдено
- `find base/src/main/resources/db/migration -name '*.sql'` — V1 существует
- `docker compose -f docker-compose.dev.yml config` — валидный YAML
- `OPENROUTER_API_KEY=$OPENROUTER_API_KEY ./gradlew test` — все тесты включая live и e2e прохо��ят
- `./gradlew test` (без OPENROUTER_API_KEY) — live/e2e тесты skip, остальные зелёные
- `./gradlew test --tests '*E2E*'` — только e2e тесты (при наличии ключа)

## Notes

- **Spring AI JDBC Chat Memory**: проверить через Context7 актуальную документацию `spring-ai-starter-model-chat-memory-repository-jdbc` — какие таблицы создаются, нужна ли отдельная Flyway миграция или starter автоматически создаёт
- **Task.id генерация**: решено — `DEFAULT gen_random_uuid()` в V1 миграции + `BeforeConvertCallback<Task>` с `UUID.randomUUID().toString()` когда `id == null` (Spring Data JDBC не использует DB defaults)
- **H2 для тестов**: можно оставить H2 как testRuntimeOnly для быстрых unit-тестов, но интеграционные должны быть с Testcontainers PostgreSQL
- **MessageWindowChatMemory**: НЕ модифицировать — `DelegatingAppendableChatMemoryRepository.appendAll()` уже содержит `instanceof AppendableChatMemoryRepository` проверку и корректно делегирует
- **publishMessageReceivedEvent()**: помечаем `@Deprecated` но не удаляем — 3 канала используют. Удалим в следующей итерации вместе с полным рефакторингом каналов
- **spring-boot-starter-data-jdbc**: нужен в `base/build.gradle` (не только в `app/`) — аннотации `@Table`/`@Id` определены в `spring-data-relational` который тянется через этот starter
- **Зависимости для добавления**:
  - `base/build.gradle`: `implementation 'org.springframework.ai:spring-ai-starter-model-chat-memory-repository-jdbc'`, `implementation 'org.springframework.boot:spring-boot-starter-data-jdbc'`
  - `app/build.gradle`: `runtimeOnly 'org.postgresql:postgresql'`, `implementation 'org.flywaydb:flyway-database-postgresql'`
- **Исследование thread-safety** — результат запишется в `specs/research-jobrunr-thread-safety.md` и будет учтён при принятии решения о ConcurrentHashMap
- **OpenRouter для тестов**: `OPENROUTER_API_KEY` уже в zshrc. OpenRouter подключается через OpenAI provider с `spring.ai.openai.base-url=https://openrouter.ai/api/v1`. Использовать дешёвую модель для CI (e.g., `google/gemini-2.0-flash-001`).
- **Playwright Java**: добавить `testImplementation 'com.microsoft.playwright:playwright:1.52.0'` в `app/build.gradle`. Проверить ��ктуальную версию через Context7. Playwright требует установки браузеров: `mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install chromium"` или через Gradle task.
- **E2E тест стратегия**: WebSocket-based UI (htmx) — Playwright подключается к localhost:{randomPort}, ждёт WebSocket messages. Для проверки chat bubbles использовать `page.locator("#chat-messages")` и ждать появления элементов.
- **Multi-instance тест**: можно реализовать через 2 `SpringApplication.run()` на разных портах с общей Testcontainers PostgreSQL, без docker-compose. Проще и быстрее для CI.
- **Зависимости для тестов**:
  - `app/build.gradle`: `testImplementation 'com.microsoft.playwright:playwright:1.52.0'`, `testImplementation 'org.awaitility:awaitility'` (для ожидания задач)

