# JavaClaw Task Architecture — Complete Specification

## Context

Полная переработка task pipeline для JavaClaw. Цель: паритет пользовательского опыта с OpenClaw/PicoClaw/NullClaw в многопользовательской среде с горизонтальным масштабированием. Покрываем ВСЕ сценарии, не оставляем дыр.

---

## Пользовательские сценарии (полный список)

Каждый сценарий ниже — это то, что конечный пользователь ДОЛЖЕН уметь делать. Ни один не опционален.

### S1. Базовый чат

Пользователь пишет сообщение → получает streaming ответ → история сохраняется.

### S2. Создание задачи из чата

"Напомни мне завтра в 9 утра про встречу" → агент вызывает TaskTool → задача создаётся → пользователь получает подтверждение.

### S3. Recurring task

"Каждый день в 9 утра присылай погоду" → cron task → каждый день результат приходит в чат пользователя.

### S4. Task isolation

Выполнение задачи НЕ загрязняет чат пользователя raw промптами, JSON schema, internal tool calls.

### S5. Real-time notifications

Когда задача завершается — пользователь видит результат в чате БЕЗ ручного обновления страницы. Для Web Chat — SSE push. Для Telegram/Discord — нативная доставка.

### S6. Human-in-the-loop (approval)

Задача выполняется → агенту нужно разрешение → он отправляет вопрос в чат пользователя → пользователь отвечает → задача продолжается с ответом пользователя.

Пример: "Проверь цены на авиабилеты и купи если дешевле 10000" → агент находит за 9500 → спрашивает "Нашёл за 9500, покупать?" → пользователь "Да" → агент покупает.

### S7. Отмена задачи

Пользователь пишет "Отмени задачу X" или "Стоп" → задача gracefully останавливается → пользователь получает подтверждение отмены.

### S8. Parent-child tasks (иерархия)

"Проанализируй 5 конкурентов" → parent task spawn'ит 5 child tasks → каждый работает параллельно → результаты собираются → parent формирует итоговый отчёт.

### S9. Child result injection

Когда child task завершается → его результат инжектится в контекст parent task → parent может использовать для продолжения работы.

### S10. Progress updates

Long-running task отправляет progress: "Обработано 3 из 5 конкурентов..." → пользователь видит в чате.

### S11. Error notification + audit access

Задача падает → пользователь получает понятную нотификацию об ошибке → пользователь может написать "Покажи детали ошибки" → агент достаёт из аудита полную информацию: какой был запрос к модели, что ответила модель, стектрейс, duration.

### S12. Notify policy

Пользователь может указать: "делай молча" (silent), "сообщай только об ошибках" (on_error), "сообщай только об успехе" (on_success), "сообщай обо всём" (state_changes). По умолчанию — done_only.

### S13. Task timeout / watchdog

Задача зависла → через timeout_seconds помечается failed → пользователь получает нотификацию "Задача X зависла и была остановлена".

### S14. Startup recovery

Сервер перезапустился → pending deliveries из delivery_queue обрабатываются → пользователь получает пропущенные нотификации.

### S15. Rate limiting

Пользователь не может создать бесконечное количество задач. Лимит на количество concurrent tasks и recurring tasks per user.

### S16. Task context carry-over (recurring memory)

Recurring task может помнить предыдущие executions. Пример: "Присылай шутку каждую минуту, не повторяйся" → агент знает какие шутки уже отправлял.

### S17. Per-user tool restrictions

Разные пользователи имеют разные наборы доступных инструментов. Admin видит всё, обычный пользователь — ограниченный набор.

### S18. Multi-channel session isolation

Один пользователь пишет из Telegram и Web Chat → это РАЗНЫЕ сессии (per_channel_peer). Или ОДНА сессия (per_peer) — настраивается.

### S19. Просмотр списка задач

Пользователь: "Покажи мои задачи" → агент показывает список active/recurring/scheduled tasks с их статусами.

### S20. Audit trail — полная прозрачность

Пользователь: "Что произошло с задачей X?" → агент показывает: когда создана, когда запущена, какой prompt отправлен в LLM, какие tools вызваны, что ответила модель, сколько заняло, была ли ошибка.

---

## Архитектура (6 слоёв)

