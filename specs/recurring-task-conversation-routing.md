# Plan: Recurring Task Conversation Routing

## Task Description

Добавить поддержку `conversationId` в `RecurringTask`, чтобы периодические задачи были привязаны к конкретному пользователю/каналу. Сейчас recurring tasks не привязаны к conversation, и нотификации при срабатывании уходят в default channel (первый зарегистрированный). После реализации — нотификации будут маршрутизироваться через `ChannelContextService` в тот канал, из которого задача была создана.

## Objective

При создании recurring task через `TaskTool.scheduleRecurringTask()` — сохранять `conversationId`. При каждом срабатывании — создавать дочерний `Task` с этим `conversationId`, обеспечивая маршрутизацию нотификаций в правильный канал через существующий `ChannelContextService` → `RoutingContext` → `Channel` pipeline. Существующие recurring tasks без `conversationId` продолжают работать через default channel fallback.

## Problem Statement

`RecurringTask` entity не имеет поля `conversationId`. Метод `TaskManager.createTaskFromRecurringTask()` создаёт дочерний `Task` без `conversationId`, теряя routing context. В результате `TaskHandler.notifyUser()` не находит `RoutingContext` и отправляет нотификацию в default channel — пользователь получает уведомление не в тот канал, откуда создавал задачу.

**Цепочка потери контекста:**

```
User (Web Chat, conversationId="web-abc123")
  → AI Agent → TaskTool.scheduleRecurringTask(cron, name, desc)  ← conversationId НЕ передаётся
    → TaskManager.scheduleRecurrently(cron, name, desc)          ← conversationId НЕ сохраняется
      → RecurringTask(name, desc, cron)                          ← нет поля conversationId
        → [cron trigger] → RecurringTaskHandler.executeTask()
          → TaskManager.createTaskFromRecurringTask(rt)
            → Task.newTask(name, desc)                           ← conversationId = null
              → TaskHandler.notifyUser()
                → channelContextService.getContext(null)          ← Optional.empty()
                  → channelRegistry.getChannel(null)             ← DEFAULT CHANNEL
```

## Solution Approach

Минимальный TaskTool-only подход:
1. Добавить `conversationId` в `RecurringTask` entity
2. Flyway миграция V13 — `ALTER TABLE recurring_tasks ADD COLUMN conversation_id`
3. Прокинуть `conversationId` через `TaskManager.scheduleRecurrently()` → `RecurringTask`
4. В `TaskManager.createTaskFromRecurringTask()` — передать `conversationId` из `RecurringTask` в дочерний `Task`
5. Добавить `conversationId` параметр в `TaskTool.scheduleRecurringTask()`
6. Обратная совместимость: `conversationId = null` → default channel fallback (как сейчас)

**Цепочка ПОСЛЕ фикса:**

```
User (Web Chat, conversationId="web-abc123")
  → AI Agent → TaskTool.scheduleRecurringTask(cron, name, desc, "web-abc123")
    → TaskManager.scheduleRecurrently(cron, name, desc, "web-abc123")
      → RecurringTask(name, desc, cron, conversationId="web-abc123")
        → [cron trigger] → RecurringTaskHandler.executeTask()
          → TaskManager.createTaskFromRecurringTask(rt)
            → Task.newTask(name, desc).withConversationId("web-abc123")
              → TaskHandler.notifyUser()
                → channelContextService.getContext("web-abc123")  ← RoutingContext found!
                  → channelRegistry.getChannel("WebChatChannel") ← CORRECT CHANNEL
```

## Relevant Files

Используй эти файлы для выполнения задачи:

