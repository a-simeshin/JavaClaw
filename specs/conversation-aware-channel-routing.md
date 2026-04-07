# Plan: Conversation-Aware Channel Routing

## Task Description

Рефакторинг системы маршрутизации уведомлений из async задач обратно в канал-источник. Текущая реализация сломана: `publishMessageReceivedEvent` — мёртвый код, `Channel.sendMessage(String)` не имеет routing context, Telegram/Discord хранят chatId в instance variables (перезаписываются при multi-user), Web Chat дропает уведомления в лог.

Заменить на conversation-aware routing: таблица `conversation_channel_context` хранит routing data (chatId, threadId, channelId) per conversationId. TaskHandler достаёт routing context из БД и передаёт в `Channel.sendMessage(RoutingContext, String)`.

## Objective

После выполнения:
1. Каждый канал при получении сообщения сохраняет routing context в БД
2. TaskTool принимает `conversationId` (не `sourceChannelName`) — агент знает conversationId из контекста
3. TaskHandler достаёт routing context по conversationId и доставляет уведомление в правильный канал/чат/тред
4. Multi-user изоляция: каждый conversationId → свой routing context (chatId, threadId)
5. Web Chat: фронтенд поллит task status, уведомления появляются при следующем взаимодействии
6. Мёртвый код `publishMessageReceivedEvent` + `ChannelMessageReceivedEvent` удалён

## Problem Statement

5 критических проблем текущей системы:

| # |                                   Проблема                                    |                          Impact                          |
|---|-------------------------------------------------------------------------------|----------------------------------------------------------|
| 1 | Agent не знает имя канала → не может заполнить `sourceChannelName` в TaskTool | Task notifications никогда не доставляются правильно     |
| 2 | `ChatChannel.sendMessage()` — no-op                                           | Web-пользователи не получают результаты задач            |
| 3 | Telegram/Discord хранят chatId в instance var                                 | Multi-user: уведомление уходит не тому пользователю      |
| 4 | Нет маппинга conversationId → channel routing data                            | Невозможно восстановить контекст канала для async задачи |
| 5 | `publishMessageReceivedEvent` — мёртвый deprecated код                        | 3 caller'а вызывают метод, который ничего не делает      |

## Solution Approach

**Conversation-aware routing** через таблицу `conversation_channel_context`:

```
Channel.consume(message)
  → ChannelContextService.save(conversationId, channelName, routingData)
  → agent.respondTo(conversationId, message)
    → LLM вызывает TaskTool.createTask(name, desc, conversationId)  // conversationId вместо channelName
      → TaskManager.create(name, desc, conversationId)
        → tasks.conversation_id = conversationId  // новая колонка
        → JobRunr async
          → TaskHandler.executeTask(taskId)
            → ChannelContextService.getContext(task.getConversationId())
            → channelRegistry.getChannel(ctx.channelName())
            → channel.sendMessage(ctx.toRoutingContext(), resultMessage)
```

**Breaking change в Channel interface**: `sendMessage(String)` → `sendMessage(RoutingContext, String)`. Все каналы (Telegram, Discord, ChatChannel) обновляются.

**Web Chat**: `ChatChannel.sendMessage(RoutingContext, msg)` пишет result в `SPRING_AI_CHAT_MEMORY` как AssistantMessage. Фронтенд поллит GET `/api/tasks` для статусов.

## Relevant Files

### Модифицируемые файлы

- `javaclaw-core/src/main/java/ai/javaclaw/channels/Channel.java` — интерфейс: `sendMessage(String)` → `sendMessage(RoutingContext, String)`
- `javaclaw-core/src/main/java/ai/javaclaw/channels/ChannelRegistry.java` — удалить `publishMessageReceivedEvent()`
- `javaclaw-core/src/main/java/ai/javaclaw/tools/TaskTool.java` — `sourceChannelName` → `conversationId`
- `javaclaw-core/src/main/java/ai/javaclaw/tasks/TaskManager.java` — принимать conversationId, сохранять в Task
- `javaclaw-core/src/main/java/ai/javaclaw/tasks/Task.java` — добавить `conversationId`, убрать/deprecated `sourceChannelName`
- `javaclaw-core/src/main/java/ai/javaclaw/tasks/TaskHandler.java` — `notifyUser()` через ChannelContextService
- `javaclaw-channel/javaclaw-channel-telegram/src/main/java/ai/javaclaw/channels/telegram/TelegramChannel.java` — убрать instance vars chatId/threadId, сохранять routing context через service, реализовать `sendMessage(RoutingContext, String)`
- `javaclaw-channel/javaclaw-channel-discord/src/main/java/ai/javaclaw/channels/discord/DiscordChannel.java` — аналогично
- `javaclaw-api/javaclaw-api-chat/src/main/java/ai/javaclaw/api/chat/channel/ChatChannel.java` — реализовать `sendMessage(RoutingContext, String)`: записать в chat memory как AssistantMessage
- `javaclaw-core/src/main/java/ai/javaclaw/tasks/TaskIdGeneratorCallback.java` — обновить `new Task(...)` вызов для нового `conversationId` параметра
- `javaclaw-api/javaclaw-api-chat/src/main/java/ai/javaclaw/api/chat/service/SseStreamingService.java` — убрать вызов `publishMessageReceivedEvent`

