# Audit: ID Generation Strategy

**Date:** 2026-04-11
**Scope:** All Flyway migrations with `DEFAULT gen_random_uuid()` + Spring Data JDBC entities
**Goal:** Identify which entities lack a `BeforeConvertCallback` and rely solely on the PostgreSQL DB default for UUID generation

---

## 1. Таблицы с `DEFAULT gen_random_uuid()` в миграциях

|         Таблица          | Колонка |  Тип колонки   |                       Файл миграции                        |
|--------------------------|---------|----------------|------------------------------------------------------------|
| `tasks`                  | `id`    | `VARCHAR(36)`  | `V1__init_tasks.sql`                                       |
| `recurring_tasks`        | `id`    | `VARCHAR(36)`  | `V1__init_tasks.sql`                                       |
| `users`                  | `id`    | `VARCHAR(36)`  | `V4__create_users.sql`                                     |
| `conversations`          | `id`    | `VARCHAR(256)` | `V5__create_conversations.sql` *(нет DEFAULT, id внешний)* |
| `virtual_files`          | `id`    | `VARCHAR(36)`  | `V6__create_virtual_files.sql`                             |
| `skills`                 | `id`    | `VARCHAR(36)`  | `V7__create_skills.sql`                                    |
| `mcp_servers`            | `id`    | `VARCHAR(36)`  | `V8__create_mcp_servers.sql`                               |
| `config`                 | `id`    | `VARCHAR(36)`  | `V9__create_config.sql`                                    |
| `conversation_summaries` | `id`    | `UUID`         | `V24__create_conversation_summaries.sql`                   |
| `tool_examples`          | `id`    | `VARCHAR(36)`  | `V25__create_tool_examples.sql`                            |
| `memories`               | `id`    | `TEXT`         | `V27__create_memories.sql`                                 |
| `skill_role_allowlist`   | `id`    | `VARCHAR(36)`  | `V33__skill_role_allowlist.sql`                            |
| `role_agent_config`      | `id`    | `VARCHAR(36)`  | `V36__role_agent_config.sql`                               |
| `role_model_allowlist`   | `id`    | `VARCHAR(36)`  | `V38__role_model_allowlist.sql`                            |

> **Примечание:** таблицы `task_executions`, `approval_requests`, `delivery_queue` в миграциях не имеют `DEFAULT gen_random_uuid()` — ID генерируется через существующие `BeforeConvertCallback`.
> Таблица `mcp_role_allowlist` (V34) не имеет `DEFAULT gen_random_uuid()` в DDL — id приходит `null` из `McpRoleAllowlist.create()`, нужен callback.
> Таблица `config` не имеет Spring Data JDBC сущности (нет `@Table("config")` ни в одном Java-файле) — используется через иные механизмы (не является объектом Phase 2).
> Таблица `conversations` (V5) не имеет `DEFAULT gen_random_uuid()` — id всегда задаётся внешне (`"web-<uuid>"`, Telegram thread id и т.д.) через `Conversation.newWithId()`.

---

## 2. Полная таблица: сущность → callback → действие