- `javaclaw-core/src/main/java/ai/javaclaw/tasks/RecurringTask.java` — entity, добавить поле `conversationId` + `withConversationId()` + обновить `newRecurringTask()`
- `javaclaw-core/src/main/java/ai/javaclaw/tasks/TaskManager.java` — добавить `conversationId` в `scheduleRecurrently()` и прокинуть в `createTaskFromRecurringTask()`
- `javaclaw-core/src/main/java/ai/javaclaw/tools/TaskTool.java` — добавить `conversationId` параметр в `scheduleRecurringTask()`
- `javaclaw-core/src/main/java/ai/javaclaw/tasks/RecurringTaskHandler.java` — без изменений (получает RecurringTask из БД, conversationId подтянется автоматически)
- `javaclaw-core/src/main/java/ai/javaclaw/tasks/RecurringTaskIdGeneratorCallback.java` — обновить конструктор `RecurringTask` — добавить `task.getConversationId()` в вызов `new RecurringTask(...)` (строка 13-19)
- `javaclaw-core/src/main/java/ai/javaclaw/tasks/RecurringTaskRepository.java` — без изменений (Spring Data JDBC)
- `javaclaw-core/src/main/java/ai/javaclaw/channels/ChannelContextService.java` — без изменений (уже поддерживает lookup по conversationId)
- `javaclaw-core/src/main/java/ai/javaclaw/tasks/TaskHandler.java` — без изменений (уже вызывает `channelContextService.getContext(task.getConversationId())`)
- `javaclaw-core/src/test/java/ai/javaclaw/tasks/TaskManagerTest.java` — обновить существующие тесты, добавить новые
- `javaclaw-core/src/test/java/ai/javaclaw/tasks/RecurringTaskRepositoryTest.java` — добавить тест на сохранение conversationId

### New Files

- `javaclaw-core/src/main/resources/db/migration/V13__add_conversation_id_to_recurring_tasks.sql` — миграция

## Implementation Phases

### Phase 1: Foundation

- Flyway миграция V13 — добавить колонку `conversation_id` в таблицу `recurring_tasks` (nullable для обратной совместимости)
- Обновить `RecurringTask` entity — добавить поле, конструктор, `withConversationId()`

### Phase 2: Core Implementation

- Обновить `TaskManager.scheduleRecurrently()` — принять и сохранить `conversationId`
- Обновить `TaskManager.createTaskFromRecurringTask()` — прокинуть `conversationId` из recurring task в дочерний task
- Обновить `TaskTool.scheduleRecurringTask()` — добавить параметр `conversationId` в @Tool метод

### Phase 3: Integration & Polish

- Обновить unit-тесты TaskManager
- Обновить integration-тесты RecurringTaskRepository
- Добавить тест на полный цикл: recurring task → child task → conversationId propagation
- Валидация обратной совместимости (null conversationId → default channel)

## Team Orchestration

- Я действую как team lead и оркестрирую команду для выполнения плана.
- Я НЕ работаю непосредственно с кодовой базой. Я использую `Task` и `Task*` инструменты для делегирования работы.

### Team Members

- Builder
  - Name: builder-core
  - Role: Реализация core-логики — entity, migration, TaskManager, TaskTool
  - Agent Type: builder
  - Resume: true
- Builder
  - Name: builder-tests
  - Role: Написание unit и integration тестов
  - Agent Type: builder
  - Resume: true
- Validator
  - Name: validator-final
  - Role: Финальная валидация — компиляция, тесты, acceptance criteria
  - Agent Type: validator
  - Resume: false

## Testing Strategy

Test pyramid ratio: **80% unit / 15% integration-API / 5% UI e2e**

### Unit Tests (80%)

- **RecurringTask entity tests** — проверить что `conversationId` корректно передаётся через конструктор, `withConversationId()`, `newRecurringTask()`
- **TaskManagerTest.scheduleRecurrentlyWithConversationId** — проверить что `conversationId` сохраняется в RecurringTask при создании recurring job
- **TaskManagerTest.createTaskFromRecurringTaskPropagatesConversationId** — проверить что дочерний Task получает `conversationId` из RecurringTask
- **TaskManagerTest.createTaskFromRecurringTaskWithNullConversationId** — проверить обратную совместимость: null conversationId → Task без conversationId
- **TaskManagerTest.scheduleRecurrentlyWithoutConversationId** — обратная совместимость существующего вызова

### Integration / API Tests (15%)