### Удаляемые файлы

- `javaclaw-core/src/main/java/ai/javaclaw/channels/ChannelMessageReceivedEvent.java` — мёртвый event class

### New Files

- `javaclaw-core/src/main/resources/db/migration/V11__create_conversation_channel_context.sql` — таблица routing context
- `javaclaw-core/src/main/resources/db/migration/V12__add_conversation_id_to_tasks.sql` — колонка conversation_id в tasks
- `javaclaw-core/src/main/java/ai/javaclaw/channels/RoutingContext.java` — record с channelName + routing data
- `javaclaw-core/src/main/java/ai/javaclaw/channels/ConversationChannelContext.java` — entity для таблицы
- `javaclaw-core/src/main/java/ai/javaclaw/channels/ConversationChannelContextRepository.java` — Spring Data JDBC
- `javaclaw-core/src/main/java/ai/javaclaw/channels/ChannelContextService.java` — save/get routing context
- `javaclaw-core/src/test/java/ai/javaclaw/channels/ChannelContextServiceTest.java`
- `javaclaw-core/src/test/java/ai/javaclaw/channels/RoutingContextTest.java`
- `javaclaw-core/src/test/java/ai/javaclaw/tasks/TaskHandlerTest.java`

## Implementation Phases

### Phase 1: Foundation — Schema + Domain Model

- Flyway миграции V10, V11
- `RoutingContext` record
- `ConversationChannelContext` entity + repository
- `ChannelContextService` (save/get)

### Phase 2: Core — Channel Interface + Implementations

- Breaking change `Channel.sendMessage(RoutingContext, String)`
- TelegramChannel: убрать instance vars, использовать RoutingContext
- DiscordChannel: аналогично
- ChatChannel: записать в chat memory при sendMessage
- Все каналы: сохранять routing context при получении сообщения

### Phase 3: Task Pipeline — TaskTool + TaskHandler

- TaskTool: `conversationId` вместо `sourceChannelName`
- Task entity: `conversationId` колонка
- TaskManager.create: сохранять conversationId
- TaskHandler.notifyUser: lookup routing context → deliver

### Phase 4: Cleanup + Frontend

- Удалить `publishMessageReceivedEvent`, `ChannelMessageReceivedEvent`
- Убрать вызовы из SseStreamingService, TelegramChannel, DiscordChannel
- Frontend: polling GET `/api/tasks`

## Team Orchestration

- Я выступаю как team lead и оркестрирую команду через Task* Tools.
- Я НИКОГДА не работаю с кодом напрямую — только деплою team members для реализации.
- Фазы 1-2 последовательны; Phase 3 зависит от Phase 2; Phase 4 — cleanup после всего.

### Team Members

- Builder
  - Name: builder-routing
  - Role: Основной разработчик — миграции, domain model, Channel interface, channel implementations, task pipeline
  - Agent Type: builder
  - Resume: true
- Builder
  - Name: builder-frontend
  - Role: Frontend polling для task notifications
  - Agent Type: builder
  - Resume: false
- Builder
  - Name: builder-tests
  - Role: Comprehensive unit + integration тесты
  - Agent Type: builder
  - Resume: true
- Validator
  - Name: validator-final
  - Role: Read-only валидация: compile, тесты, acceptance criteria
  - Agent Type: validator
  - Resume: false

## Testing Strategy

Test pyramid ratio: **80% unit / 15% integration-API / 5% UI e2e**

### Unit Tests (80%)