|            Таблица             |        Java-сущность         |               Файл сущности                |              @Id тип               |                    Уже есть callback?                    |           Действие           |
|--------------------------------|------------------------------|--------------------------------------------|------------------------------------|----------------------------------------------------------|------------------------------|
| `tasks`                        | `Task`                       | `tasks/Task.java`                          | `String`                           | ДА — `TaskIdGeneratorCallback`                           | OK                           |
| `recurring_tasks`              | `RecurringTask`              | `tasks/RecurringTask.java`                 | `String`                           | ДА — `RecurringTaskIdGeneratorCallback`                  | OK                           |
| `task_executions`              | `TaskExecution`              | `tasks/TaskExecution.java`                 | `String`                           | ДА — `TaskExecutionIdGeneratorCallback`                  | OK                           |
| `approval_requests`            | `ApprovalRequest`            | `tasks/ApprovalRequest.java`               | `String`                           | ДА — `ApprovalRequestIdGeneratorCallback`                | OK                           |
| `delivery_queue`               | `DeliveryQueue`              | `delivery/DeliveryQueue.java`              | `String`                           | ДА — `DeliveryQueueIdGeneratorCallback`                  | OK                           |
| `users`                        | `AppUser`                    | `users/AppUser.java`                       | `String`                           | НЕТ                                                      | **Нужен callback (Phase 2)** |
| `virtual_files`                | `VirtualFile`                | `files/VirtualFile.java`                   | `String`                           | НЕТ                                                      | **Нужен callback (Phase 2)** |
| `skills`                       | `Skill`                      | `skills/Skill.java`                        | `String`                           | НЕТ                                                      | **Нужен callback (Phase 2)** |
| `mcp_servers`                  | `McpServer`                  | `mcp/McpServer.java`                       | `String`                           | НЕТ                                                      | **Нужен callback (Phase 2)** |
| `conversation_summaries`       | `ConversationSummary`        | `agent/pipeline/ConversationSummary.java`  | `String`                           | НЕТ                                                      | **Нужен callback (Phase 2)** |
| `tool_examples`                | `ToolExample`                | `agent/pipeline/ToolExample.java`          | `String`                           | НЕТ                                                      | **Нужен callback (Phase 2)** |
| `memories`                     | `Memory`                     | `memory/Memory.java`                       | `String`                           | НЕТ                                                      | **Нужен callback (Phase 2)** |
| `skill_role_allowlist`         | `SkillRoleAllowlist`         | `skills/SkillRoleAllowlist.java`           | `String`                           | НЕТ                                                      | **Нужен callback (Phase 2)** |
| `role_agent_config`            | `RoleAgentConfig`            | `agent/config/RoleAgentConfig.java`        | `String`                           | НЕТ                                                      | **Нужен callback (Phase 2)** |
| `role_model_allowlist`         | `RoleModelAllowlist`         | `agent/config/RoleModelAllowlist.java`     | `String`                           | НЕТ                                                      | **Нужен callback (Phase 2)** |
| `mcp_role_allowlist`           | `McpRoleAllowlist`           | `mcp/McpRoleAllowlist.java`                | `String`                           | НЕТ                                                      | **Нужен callback (Phase 2)** |
| `user_session`                 | `UserSession`                | `security/session/UserSession.java`        | `String`                           | НЕТ (id генерится вручную в `OpaqueSessionTokenService`) | **Особый случай — см. ниже** |
| `conversations`                | `Conversation`               | `conversations/Conversation.java`          | `String`                           | НЕТ (id всегда внешний)                                  | OK — не нужен                |
| `conversation_channel_context` | `ConversationChannelContext` | `channels/ConversationChannelContext.java` | `String` (`conversationId`)        | НЕТ (id = conversation id, задаётся явно)                | OK — не нужен                |
| `config`                       | *(нет сущности)*             | —                                          | —                                  | —                                                        | Вне области Phase 2          |
| `agent_quotas`                 | `AgentQuota`                 | `agent/quota/AgentQuota.java`              | `Long` (`BIGINT GENERATED ALWAYS`) | Не нужен — DB sequence                                   | OK                           |
| `chat_audit_log`               | `ChatAuditLog`               | `agent/audit/ChatAuditLog.java`            | `Long` (`BIGINT GENERATED ALWAYS`) | Не нужен — DB sequence                                   | OK                           |
| `auth_audit_log`               | `AuthAuditLog`               | `agent/audit/AuthAuditLog.java`            | `Long` (`BIGINT GENERATED ALWAYS`) | Не нужен — DB sequence                                   | OK                           |
| `task_audit_log`               | `TaskAuditLog`               | `agent/audit/TaskAuditLog.java`            | `Long` (`BIGINT GENERATED ALWAYS`) | Не нужен — DB sequence                                   | OK                           |
| `delivery_audit_log`           | `DeliveryAuditLog`           | `agent/audit/DeliveryAuditLog.java`        | `Long` (`BIGINT GENERATED ALWAYS`) | Не нужен — DB sequence                                   | OK                           |
| `skill_usage_audit`            | `SkillUsageAudit`            | `skills/SkillUsageAudit.java`              | `Long` (`BIGINT GENERATED ALWAYS`) | Не нужен — DB sequence                                   | OK                           |
| `custom_roles`                 | `CustomRole`                 | `users/CustomRole.java`                    | `Long` (`BIGINT GENERATED ALWAYS`) | Не нужен — DB sequence                                   | OK                           |
| `role_permissions`             | `RolePermission`             | `users/RolePermission.java`                | `Long` (`BIGINT GENERATED ALWAYS`) | Не нужен — DB sequence                                   | OK                           |
| `conversation_shares`          | `ConversationShare`          | `conversations/ConversationShare.java`     | `Long` (`BIGSERIAL`)               | Не нужен — DB sequence                                   | OK                           |