- **RecurringTaskRepositoryTest.savePreservesConversationId** — проверить что conversationId сохраняется/читается из БД
- **RecurringTaskRepositoryTest.saveWithNullConversationId** — проверить что null conversationId корректно обрабатывается
- **Full propagation integration test** — RecurringTask(conversationId) → createTaskFromRecurringTask → Task.getConversationId() != null

### UI E2E Tests (5%)

- Не требуются для данной задачи (изменения только в core, нет UI-компонентов)

## Step by Step Tasks

### 1. Database Migration + Entity Update

- **Task ID**: migration-and-entity
- **Depends On**: none
- **Assigned To**: builder-core
- **Agent Type**: builder
- **Stack**: Java Spring Boot JPA entity record migration
- **Parallel**: false
- **Tests**: Unit: RecurringTask entity constructor tests (builder-tests сделает позже)
- Создать файл миграции `V13__add_conversation_id_to_recurring_tasks.sql`:

  ```sql
  ALTER TABLE recurring_tasks ADD COLUMN conversation_id VARCHAR(256);
  ```
- Обновить `RecurringTask.java`:
  - Добавить поле `private final String conversationId` в конструктор
  - Обновить factory method `newRecurringTask()` — добавить overload с `conversationId` параметром. Сохранить существующий метод без `conversationId` для обратной совместимости (передаёт null)
  - Добавить метод `withConversationId(String conversationId)`
  - Обновить метод `withJobId()` — сохранять `conversationId` при копировании
- Обновить `RecurringTaskIdGeneratorCallback.onBeforeConvert()` — добавить `task.getConversationId()` в конструктор `new RecurringTask(...)` (7-й аргумент)

### 2. TaskManager Propagation

- **Task ID**: taskmanager-propagation
- **Depends On**: migration-and-entity
- **Assigned To**: builder-core
- **Agent Type**: builder
- **Stack**: Java Spring Boot service
- **Parallel**: false
- **Tests**: Unit: TaskManagerTest — scheduleRecurrently и createTaskFromRecurringTask (builder-tests сделает позже)
- Обновить `TaskManager.scheduleRecurrently()`:
  - Добавить overload с `conversationId` параметром
  - Сохранить существующий метод без `conversationId` для обратной совместимости (вызывает новый с null)
  - Использовать `RecurringTask.newRecurringTask(name, description, cronExpression, conversationId)` в новом overload
- Обновить `TaskManager.createTaskFromRecurringTask()`:
  - После создания `Task.newTask(name, desc)` — вызвать `.withConversationId(recurringTask.getConversationId())`
  - Это ключевое изменение: дочерний task теперь наследует conversationId от recurring task

### 3. TaskTool Integration

- **Task ID**: tasktool-integration
- **Depends On**: taskmanager-propagation
- **Assigned To**: builder-core
- **Agent Type**: builder
- **Stack**: Java Spring Boot service
- **Parallel**: false
- **Tests**: нет отдельных тестов — покрывается через TaskManager тесты
- Обновить `TaskTool.scheduleRecurringTask()`:
  - Добавить параметр `String conversationId` в метод
  - Обновить `@Tool` description — документировать новый параметр
  - Вызывать `taskManager.scheduleRecurrently(cronExpression, name, description, conversationId)`
  - НЕ менять `TaskEventHandler.recurringTaskCreated(cronExpression, name, description)` — event consumers не нуждаются в conversationId (это internal routing concern, не бизнес-событие). Оставить сигнатуру как есть.

### 4. Write Tests