|         Test Class          |     Тестируемый класс      |                                    Ключевые тест-кейсы                                     |
|-----------------------------|----------------------------|--------------------------------------------------------------------------------------------|
| `RoutingContextTest`        | `RoutingContext`           | Создание, JSON serialization routing data, null handling                                   |
| `ChannelContextServiceTest` | `ChannelContextService`    | save + get round trip, update existing, not found returns empty, channel name extraction   |
| `TaskHandlerTest`           | `TaskHandler.notifyUser()` | Доставка через routing context, channel not found → fallback, null conversationId handling |
| `TaskManagerTest`           | `TaskManager`              | create() сохраняет conversationId в Task, schedule() аналогично                            |
| `TaskToolTest`              | `TaskTool`                 | conversationId передаётся в TaskManager                                                    |

### Integration / API Tests (15%)

|                 Test Class                 |          Scope           |                                  Описание                                  |
|--------------------------------------------|--------------------------|----------------------------------------------------------------------------|
| `ConversationChannelContextRepositoryTest` | JDBC + DB                | Save/findByConversationId round trip, upsert semantics                     |
| `TaskHandlerIntegrationTest`               | Handler + ContextService | End-to-end: save context → create task → execute → notify via mock channel |

### UI E2E Tests (5%)

- Существующие E2E тесты не затрагиваются
- Frontend polling тестируется через unit тесты hook'а

## Step by Step Tasks

- IMPORTANT: Execute every step in order, top to bottom.

### 1. Create Flyway Migrations + Domain Model

- **Task ID**: create-foundation
- **Depends On**: none
- **Assigned To**: builder-routing
- **Agent Type**: general-purpose
- **Stack**: Java Spring Boot entity JPA record
- **Parallel**: false
- **Tests**: Unit: `RoutingContextTest`, `ChannelContextServiceTest`
- Создать `V11__create_conversation_channel_context.sql`:

  ```sql
  CREATE TABLE conversation_channel_context (
      conversation_id VARCHAR(256) PRIMARY KEY,
      channel_name    VARCHAR(100) NOT NULL,
      routing_data    TEXT,  -- JSON: {"chatId":42,"threadId":7}
      updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
  );
  ```
- Создать `V12__add_conversation_id_to_tasks.sql`:

  ```sql
  ALTER TABLE tasks ADD COLUMN conversation_id VARCHAR(256);
  -- Migrate existing data: copy source_channel_name to conversation_id where available
  UPDATE tasks SET conversation_id = source_channel_name WHERE source_channel_name IS NOT NULL;
  ```
- Создать `RoutingContext` record в `ai.javaclaw.channels`:
  - `String channelName` — имя канала ("TelegramChannel", "DiscordChannel", "ChatChannel")
  - `Map<String, String> data` — routing data (chatId, threadId, channelId, conversationId)
  - Helper: `String get(String key)`, `long getLong(String key)`
- Создать `ConversationChannelContext` entity (Spring Data JDBC):
  - `@Id String conversationId`
  - `String channelName`
  - `String routingData` (JSON строка)
  - `Instant updatedAt`
- Создать `ConversationChannelContextRepository` extends `CrudRepository`
  - `Optional<ConversationChannelContext> findByConversationId(String id)` — уже есть через @Id
- Создать `ChannelContextService` (@Service):
  - `void saveContext(String conversationId, String channelName, Map<String, String> routingData)` — upsert
  - `Optional<RoutingContext> getContext(String conversationId)` — load + deserialize JSON
- Написать unit тесты

### 2. Refactor Channel Interface + Implementations

- **Task ID**: refactor-channels
- **Depends On**: create-foundation
- **Assigned To**: builder-routing
- **Agent Type**: general-purpose
- **Stack**: Java Spring Boot service component
- **Parallel**: false
- **Tests**: Compile all channel modules.
- Изменить `Channel.java` interface:
  - Удалить `void sendMessage(String message)`
  - Добавить `void sendMessage(RoutingContext routingContext, String message)`
- Обновить `TelegramChannel.java`:
  - Убрать instance variables `chatId`, `messageThreadId`
  - Добавить `ChannelContextService channelContextService` в конструктор
  - В `consume(Update)`: после получения сообщения вызвать `channelContextService.saveContext(conversationId, getName(), Map.of("chatId", chatId, "threadId", threadId))`
  - `sendMessage(RoutingContext ctx, String message)`: extract chatId/threadId из `ctx.data()`, вызвать Telegram API
  - Убрать вызов `publishMessageReceivedEvent`
- Обновить `DiscordChannel.java`:
  - Убрать instance variable `lastChannel`
  - Добавить `ChannelContextService channelContextService`
  - В `onMessageReceived()`: сохранить routing context `Map.of("channelId", channelId)`
  - `sendMessage(RoutingContext ctx, String message)`: resolve Discord channel по channelId из JDA, отправить
  - Убрать вызов `publishMessageReceivedEvent`