---

## 3. Сущности, которым нужен новый BeforeConvertCallback (Phase 2)

Все передают `null` в конструктор через статический factory-метод и полагаются на `DEFAULT gen_random_uuid()` в PostgreSQL. Это не переносимо (SQLite, тесты без PG) и нарушает паттерн проекта.

| #  |     Java-сущность     |            Пакет             |                                             Factory-метод                                             |
|----|-----------------------|------------------------------|-------------------------------------------------------------------------------------------------------|
| 1  | `AppUser`             | `ai.javaclaw.users`          | `AppUser.create(...)` → id=null                                                                       |
| 2  | `VirtualFile`         | `ai.javaclaw.files`          | `VirtualFile.newGlobalFile(...)`, `newUserFile(...)` → id=null                                        |
| 3  | `Skill`               | `ai.javaclaw.skills`         | `Skill.newGlobal(...)` → id=null                                                                      |
| 4  | `McpServer`           | `ai.javaclaw.mcp`            | `McpServer.newGlobal(...)`, `newPersonal(...)` → id=null                                              |
| 5  | `McpRoleAllowlist`    | `ai.javaclaw.mcp`            | `McpRoleAllowlist.create(...)` → id=null *(нет DEFAULT в DDL — без callback вставка упадёт!)*         |
| 6  | `Memory`              | `ai.javaclaw.memory`         | `Memory.create(...)` → id=null                                                                        |
| 7  | `ToolExample`         | `ai.javaclaw.agent.pipeline` | `ToolExample.create(...)` → id=null                                                                   |
| 8  | `ConversationSummary` | `ai.javaclaw.agent.pipeline` | `ConversationSummary.create(...)` → id=null                                                           |
| 9  | `SkillRoleAllowlist`  | `ai.javaclaw.skills`         | `SkillRoleAllowlist.create(...)` → id=null *(DDL имеет DEFAULT, но callback нужен для портируемости)* |
| 10 | `RoleAgentConfig`     | `ai.javaclaw.agent.config`   | `RoleAgentConfig.create(...)` → id=null                                                               |
| 11 | `RoleModelAllowlist`  | `ai.javaclaw.agent.config`   | `RoleModelAllowlist.create(...)` → id=null                                                            |

### Особый случай: `UserSession`

`UserSession` находится в модуле `javaclaw-security`. ID генерируется **вручную** в `OpaqueSessionTokenService.createSession()`:

```java
String sessionId = UUID.randomUUID().toString();
// затем INSERT INTO user_session ... через JdbcTemplate (не через Spring Data save)
```

Сущность `UserSession` не сохраняется через `repository.save()` — используется прямой SQL INSERT. **BeforeConvertCallback не нужен**, но логика корректна и портируема.

---

## 4. Прямые вызовы `gen_random_uuid()` в Java-коде

**Не найдено.** Поиск по всем `.java` файлам в `src/main/java/` дал 0 результатов.

Единственный явный вызов `UUID.randomUUID()` в production-коде вне callback — в `OpaqueSessionTokenService` (см. выше), что является допустимым паттерном.

---

## 5. Критический риск: McpRoleAllowlist

Таблица `mcp_role_allowlist` (V34) **не имеет** `DEFAULT gen_random_uuid()` в DDL:

```sql
CREATE TABLE mcp_role_allowlist (
    id         VARCHAR(36) PRIMARY KEY,  -- нет DEFAULT!
    ...
);
```

При этом `McpRoleAllowlist.create()` передаёт `id=null`. Это означает, что при попытке сохранить через Spring Data JDBC вставка **упадёт с NOT NULL violation** (или нарушением PK). Приоритет исправления — **критический**.

---

## 6. Итог

- **5 сущностей** с callback: Task, RecurringTask, TaskExecution, ApprovalRequest, DeliveryQueue — OK
- **11 сущностей** без callback: нужно добавить в Phase 2 (список в разделе 3)
- **1 критический баг**: `McpRoleAllowlist` — нет `DEFAULT` в DDL и нет callback
- **0 вызовов** `gen_random_uuid()` в Java-коде
- **10 сущностей** с `Long @Id` (BIGINT/BIGSERIAL GENERATED ALWAYS) — не требуют callback, корректны
- **2 сущности** с внешним ID (`Conversation`, `ConversationChannelContext`) — не требуют callback, корректны