- **Task ID**: write-tests
- **Depends On**: tasktool-integration
- **Assigned To**: builder-tests
- **Agent Type**: builder
- **Stack**: Java MockMvc Mockito assertj allure test structure test naming integration test database test repository test
- **Parallel**: false
- **ВАЖНО**: Существующие тесты `TaskManagerTest` (строки ~123, ~140) используют `new RecurringTask(id, name, desc, cron, null, instant)` с 6 аргументами — после добавления поля `conversationId` конструктор станет 7-аргументным. Обновить эти вызовы: добавить `null` как 7-й аргумент (conversationId) для обратной совместимости.
- Написать unit тесты в `TaskManagerTest.java`:
  - `scheduleRecurrentlyWithConversationIdSavesItToRecurringTask()` — verify что conversationId передан в RecurringTask
  - `scheduleRecurrentlyWithoutConversationIdPassesNull()` — обратная совместимость
  - `createTaskFromRecurringTaskPropagatesConversationId()` — verify что Task получил conversationId от RecurringTask
  - `createTaskFromRecurringTaskWithNullConversationIdCreatesTaskWithNull()` — null safety
- Написать integration тесты в `RecurringTaskRepositoryTest.java`:
  - `savePreservesConversationId()` — сохранить и прочитать recurring task с conversationId
  - `saveWithNullConversationIdWorks()` — nullable column корректно обрабатывается
- **КРИТИЧНО**: после написания — запустить все тесты и убедиться что они зелёные

### 5. Final Validation

- **Task ID**: validate-all
- **Depends On**: write-tests
- **Assigned To**: validator-final
- **Agent Type**: validator
- **Stack**: Java Spring Boot maven surefire test
- **Parallel**: false
- Запустить полную компиляцию: `./mvnw compile -pl javaclaw-core`
- Запустить все тесты модуля: `./mvnw test -pl javaclaw-core`
- Проверить что Flyway миграция V13 применяется корректно (в тестах с Testcontainers)
- Верифицировать acceptance criteria:
  - RecurringTask entity имеет поле conversationId
  - TaskManager.createTaskFromRecurringTask() пропагирует conversationId
  - Существующие recurring tasks (conversationId=null) продолжают работать
  - Все тесты зелёные

## Acceptance Criteria

- [ ] `RecurringTask` entity содержит поле `conversationId` (nullable)
- [ ] Flyway миграция V13 добавляет колонку `conversation_id` в таблицу `recurring_tasks`
- [ ] `TaskTool.scheduleRecurringTask()` принимает `conversationId` параметр
- [ ] `TaskManager.scheduleRecurrently()` сохраняет `conversationId` в `RecurringTask`
- [ ] `TaskManager.createTaskFromRecurringTask()` передаёт `conversationId` из `RecurringTask` в дочерний `Task`
- [ ] Дочерний `Task` маршрутизирует нотификации через `ChannelContextService` в правильный канал
- [ ] Существующие recurring tasks без `conversationId` продолжают работать через default channel fallback
- [ ] Unit тесты покрывают propagation и обратную совместимость
- [ ] Integration тесты покрывают persistence conversationId
- [ ] `./mvnw test -pl javaclaw-core` — все тесты зелёные

## Validation Commands

Выполни эти команды для валидации:

- `./mvnw compile -pl javaclaw-core` — проект компилируется без ошибок
- `./mvnw test -pl javaclaw-core` — все тесты проходят
- `grep -r "conversationId" javaclaw-core/src/main/java/ai/javaclaw/tasks/RecurringTask.java` — поле существует в entity
- `cat javaclaw-core/src/main/resources/db/migration/V13__add_conversation_id_to_recurring_tasks.sql` — миграция существует
- `grep -r "withConversationId" javaclaw-core/src/main/java/ai/javaclaw/tasks/TaskManager.java` — propagation в createTaskFromRecurringTask

## Notes

- Не добавляем REST endpoint — это отдельная задача при появлении потребности от UI/интеграций
- `RecurringTaskHandler` и `TaskHandler` не требуют изменений — они уже корректно работают с conversationId через существующий pipeline
- `RecurringTaskIdGeneratorCallback` **требует обновления** — конструирует `RecurringTask` напрямую, нужно прокинуть `task.getConversationId()` (7-й аргумент)
- `TaskEventHandler.recurringTaskCreated()` **НЕ меняется** — conversationId является internal routing concern, event consumers не нуждаются в нём
- Паттерн `withConversationId()` уже используется в `Task` entity — следуем тому же immutable pattern