- Обновить `ChatChannel.java`:
  - Добавить `ChatMemoryRepository chatMemoryRepository` (не ChatMemory — в кодовой базе используется repository pattern)
  - `sendMessage(RoutingContext ctx, String message)`: записать AssistantMessage в chat memory через repository
- Обновить **существующие тесты** сразу (breaking change в Channel interface):
  - `TelegramChannelTest` — обновить mock/verify для `sendMessage(RoutingContext, String)`
  - `DiscordChannelTest` — аналогично
  - `ChatChannelTest` — аналогично
  - `ChannelRegistryTest` — если вызывает `sendMessage`
  - `TaskManagerTest` — обновить `new Task(...)` конструкторы (9-й arg)
  - Пользователь увидит при следующем открытии/поллинге

### 3. Refactor Task Pipeline

- **Task ID**: refactor-task-pipeline
- **Depends On**: refactor-channels
- **Assigned To**: builder-routing
- **Agent Type**: general-purpose
- **Stack**: Java Spring Boot service entity JPA
- **Parallel**: false
- **Tests**: Unit: `TaskHandlerTest`, `TaskManagerTest`, `TaskToolTest`
- Обновить `Task.java`:
  - Добавить поле `String conversationId`
  - Добавить `withConversationId(String id)` builder method
  - `sourceChannelName` пометить `@Deprecated` (не удалять — backward compat с существующими tasks в БД)
  - Обновить ВСЕ `with*` методы и `newTask` factory чтобы propagate `conversationId`
- Обновить `TaskIdGeneratorCallback.java`:
  - `new Task(...)` вызов содержит 8 positional args — добавить 9-й `conversationId` (null)
- Обновить `TaskTool.java`:
  - `createTask(name, desc, conversationId)` — переименовать параметр, обновить @Tool description
  - `scheduleTask(executionTime, name, desc, conversationId)` — аналогично
  - В описании: "The conversationId of the current conversation. Used to route task notifications back to the user's channel."
- Обновить `TaskManager.java`:
  - `create(name, desc, conversationId)` — `Task.newTask(name, desc).withConversationId(conversationId)`
  - `schedule(...)` — аналогично
- Обновить `TaskHandler.java`:
  - Inject `ChannelContextService channelContextService`
  - `notifyUser(Task task, TaskResult result)`:
    1. `Optional<RoutingContext> ctx = channelContextService.getContext(task.getConversationId())`
    2. If present: `channelRegistry.getChannel(ctx.channelName()).sendMessage(ctx, message)`
    3. If empty: fallback на `task.getSourceChannelName()` (legacy) → `channelRegistry.getChannel(name).sendMessage(new RoutingContext(name, Map.of()), message)`
    4. Catch + log warn

### 4. Cleanup Dead Code

- **Task ID**: cleanup-dead-code
- **Depends On**: refactor-task-pipeline
- **Assigned To**: builder-routing
- **Agent Type**: general-purpose
- **Stack**: Java Spring Boot
- **Parallel**: false
- **Tests**: Compile + grep verification
- Удалить `ChannelMessageReceivedEvent.java`
- Удалить `publishMessageReceivedEvent()` из `ChannelRegistry.java`
- Убрать вызов из `SseStreamingService.java` + удалить import `ChannelMessageReceivedEvent`
- Удалить `TelegramChannelMessageReceivedEvent` inner class из TelegramChannel
- Убрать import `ChannelMessageReceivedEvent` из всех файлов
- Verify: `grep -r "ChannelMessageReceivedEvent\|publishMessageReceivedEvent" --include="*.java"` → пусто

### 5. Frontend Task Polling

- **Task ID**: frontend-polling
- **Depends On**: refactor-task-pipeline
- **Assigned To**: builder-frontend
- **Agent Type**: general-purpose
- **Stack**: React component hook useState useEffect tsx vite
- **Parallel**: true (can run alongside cleanup)
- **Tests**: N/A for MVP polling
- Создать hook `useTaskNotifications(conversationId)` в `javaclaw-frontend/src/hooks/`:
  - Поллит `GET /api/tasks?conversationId={id}&status=completed` каждые 30 секунд
  - Возвращает `{ completedTasks: Task[], dismiss: (taskId) => void }`
- Добавить endpoint в backend (если отсутствует): `GET /api/tasks` с query params
  - Проверить TaskController / создать если нет
- Интегрировать в Chat UI: показать toast/badge при появлении completed task