```
┌─────────────────────────────────────────────────────────────────────┐
│  LAYER 1: CHANNEL ADAPTERS                                          │
│                                                                     │
│  WebChatChannel       TelegramChannel       DiscordChannel          │
│                                                                     │
│  interface Channel:                                                 │
│    sendMessage(RoutingContext, message)                              │
│    // Capability interfaces:                                        │
│    StreamingCapable — live LLM output (edit-in-place)              │
│    TypingCapable — typing indicators                                │
│    MessageEditorCapable — edit sent messages                        │
└──────────┬──────────────────────────────────────────────────────────┘
           │
           ▼
┌─────────────────────────────────────────────────────────────────────┐
│  LAYER 2: SESSION ROUTER                                            │
│                                                                     │
│  SessionRouter.resolve(RoutingContext) → Session                    │
│                                                                     │
│  Session isolation modes (как PicoClaw DM scopes):                  │
│    main             — все DM в одну session                         │
│    per_peer         — одна session на юзера cross-channel           │
│    per_channel_peer — session на юзера на канал (default)          │
│                                                                     │
│  Session key: "agent:{agentId}:{channel}:{scope}:{userId}"         │
│  Thread safety: mutex per session, parallel across sessions         │
└──────────┬──────────────────────────────────────────────────────────┘
           │
           ▼
┌─────────────────────────────────────────────────────────────────────┐
│  LAYER 3: EVENT BUS                                                 │
│  (PicoClaw eventbus.go + NullClaw bus.zig)                          │
│                                                                     │
│  interface EventBus {                                               │
│    emit(AgentEvent event);                                          │
│    Flux<AgentEvent> subscribe(String sessionKey);                   │
│    Flux<AgentEvent> subscribeAll();                                 │
│  }                                                                  │
│                                                                     │
│  EventKind enum:                                                    │
│    TURN_START, TURN_END                                             │
│    LLM_REQUEST, LLM_RESPONSE, LLM_DELTA                           │
│    TOOL_EXEC_START, TOOL_EXEC_END, TOOL_EXEC_DENIED               │
│    TASK_CREATED, TASK_STATUS_CHANGE, TASK_CANCELLED                │
│    SUBTASK_SPAWN, SUBTASK_END, SUBTASK_RESULT_INJECTED            │
│    APPROVAL_REQUESTED, APPROVAL_RECEIVED                           │
│    PROGRESS_UPDATE                                                  │
│    ERROR                                                            │
│                                                                     │
│  AgentEvent { kind, timestamp, meta{turnId, sessionKey,            │
│    parentTurnId, taskId}, payload }                                 │
└──────────┬──────────────────────────────────────────────────────────┘
           │
           ▼
┌─────────────────────────────────────────────────────────────────────┐
│  LAYER 4: CHAT PIPELINE                                             │
│                                                                     │
│  MessageAssembler → ChatService → ChatMemory                       │
│                                                                     │
│  ChatService emits events: LLM_REQUEST, LLM_RESPONSE, LLM_DELTA   │
│  ChatMemory: ТОЛЬКО user ↔ assistant. Tasks НИКОГДА сюда.         │
│  Audit: каждый request/response → chat_audit_log                   │
└─────────────────────────────────────────────────────────────────────┘

           ════════════════════════════════════════

┌─────────────────────────────────────────────────────────────────────┐
│  LAYER 5: TASK PIPELINE                                             │
│                                                                     │
│  ┌────────────────────────────────────────────────────────────┐     │
│  │ TaskManager                                                 │     │
│  │  create(name, desc, convId, policy, timeout)               │     │
│  │  schedule(time, name, desc, convId, policy, timeout)       │     │
│  │  scheduleRecurrently(cron, name, desc, convId, policy,     │     │
│  │    timeout, carryOverContext)                               │     │
│  │  spawn(parentTaskId, name, desc, restrictedTools)          │     │
│  │  cancel(taskId) → sets cancelled, cancels JobRunr job      │     │
│  └────────────────────────────────────────────────────────────┘     │
│                                                                     │
│  ┌────────────────────────────────────────────────────────────┐     │
│  │ TaskHandler.executeTask(taskId)                             │     │
│  │                                                             │     │
│  │  1. Acquire semaphore (max concurrent, 30s timeout)        │     │
│  │  2. Check depth ≤ 3 (subtasks)                             │     │
│  │  3. Check CancellationToken before each step               │     │
│  │  4. Create TaskExecution record                             │     │
│  │  5. Emit TURN_START                                         │     │
│  │  6. chatModel.call() НАПРЯМУЮ (не через ChatService):      │     │
│  │     - System prompt + task prompt                           │     │
│  │     - Restricted tools (subtasks: no spawn/cancel)         │     │
│  │     - carryOverContext: include prev execution summaries    │     │
│  │     - Tool loop with CancellationToken check               │     │
│  │     - Emit TOOL_EXEC_START/END per tool call               │     │
│  │     - Emit PROGRESS_UPDATE on model's request              │     │
│  │  7. Save prompt+response+tools → task_executions           │     │
│  │  8. Update Task status                                      │     │
│  │  9. Emit TASK_STATUS_CHANGE                                 │     │
│  │  10. DeliveryService.deliver(task, result)                  │     │
│  │  11. Emit TURN_END                                          │     │
│  │  12. Release semaphore                                      │     │
│  │                                                             │     │
│  │  On error:                                                  │     │
│  │    task.status = failed                                     │     │
│  │    Save error + stacktrace to task_executions               │     │
│  │    DeliveryService.deliver(task, ERROR result)              │     │
│  │    Emit ERROR event                                         │     │
│  │                                                             │     │
│  │  On cancel:                                                 │     │
│  │    task.status = cancelled                                  │     │
│  │    Emit TASK_CANCELLED                                      │     │
│  │    DeliveryService.deliver(task, CANCELLED result)          │     │
│  └────────────────────────────────────────────────────────────┘     │
│                                                                     │
│  ┌────────────────────────────────────────────────────────────┐     │
│  │ ApprovalService (human-in-the-loop)                         │     │
│  │                                                             │     │
│  │  requestApproval(taskId, question, timeout):                │     │
│  │    1. task.status = awaiting_input                          │     │
│  │    2. Save pending approval to DB (approval_requests)       │     │
│  │    3. DeliveryService.deliver(task, APPROVAL_REQUEST)       │     │
│  │    4. Emit APPROVAL_REQUESTED                               │     │
│  │    5. Block on CompletableFuture (with timeout)             │     │
│  │    6. Return user's response or TIMEOUT                     │     │
│  │                                                             │     │
│  │  submitApproval(conversationId, userResponse):              │     │
│  │    1. Find pending approval for conversationId              │     │
│  │    2. Complete the CompletableFuture with response          │     │
│  │    3. task.status = in_progress                             │     │
│  │    4. Emit APPROVAL_RECEIVED                                │     │
│  │                                                             │     │
│  │  Routing: when user replies in chat with pending approval   │     │
│  │    → ChatService checks ApprovalService first               │     │
│  │    → if pending approval exists → submitApproval()          │     │
│  │    → else → normal chat flow                                │     │
│  └────────────────────────────────────────────────────────────┘     │
│                                                                     │
│  ┌────────────────────────────────────────────────────────────┐     │
│  │ TaskWatchdog (@Scheduled fixedRate=30s)                      │     │
│  │                                                             │     │
│  │  1. Find tasks: status=in_progress AND                      │     │
│  │     updated_at < now() - timeout_seconds                    │     │
│  │  2. Mark as failed, set feedback "Timed out"               │     │
│  │  3. DeliveryService.deliver(task, TIMEOUT)                  │     │
│  │                                                             │     │
│  │  Also: find awaiting_input approvals past timeout           │     │
│  │  → auto-deny, resume task with "approval timed out"        │     │
│  └────────────────────────────────────────────────────────────┘     │
│                                                                     │
│  ┌────────────────────────────────────────────────────────────┐     │
│  │ TaskRateLimiter                                              │     │
│  │                                                             │     │
│  │  Per-user limits (configurable):                            │     │
│  │    max_concurrent_tasks: 10                                 │     │
│  │    max_recurring_tasks: 20                                  │     │
│  │    max_tasks_per_hour: 100                                  │     │
│  │                                                             │     │
│  │  checkLimit(userId) → throws RateLimitExceededException     │     │
│  │  Called by TaskManager before create/schedule/spawn          │     │
│  └────────────────────────────────────────────────────────────┘     │
│                                                                     │
│  ┌────────────────────────────────────────────────────────────┐     │
│  │ Task Entity (расширенная модель)                             │     │
│  │                                                             │     │
│  │  Status: todo → in_progress → completed | failed |          │     │
│  │          awaiting_input | cancelled                         │     │
│  │                                                             │     │
│  │  NotifyPolicy: SILENT | DONE_ONLY | ON_ERROR |              │     │
│  │    ON_SUCCESS | STATE_CHANGES                               │     │
│  │                                                             │     │
│  │  TaskRuntime: INLINE | ASYNC | CRON                         │     │
│  │                                                             │     │
│  │  Fields: id, name, description, status, parentTaskId,       │     │
│  │    conversationId, notifyPolicy, runtimeType,               │     │
│  │    timeoutSeconds, carryOverContext (bool),                  │     │
│  │    userId (FK), createdAt, updatedAt, failedAt,            │     │
│  │    cancelledAt, feedback                                    │     │
│  └────────────────────────────────────────────────────────────┘     │
└─────────────────────────────────────────────────────────────────────┘

           │
           ▼
┌─────────────────────────────────────────────────────────────────────┐
│  LAYER 6: DELIVERY SERVICE                                          │
│                                                                     │
│  DeliveryService.deliver(task, result):                             │
│                                                                     │
│  1. Check notifyPolicy against result status                       │
│  2. Resolve target: conversationId → ChannelContextService         │
│     → RoutingContext { channelName, channelMeta }                  │
│  3. Format message: buildNotificationMessage(task, result)         │
│  4. Route by channel:                                              │
│     Web Chat → NotificationTransport.broadcast() (SSE)            │
│     External → delivery_queue + channel.sendMessage() + retry      │
│  5. Persist in user's chat memory (so visible on page reload)      │
│  6. Save to delivery_audit_log                                     │
│                                                                     │
│  delivery_queue: only for external channels (Telegram, Discord)    │
│  Retry: exponential backoff, max 3 attempts                        │
│  Startup recovery: process pending deliveries on boot              │
│                                                                     │
│  NotificationTransport (real-time push):                           │
│    PgNotifyTransport — production PostgreSQL, multi-pod            │
│      broadcast: NOTIFY, subscribe: LISTEN                          │
│      Activation: custom Condition startsWith("jdbc:postgresql")    │
│    InMemoryTransport — dev/test SQLite/H2, single-pod             │
│      broadcast: ApplicationEventPublisher                          │
│      Activation: @ConditionalOnMissingBean                         │
│                                                                     │
│  SSE Endpoint: GET /api/chat/notifications/{conversationId}        │
│    → SseEmitter (separate from chat streaming)                     │
│    → Frontend EventSource → invalidateQueries → UI update          │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Audit System (расширенный)

Два уровня аудита: chat audit + task audit. Пользователь может запросить любую информацию через чат.

### chat_audit_log (уже существует V14, расширить)

```sql
ALTER TABLE chat_audit_log ADD COLUMN user_id VARCHAR(36);
ALTER TABLE chat_audit_log ADD COLUMN tool_calls_detail TEXT;  -- JSON: [{name, args, result, duration_ms}]
ALTER TABLE chat_audit_log ADD COLUMN token_usage TEXT;        -- JSON: {prompt_tokens, completion_tokens, total}
```

### task_audit_log (NEW)

```sql
CREATE TABLE task_audit_log (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    task_id         VARCHAR(256) NOT NULL REFERENCES tasks(id),
    execution_id    VARCHAR(256),
    event_type      VARCHAR(32) NOT NULL,  -- created/started/tool_call/progress/
                                           -- approval_requested/approval_received/
                                           -- completed/failed/cancelled/timeout/delivered
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    system_prompt   TEXT,
    user_prompt     TEXT,
    tool_name       VARCHAR(128),
    tool_args       TEXT,
    tool_result     TEXT,
    tool_duration_ms BIGINT,
    llm_request     TEXT,       -- full prompt sent to model
    llm_response    TEXT,       -- full model response
    token_usage     TEXT,       -- JSON
    error_message   TEXT,
    error_trace     TEXT,
    duration_ms     BIGINT,
    metadata        TEXT        -- JSON for extra context
);
CREATE INDEX idx_task_audit_task_id ON task_audit_log (task_id);
CREATE INDEX idx_task_audit_execution ON task_audit_log (execution_id);
CREATE INDEX idx_task_audit_type ON task_audit_log (event_type);
```

### delivery_audit_log (NEW)

```sql
CREATE TABLE delivery_audit_log (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    task_id         VARCHAR(256),
    conversation_id VARCHAR(256) NOT NULL,
    channel_name    VARCHAR(64) NOT NULL,
    message         TEXT NOT NULL,
    status          VARCHAR(16) NOT NULL,  -- delivered/failed
    attempts        INT NOT NULL DEFAULT 1,
    error_message   TEXT,
    duration_ms     BIGINT,
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_delivery_audit_task ON delivery_audit_log (task_id);
CREATE INDEX idx_delivery_audit_conv ON delivery_audit_log (conversation_id);
```

### AuditTool (доступен агенту)

```
@Tool: getTaskAudit(taskId) → полная история task execution из task_audit_log
@Tool: getChatAudit(conversationId, limit) → история chat requests из chat_audit_log
@Tool: getDeliveryAudit(taskId) → история доставок из delivery_audit_log
```

Пользователь спрашивает "Что случилось с задачей X?" → агент вызывает getTaskAudit → показывает:
- Когда создана, кем
- Какой prompt отправлен в LLM
- Какие tools вызваны (с аргументами и результатами)
- Что ответила модель
- Была ли ошибка (message + stacktrace)
- Сколько заняло (duration_ms)
- Статус доставки нотификации

---

## Новые таблицы (summary)

### V15: task_executions

### V16: delivery_queue

### V17: extend tasks (parent_task_id, notify_policy, runtime_type, timeout_seconds, carry_over_context, user_id, cancelled_at)

### V18: extend recurring_tasks (notify_policy, timeout_seconds, carry_over_context)

### V19: approval_requests

```sql
CREATE TABLE approval_requests (
    id              VARCHAR(256) PRIMARY KEY,
    task_id         VARCHAR(256) NOT NULL REFERENCES tasks(id),
    conversation_id VARCHAR(256) NOT NULL,
    question        TEXT NOT NULL,
    response        TEXT,
    status          VARCHAR(16) NOT NULL DEFAULT 'pending',  -- pending/approved/denied/timeout
    timeout_at      TIMESTAMP NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    responded_at    TIMESTAMP
);
CREATE INDEX idx_approval_pending ON approval_requests (conversation_id, status) WHERE status = 'pending';
```

### V20: task_audit_log + delivery_audit_log

### V21: extend chat_audit_log (user_id, tool_calls_detail, token_usage)

### V22: rate_limits (если не через конфиг)

---

## Файлы

### Новые (javaclaw-core)

- `agent/event/EventBus.java` — interface
- `agent/event/SpringEventBus.java` — ApplicationEventPublisher impl
- `agent/event/AgentEvent.java` — record
- `agent/event/EventKind.java` — enum
- `agent/audit/TaskAuditService.java` — writes task_audit_log
- `agent/audit/TaskAuditLog.java` — entity
- `agent/audit/TaskAuditLogRepository.java`
- `agent/audit/DeliveryAuditLog.java` — entity
- `agent/audit/DeliveryAuditLogRepository.java`
- `tasks/TaskExecution.java` — entity
- `tasks/TaskExecutionRepository.java`
- `tasks/TaskWatchdog.java` — @Scheduled
- `tasks/TaskRateLimiter.java`
- `tasks/NotifyPolicy.java` — enum
- `tasks/TaskRuntime.java` — enum
- `tasks/ApprovalService.java` — human-in-the-loop
- `tasks/ApprovalRequest.java` — entity
- `tasks/ApprovalRequestRepository.java`
- `delivery/DeliveryService.java`
- `delivery/DeliveryQueue.java` — entity
- `delivery/DeliveryQueueRepository.java`
- `delivery/DeliveryRecovery.java` — startup recovery
- `delivery/NotificationTransport.java` — interface
- `delivery/InMemoryNotificationTransport.java`
- `tools/AuditTool.java` — @Tool getTaskAudit, getChatAudit, getDeliveryAudit

### Новые (javaclaw-api-chat)

- `PgNotificationTransport.java`
- `NotificationTransportConfiguration.java`

### Новые (javaclaw-frontend)

**Hooks:**
- `hooks/use-task-notifications.ts` — EventSource SSE подписка на task events
- `hooks/use-approval.ts` — логика approve/deny/timeout для pending approvals

**Chat message types (новые компоненты внутри assistant-message):**
- `components/chat/task-notification-message.tsx` — визуально выделенная нотификация о задаче:
- Status badge: completed (green), failed (red), cancelled (grey), in_progress (blue), awaiting_input (orange)
- Иконка по типу: ✅ completed, ❌ failed, ⏳ in_progress, 🔔 awaiting_input
- Expandable details: task name, duration, feedback text
- Кнопка "Подробнее" → вызывает AuditTool через чат

- `components/chat/approval-request-card.tsx` — карточка запроса одобрения:
  - Вопрос агента крупным текстом
  - Countdown timer до timeout (визуальная полоса, цвет меняется: зелёный → жёлтый → красный)
  - Кнопки: "Одобрить" (green) / "Отклонить" (red) / "Подробнее" (grey)
  - Quick-reply input: пользователь может написать текстовый ответ вместо кнопки
  - After action: кнопки заменяются на status pill ("Одобрено ✓" / "Отклонено ✗" / "Истекло ⏰")
  - Disabled state когда approval resolved
- `components/chat/task-progress-card.tsx` — progress update для long-running tasks:
  - Progress bar (determinate если % известен, indeterminate если нет)
  - Текст прогресса: "Обработано 3 из 5..."
  - Кнопка "Отменить задачу"
  - Collapse/expand предыдущие progress updates
- `components/chat/task-error-card.tsx` — ошибка задачи:
  - Red border + error icon
  - Error message (краткое)
  - Expandable: полный стектрейс, request к модели, duration
  - Кнопки: "Повторить" / "Показать аудит"

**Conversation list enhancements:**
- `components/chat/conversation-badge.tsx` — badge на conversation в sidebar:
- Unread notification count (число непрочитанных task notifications)
- Pending approval indicator (оранжевая точка)
- Active task indicator (пульсирующий синий индикатор)

**Task management UI:**
- `components/tasks/task-list-panel.tsx` — боковая панель со списком задач:
- Фильтры: All / Active / Recurring / Completed / Failed
- Каждая задача: name, status badge, created_at, last execution time
- Parent-child: дочерние задачи с indent под parent
- Actions: Cancel, Delete, View Audit
- Pull-to-refresh / auto-refresh

- `components/tasks/task-detail-dialog.tsx` — диалог деталей задачи:
  - Full audit trail timeline (created → started → tool calls → completed/failed)
  - Каждый event: timestamp, duration, expandable details
  - LLM request/response viewer (collapsible JSON/text)
  - Tool calls: name, args, result, duration
  - Delivery history: attempts, status, channel

**Toast notifications (Sonner integration):**
- Task completed → success toast with task name + link to conversation
- Task failed → error toast with retry action
- Approval requested → persistent toast with "Go to chat" action
- Rate limit hit → warning toast

**Store:**
- `store/tasks.ts` — Jotai atoms: pendingApprovals, activeTaskCount, unreadNotifications
- `store/notifications.ts` — notification queue for toasts

### Новые REST endpoints (backend)

- `GET /api/tasks` — список задач пользователя (фильтры: status, type)
- `GET /api/tasks/{id}` — детали задачи
- `GET /api/tasks/{id}/audit` — audit trail задачи
- `POST /api/tasks/{id}/cancel` — отмена задачи
- `DELETE /api/tasks/{id}` — удаление задачи
- `GET /api/tasks/{id}/executions` — история выполнений
- `POST /api/chat/approval/{approvalId}/respond` — ответ на approval (JSON body: {response, approved})
- `GET /api/chat/approval/pending?conversationId=X` — pending approvals для conversation
- `GET /api/chat/notifications/{conversationId}` — SSE endpoint для push
- `GET /api/audit/chat?conversationId=X&limit=N` — chat audit log

### Новые frontend API modules

- `api/tasks.ts` — CRUD задач, cancel, audit
- `api/approvals.ts` — respond to approval, list pending
- `api/audit.ts` — chat/task/delivery audit queries

### Изменяемые

- `TaskHandler.java` — полный рефакторинг
- `TaskManager.java` — spawn, cancel, notifyPolicy, runtimeType, timeout, carryOver, rateLimiter
- `Task.java` — все новые поля
- `RecurringTask.java` — notifyPolicy, timeout, carryOver
- `TaskTool.java` — spawnTask, cancelTask, listTasks расширения + AuditTool
- `ChatService.java` — emit events, check ApprovalService before normal flow
- `ChatChannel.java` — outbound через DeliveryService
- `ChatRestController.java` — SSE notifications endpoint
- `ChatAuditService.java` — расширить tool_calls_detail, token_usage, user_id
- `chat-page.tsx` — подключить useTaskNotifications + approval UI

### Migrations

V15–V22 (8 миграций)

---

## Изоляция пользователей

### Данные

- Все таблицы с пользовательскими данными содержат `user_id` FK:
  - `conversations.user_id` — уже есть
  - `tasks.user_id` — добавляем (V17)
  - `recurring_tasks.user_id` — добавляем (V18)
  - `approval_requests` — через task_id → tasks.user_id
  - `delivery_queue` — через task_id → tasks.user_id
  - `virtual_files.owner_id` — уже есть (NULL = global)
  - `spring_ai_chat_memory` — через conversation_id → conversations.user_id
- **Все запросы фильтруются по user_id.** Repository методы: `findByUserId()`, не `findAll()`.
- Task не может читать/писать данные другого пользователя.
- Audit logs содержат `user_id` для фильтрации.

### Сессии

- Session key включает userId: `"agent:default:{channel}:{userId}"`
- ChatMemory: conversation привязана к user, FK enforced
- Task execution: TaskHandler получает userId из Task → все LLM вызовы в scope этого user

### Tools

- ToolCallbackResolver может фильтровать tools per user:
  - `UserToolPermission` таблица: user_id, tool_name, allowed (boolean)
  - Или role-based: admin → all tools, user → restricted set
  - TaskTool при spawn subtask: restricted tools (без spawn/cancel для depth > 1)
- Per-user virtual files (AGENT.md с owner_id = userId → персональный промпт)

### Rate limits

- Per-user: max_concurrent_tasks, max_recurring_tasks, max_tasks_per_hour
- Хранятся в конфиге или в `user_settings` таблице
- TaskRateLimiter проверяет перед каждым create/schedule/spawn

### Безопасность

- Channel adapters аутентифицируют userId:
  - Web Chat: Basic Auth → userId (уже есть)
  - Telegram: chatId mapping → userId
  - Discord: discordUserId mapping → userId
- TaskHandler: verify task.userId == currentUser (или system for cron)
- ApprovalService: только owner задачи может approve/deny
- AuditTool: только свои audit records (фильтр по userId)

---

## Масштабирование подов (shared PostgreSQL)

### Архитектурные гарантии

**Stateless pods.** Каждый pod — stateless реплика. Все состояние в PostgreSQL.

**Shared resources через PostgreSQL:**
- Таблицы: conversations, tasks, spring_ai_chat_memory, task_executions, delivery_queue, approval_requests, audit logs — всё в одной БД
- JobRunr: использует PostgreSQL storage provider → job scheduling координируется через БД, только один pod берёт конкретный job
- Flyway: `spring.flyway.enabled=true` с advisory lock → только один pod выполняет миграции

**Cross-pod communication через PG NOTIFY:**
- Task завершается на pod A → `NOTIFY task_notifications` → pod B (где открыт SSE) получает → push в browser
- Approval response приходит на pod A → `NOTIFY approval_responses` → pod B (где ждёт CompletableFuture) получает → task resume
- Все pods делают `LISTEN` на startup

**Concurrency safety:**
- TaskHandler semaphore: per-pod (JVM), но max_concurrent_tasks проверяется через DB count

```sql
SELECT COUNT(*) FROM tasks WHERE status = 'in_progress' AND user_id = ?
```

- Optimistic locking на Task entity (`@Version` column) — предотвращает двойное выполнение
- approval_requests: `SELECT ... FOR UPDATE SKIP LOCKED` при submitApproval → только один pod обрабатывает

**JobRunr distribution:**
- JobRunr BackgroundJobServer на каждом pod
- Server ZooKeeper election: один pod = master (dashboard, recurring scheduling)
- Job execution: любой pod может взять enqueued job
- Retry: если pod падает mid-execution → JobRunr переназначает на другой pod

**SSE connections:**
- Каждый browser SSE connection привязан к конкретному pod (через load balancer)
- PG NOTIFY доставляет событие на ВСЕ pods → только pod с активным SSE connection для данного conversationId отправляет в browser
- Если SSE connection обрывается → browser автоматически reconnect (EventSource built-in)

**Delivery queue processing:**
- DeliveryRecovery на startup: каждый pod проверяет pending deliveries
- `SELECT ... FOR UPDATE SKIP LOCKED` → только один pod берёт delivery → предотвращает дубликаты
- Idempotency: delivery_queue.id как idempotency key для external channels

### Новые constraints для multi-pod

```sql
-- Optimistic locking
ALTER TABLE tasks ADD COLUMN version INT NOT NULL DEFAULT 0;

-- Approval cross-pod notification
-- (PG NOTIFY channel: 'approval_responses')

-- Delivery deduplication
ALTER TABLE delivery_queue ADD COLUMN claimed_by VARCHAR(64);  -- pod instance id
ALTER TABLE delivery_queue ADD COLUMN claimed_at TIMESTAMP;
```

### Тесты масштабирования

- T61: два pod'а запущены → JobRunr job выполняется только на одном
- T62: task завершается на pod A → SSE push приходит на pod B → browser получает
- T63: approval response на pod A → task на pod B возобновляется
- T64: оба pod'а стартуют → миграции выполняются один раз
- T65: pod падает mid-task → JobRunr retry на другом pod'е
- T66: delivery_queue claimed_by → только один pod обрабатывает delivery
- T67: optimistic locking → concurrent update → один succeeds, другой retries

---

## Порядок имплементации

### Phase 1: Foundations

- EventBus interface + Spring impl
- NotifyPolicy, TaskRuntime enums
- Task entity extensions + migrations V17, V18
- TaskExecution entity + migration V15

### Phase 2: Task isolation + execution

- TaskHandler refactor: chatModel напрямую, task_executions
- Restricted tool sets for subtasks
- CancellationToken mechanism
- TaskAuditService + migration V20

### Phase 3: Task hierarchy

- parent_task_id + TaskManager.spawn()
- Depth limit (3) + concurrency semaphore (5)
- Child result injection into parent context

### Phase 4: Human-in-the-loop

- ApprovalService + approval_requests table + migration V19
- ChatService integration: check pending approvals
- ApprovalRequest timeout in TaskWatchdog

### Phase 5: Delivery pipeline

- DeliveryService + delivery_queue + migration V16
- NotificationTransport interface + PgNotify + InMemory
- Startup recovery (DeliveryRecovery)
- DeliveryAuditLog

### Phase 6: Watchdog + rate limiting

- TaskWatchdog @Scheduled
- TaskRateLimiter
- Failed status handling + timeout notifications

### Phase 7: Audit expansion

- Extend chat_audit_log (V21)
- AuditTool (@Tool for agent)
- Token usage tracking

### Phase 8: Backend REST API

- Task CRUD endpoints (GET/POST/DELETE /api/tasks)
- Task cancel endpoint (POST /api/tasks/{id}/cancel)
- Task audit endpoint (GET /api/tasks/{id}/audit)
- Approval respond endpoint (POST /api/chat/approval/{id}/respond)
- Pending approvals endpoint (GET /api/chat/approval/pending)
- SSE notifications endpoint (GET /api/chat/notifications/{conversationId})
- Chat audit endpoint (GET /api/audit/chat)

### Phase 9: Frontend — Core

- use-task-notifications.ts (EventSource SSE hook)
- use-approval.ts (approve/deny/timeout logic)
- api/tasks.ts, api/approvals.ts, api/audit.ts (API modules)
- store/tasks.ts, store/notifications.ts (Jotai atoms)
- Sonner toast integration for task events

### Phase 10: Frontend — Chat Message Components

- task-notification-message.tsx (status badge, icon, expandable details)
- approval-request-card.tsx (countdown timer, approve/deny buttons, quick-reply)
- task-progress-card.tsx (progress bar, cancel button)
- task-error-card.tsx (error details, retry button, audit link)
- conversation-badge.tsx (unread count, pending approval dot, active task indicator)
- Integration into assistant-message.tsx and chat-page.tsx

### Phase 11: Frontend — Task Management

- task-list-panel.tsx (Active/Recurring/History tabs, parent-child hierarchy)
- task-detail-dialog.tsx (timeline, LLM request/response viewer, tool calls, delivery)
- Admin: "all users" toggle in task panel

### Phase 12: Recurring task context carry-over

- carryOverContext flag on RecurringTask
- TaskHandler loads previous execution summaries when carryOver=true

### Phase 13: E2E Tests (Playwright)

- E1-E31: full Playwright test suite covering all user stories
- Test fixtures: mock users (admin + regular), mock tasks, mock approvals
- CI pipeline: run E2E after all phases complete

---

## Тестовые сценарии (полное покрытие)

### Unit Tests

**TaskHandler:**
- T1: executeTask → prompt goes to task_executions, NOT to spring_ai_chat_memory
- T2: executeTask → uses restricted tool set (no spawn for depth=3)
- T3: executeTask → CancellationToken checked → throws on cancel
- T4: executeTask → error → task.status=failed + error in task_executions
- T5: executeTask → emits TURN_START, LLM_REQUEST, LLM_RESPONSE, TURN_END events
- T6: executeTask → semaphore full → waits, then timeout → exception
- T7: executeTask with carryOverContext=true → includes previous execution summaries

**TaskManager:**
- T8: create() → sets notifyPolicy, timeout, userId, conversationId
- T9: spawn() → sets parentTaskId, inherits conversationId from parent
- T10: spawn() → depth > 3 → throws
- T11: cancel() → sets cancelled status, cancels JobRunr job
- T12: scheduleRecurrently() → sets carryOverContext flag
- T13: create() → rateLimiter.checkLimit → throws when exceeded

**ApprovalService:**
- T14: requestApproval() → saves to DB, delivers question, blocks on Future
- T15: submitApproval() → completes Future, task resumes
- T16: approval timeout → auto-deny, task continues with "timed out"
- T17: no pending approval → normal chat flow proceeds

**DeliveryService:**
- T18: deliver with SILENT policy → nothing happens
- T19: deliver with DONE_ONLY + completed → delivers
- T20: deliver with DONE_ONLY + in_progress → skips
- T21: deliver with ON_ERROR + failed → delivers
- T22: deliver with ON_ERROR + completed → skips
- T23: deliver with ON_SUCCESS + completed → delivers
- T24: deliver with STATE_CHANGES → delivers on every status change
- T25: deliver to Web Chat → NotificationTransport.broadcast() called
- T26: deliver to Telegram → delivery_queue INSERT + channel.sendMessage()
- T27: deliver failure → retry with exponential backoff
- T28: deliver max retries exceeded → status=failed in delivery_queue
- T29: deliver → persists notification in user's chat memory
- T30: deliver → saves to delivery_audit_log

**TaskWatchdog:**
- T31: task in_progress past timeout → marked failed + notification sent
- T32: approval_request past timeout → auto-denied
- T33: task within timeout → not touched

**TaskRateLimiter:**
- T34: under limit → passes
- T35: at concurrent limit → throws
- T36: at hourly limit → throws
- T37: at recurring limit → throws

**EventBus:**
- T38: emit → all subscribers receive
- T39: subscribe with filter → only matching events
- T40: subscriber slow → event dropped, not blocking

**AuditTool:**
- T41: getTaskAudit → returns full execution history
- T42: getChatAudit → returns chat requests with tool details
- T43: getDeliveryAudit → returns delivery attempts

**NotificationTransport:**
- T44: PgNotifyTransport.broadcast() → NOTIFY issued
- T45: PgNotifyTransport.subscribe() → receives NOTIFY events
- T46: InMemoryTransport.broadcast() → ApplicationEvent published
- T47: InMemoryTransport.subscribe() → receives events

### Integration Tests

**Task Execution Flow:**
- T48: create recurring task → JobRunr fires → task_executions populated → user chat clean
- T49: parent task spawns 2 children → both execute → results injected → parent completes
- T50: task requests approval → message delivered → user responds → task resumes → completes
- T51: task exceeds timeout → watchdog marks failed → error notification delivered
- T52: task cancelled mid-execution → graceful stop → cancellation notification
- T53: recurring task with carryOverContext → second execution sees first's summary
- T54: server restart → pending deliveries recovered → notifications sent

**Delivery Integration:**
- T55: Web Chat delivery → SSE event received by test client
- T56: PG NOTIFY → second pod receives → SSE push (requires 2 instances or simulated)
- T57: Telegram delivery failure → retry 3x → marked failed in delivery_queue

**Audit Integration:**
- T58: full task lifecycle → all events in task_audit_log with correct types
- T59: chat with tool calls → tool_calls_detail populated in chat_audit_log
- T60: user asks "what happened with task X" → AuditTool returns full history

### E2E Tests (Browser / Playwright) — User Stories

**Chat Basics:**
- E1: send message → streaming dots appear → response renders with markdown → message in history
- E2: refresh page → all previous messages rendered correctly (user + assistant)
- E3: create new conversation → empty chat → send message → response → conversation appears in sidebar

**Task Notification UI:**
- E4: "Напомни через 1 минуту выпить воды"
→ Agent confirms: task-notification-card appears (blue, ⏳ scheduled)
→ After 1 min: task-notification-card appears (green, ✅ completed) with reminder text
→ Notification appears WITHOUT page refresh (SSE push)
→ Sonner toast appears in top-right corner

- E5: task fails (e.g., LLM timeout)
  → task-error-card appears in chat (red border, ❌ icon)
  → Error message shown (краткое)
  → "Подробнее" button → expandable: full error, request to model, stacktrace
  → "Повторить" button → creates new task with same params
  → Sonner error toast with "Открыть чат" action

- E6: conversation sidebar
  → Badge shows unread notification count (number)
  → Switch to conversation → badge resets to 0
  → Active task → pulsing blue dot on conversation in sidebar

**Recurring Tasks UI:**
- E7: "Присылай погоду каждый день в 9 утра"
→ Agent confirms with task-notification-card (recurring icon 🔄)
→ Next morning: notification arrives in correct conversation
→ Second day: new notification, different from first (carryOverContext works)

- E8: "Покажи мои повторяющиеся задачи"
  → Agent lists: name, cron expression (human-readable), last execution, status
  → "Удали задачу X" → agent confirms → no more notifications

**Approval Request UI:**
- E9: "Проверь цену на билет и купи если < 10000"
→ Task starts → agent finds price 9500
→ approval-request-card appears in chat:
- Question: "Найден билет за 9500₽. Купить?"
- Countdown bar (green → yellow → red, 60s timeout)
- Buttons: [Одобрить ✓] [Отклонить ✗]
- Quick-reply text input
→ User clicks "Одобрить"
→ Buttons replaced by "Одобрено ✓" pill (green, disabled)
→ Task continues → completion notification arrives

- E10: approval timeout
  → approval-request-card countdown reaches 0
  → Buttons replaced by "Истекло ⏰" pill (grey, disabled)
  → Task receives timeout → handles gracefully

- E11: approval with text response
  → User types "Да, но только эконом класс" in quick-reply input → sends
  → Task receives text response → uses it for decision

**Task Progress UI:**
- E12: "Проанализируй 5 конкурентов"
→ task-progress-card appears: progress bar (0%), "Начинаю анализ..."
→ Progress updates: "Competitor 1/5..." (20%), "Competitor 2/5..." (40%)
→ Each update: progress bar animates, text changes
→ "Отменить" button visible on progress card
→ Final: task-notification-card (completed) with full report

- E13: cancel mid-progress
  → User clicks "Отменить" on progress card
  → Confirmation dialog: "Отменить задачу 'Анализ конкурентов'?"
  → Confirm → task-notification-card (grey, 🚫 cancelled)

**Parent-Child Task UI:**
- E14: parent task spawns children
→ Parent progress card shows child tasks as nested items
→ Each child: name, status badge, duration
→ Child completes → its status updates in parent card
→ All children complete → parent produces final result

**Task Management Panel:**
- E15: open task panel (sidebar or dedicated page)
→ Tabs: Active | Recurring | History
→ Active tab: in_progress tasks with progress bars
→ Recurring tab: cron tasks with next execution time
→ History tab: completed/failed with date filter
→ Each task row: click → task-detail-dialog opens

- E16: task-detail-dialog
  → Timeline view: created → started → tool_call_1 → tool_call_2 → completed
  → Each event: timestamp, duration badge
  → Expand event → full details (LLM request/response, tool args/result)
  → "Запрос к модели" tab: raw system prompt + user prompt
  → "Ответ модели" tab: raw LLM response
  → "Инструменты" tab: list of tool calls with args/results
  → Delivery history: channel, attempts, status

**Error & Audit UI:**
- E17: user asks "Что случилось с задачей X?"
→ Agent calls AuditTool → shows structured audit:
- Task name, status, created/finished timestamps
- Error message (if failed)
- Full request that was sent to model
- Model's response
- Tool calls with args and results
- Duration breakdown

- E18: user asks "Покажи последние 5 запросов к модели"
  → Agent calls getChatAudit → shows table:
  - Timestamp, method (stream/call), duration_ms
  - User content (truncated)
  - Response (truncated)
  - Tools used
  - Token usage (prompt/completion/total)
  - Error (if any)

**Notification Routing:**
- E19: create task in conversation A → switch to conversation B
→ Task completes → notification appears ONLY in conversation A
→ Conversation A badge shows "1" in sidebar
→ Switch back to A → notification visible, badge resets

- E20: pending approval in conversation A → switch to B
  → Persistent toast: "Ожидает ответа в чате 'ConvA'" with "Перейти" button
  → Click "Перейти" → switches to conversation A → approval card visible

**Rate Limiting:**
- E21: create 15 tasks quickly
→ First 10 succeed → confirmations
→ 11th: agent says "Превышен лимит: максимум 10 одновременных задач. Текущие задачи: ..."
→ Warning toast

**User Isolation:**
- E22: login as user A → create task → logout → login as user B
→ "Покажи мои задачи" → empty list (user B has no tasks)

- E23: user A's recurring task fires → notification does NOT appear for user B

- E24: user A opens task-detail-dialog for own task → audit visible
  → API call for user B's task_id → 403 Forbidden

- E25: admin user → task panel shows "all users" filter toggle
  → Regular user → no such toggle, only own tasks

**Multi-Channel:**
- E26: user creates task from Telegram → notification arrives in Telegram (not Web Chat)
- E27: user creates task from Web Chat → notification arrives in Web Chat (not Telegram)
- E28: same user, per_channel_peer mode → Telegram and Web Chat have SEPARATE conversation histories

**Scaling:**
- E29: start 2 pods → both healthy → task execution distributed
- E30: task executes on pod B → SSE notification arrives on pod A (user's browser)
- E31: kill pod A → user reconnects to pod B → all data intact, SSE reconnects

---

## Верификация (manual checklist after all phases)

1. S1-S20 все пользовательские сценарии работают end-to-end
2. T1-T67 все unit/integration тесты зелёные
3. E1-E31 все E2E тесты проходят (Playwright)
4. Нет raw task промптов в user conversation (проверить spring_ai_chat_memory)
5. task_audit_log содержит полную историю для каждого task execution
6. delivery_audit_log содержит все попытки доставки
7. chat_audit_log содержит tool_calls_detail и token_usage
8. PG NOTIFY работает cross-pod (тест с двумя инстансами)
9. InMemoryTransport работает с H2
10. Startup recovery обрабатывает pending deliveries
11. Watchdog ловит зависшие задачи и timed-out approvals
12. Rate limiter блокирует excessive task creation
13. Per-user tool restrictions работают (admin vs regular user)
14. User A не видит данные user B (задачи, аудит, нотификации)
15. Multi-channel: task из Telegram → notification в Telegram, не в Web Chat
16. 2 pod'а: task на pod A → SSE push на pod B → browser получает
17. 2 pod'а: approval response на pod A → task на pod B возобновляется
18. Pod crash → JobRunr retry → task завершается на другом pod'е
19. Delivery deduplication: claimed_by предотвращает двойную доставку