### 6. Write Comprehensive Tests

- **Task ID**: write-tests
- **Depends On**: cleanup-dead-code, frontend-polling
- **Assigned To**: builder-tests
- **Agent Type**: general-purpose
- **Stack**: Java Mockito assertj allure test structure test naming
- **Parallel**: false
- **Tests**: N/A — this IS the test task.
- `RoutingContextTest`: создание, null data, getLong helper
- `ChannelContextServiceTest`: save+get round trip, upsert, not found, JSON parsing
- `TaskHandlerTest`: routing context delivery, fallback to sourceChannelName, null conversationId
- `TaskManagerTest`: conversationId сохраняется
- `ConversationChannelContextRepositoryTest`: JDBC integration с H2/Testcontainers
- Verify all existing tests still pass: `mvn test`

### 7. Final Validation

- **Task ID**: validate-all
- **Depends On**: write-tests
- **Assigned To**: validator-final
- **Agent Type**: validator
- **Stack**: Java Spring Boot Maven test compile spotless assertj
- **Parallel**: false
- Run all validation commands
- Verify all tests pass
- Verify acceptance criteria met:
  - [ ] `conversation_channel_context` таблица создана (V10)
  - [ ] `tasks.conversation_id` колонка добавлена (V11)
  - [ ] Channel.sendMessage принимает RoutingContext
  - [ ] TelegramChannel сохраняет routing context per conversationId
  - [ ] DiscordChannel сохраняет routing context per conversationId
  - [ ] ChatChannel пишет в chat memory
  - [ ] TaskTool принимает conversationId
  - [ ] TaskHandler доставляет через routing context
  - [ ] publishMessageReceivedEvent удалён
  - [ ] ChannelMessageReceivedEvent удалён
  - [ ] `mvn compile -q` проходит
  - [ ] `mvn test` — все тесты зелёные

## Acceptance Criteria

1. Таблица `conversation_channel_context` создана Flyway миграцией V11
2. Колонка `conversation_id` добавлена в `tasks` через V12
3. `Channel.sendMessage(RoutingContext, String)` — единственный метод отправки (breaking change)
4. TelegramChannel сохраняет `{chatId, threadId}` при каждом сообщении через `ChannelContextService`
5. DiscordChannel сохраняет `{channelId}` аналогично
6. ChatChannel записывает task result в chat memory как AssistantMessage
7. TaskTool принимает `conversationId` (не `sourceChannelName`)
8. TaskHandler.notifyUser() достаёт RoutingContext из БД, доставляет через channel
9. `publishMessageReceivedEvent` и `ChannelMessageReceivedEvent` полностью удалены
10. Multi-user: два Telegram-пользователя с разными chatId получают уведомления в свои чаты
11. `mvn compile -q` и `mvn test` проходят без ошибок

## Validation Commands

- `mvn spotless:check` — форматирование
- `mvn compile -q` — компиляция без ошибок
- `mvn test` — все тесты зелёные
- `grep -r "publishMessageReceivedEvent" --include="*.java" javaclaw-*` — должен вернуть пусто
- `grep -r "ChannelMessageReceivedEvent" --include="*.java" javaclaw-*/src/main` — должен вернуть пусто
- `grep -r "sendMessage(String" --include="*.java" javaclaw-core/src/main/java/ai/javaclaw/channels/Channel.java` — должен вернуть пусто (старый метод удалён)

## Notes

- **Flyway V11/V12**: Последняя миграция — V10 (`V10__seed_default_users.sql`). Новые: V11 (routing context table), V12 (tasks.conversation_id column).
- **RoutingContext как JSON**: `routing_data TEXT` в БД хранит JSON-строку. Десериализация через Jackson ObjectMapper в `ChannelContextService`.
- **Backward compatibility**: `Task.sourceChannelName` пометить `@Deprecated`, но не удалять. `TaskHandler.notifyUser()` fallback'ит на sourceChannelName если conversationId отсутствует (legacy tasks в БД).
- **Discord channelId**: Discord JDA позволяет resolve channel по ID: `jda.getTextChannelById(channelId)`. Нужно inject JDA instance в DiscordChannel.
- **ChatChannel + ChatMemory**: записываем AssistantMessage с prefix `[Task notification]` чтобы отличить от обычных ответов.
- **Frontend polling**: MVP — простой polling каждые 30 сек. В будущем можно заменить на SSE persistent connection.
- **Spring AI 2.0.0-SNAPSHOT**: `AssistantMessage` builder API: `AssistantMessage.builder().content(text).build()`.

