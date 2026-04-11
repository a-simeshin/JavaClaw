# Стратегическая карта пользовательской функциональности JavaClaw

## Context

После Phase 8-15 (RBAC complete, 1187+ тестов зелёные) накоплено много фич, но нет единого **user-facing чек-листа** — что именно должно работать у живого пользователя end-to-end (UI + API + БД + стриминг). Нужна стратегическая карта, по которой можно пройти и убедиться: «вот это работает у админа, вот это у POWER_USER, вот это у USER». Это основа для:
1. приоритизации ручной/E2E верификации,
2. выявления функциональных дыр между backend и frontend,
3. планирования оставшихся gap-closure задач перед релизом.

---

## 1. Роли и базовые права доступа

|      Роль      |                                                    Ожидаемое поведение                                                    |
|----------------|---------------------------------------------------------------------------------------------------------------------------|
| **USER**       | Чат, свои conversations, свои задачи, свои файлы/настройки, ограниченный набор моделей (если allowlist настроен)          |
| **POWER_USER** | Всё что USER + share conversation, управление skills, личные MCP серверы, расширенные квоты                               |
| **ADMIN**      | Полный доступ: /admin/*, все conversations, все audit, управление ролями/моделями/квотами, bypass CONVERSATION_ACCESS_ALL |

**Критичные файлы:** `PermissionService`, `SecurityConfig`, `RoleModelAllowlistService`, `RoleAgentConfigService`

---

## 2. Карта user-flows (что должно работать)

### 2.1 Аутентификация и сессия

- [ ] Логин через `/login` (форма → Spring Security)
- [ ] Неверный пароль → запись в `auth_audit_log` (V29)
- [ ] Успешный вход → редирект на `/chat`
- [ ] Logout очищает сессию
- [ ] Проверка audit: `GET /api/audit/auth` для ADMIN

### 2.2 Чат (основной поток)

- [ ] Отправка сообщения через `/chat` → SSE-стрим ответа
- [ ] Thinking/reasoning события отображаются в UI (Phase 8.5)
- [ ] Cancel кнопка обрывает стрим (`POST /api/chat/cancel/{id}`, Phase 8.2)
- [ ] Fallback модели работают при ошибке основной (Phase 8.6)
- [ ] USER_SOUL.md / USER_AGENT.md применяется per-user (Phase 8.1)
- [ ] Auto-summarization при переполнении контекста (V24, Phase 8.3)
- [ ] Few-shot examples инжектятся в system prompt (V25, Phase 8.4)
- [ ] Сообщение + ответ пишутся в `chat_memory` и `chat_audit_log`
- [ ] При запрете модели для роли → 403 (V38, RoleModelAllowlistService)
- [ ] При превышении квоты → 429 (V30, AgentQuotaService)

### 2.3 Управление conversations

- [ ] `/conversations` — список своих диалогов (пагинация)
- [ ] Просмотр сообщений, удаление
- [ ] **Share**: POST `/api/conversations/{id}/share` с permission READ/EDIT (V35)
- [ ] Получатель видит shared conversation в своём списке
- [ ] EDIT-permission позволяет дописывать в чат
- [ ] Revoke share (DELETE), список shares (GET)
- [ ] ADMIN видит ВСЕ conversations (CONVERSATION_ACCESS_ALL bypass)

### 2.4 Задачи (Tasks & Approvals)

- [ ] Агент создаёт задачу через `TaskTool` → появляется в `/tasks`
- [ ] Иерархия parent/child отображается
- [ ] Approval flow: задача требует approval → уведомление → admin в `/approvals` или через чат → respond → задача продолжается
- [ ] Cancel задачи работает (POST `/api/tasks/{id}/cancel`)
- [ ] Executions history отображается (V18)
- [ ] Delivery queue доставляет результат обратно в conversation (V20)
- [ ] Audit по задаче: `GET /api/tasks/{id}/audit`
- [ ] Delegation: агент делегирует подзадачу (Phase 12.3, DelegationTool)

### 2.5 Recurring tasks / Cron

- [ ] `/cron` UI: создание/редактирование/пауза/резюме
- [ ] CronTool у агента: validate, update, pause, resume, list, details (V28)
- [ ] `active=false` останавливает выполнение
- [ ] Routing: задача из cron попадает в правильный канал (Discord/Telegram/Web)

### 2.6 Files (virtual + workspace)

- [ ] `/files` — браузинг virtual_files и workspace
- [ ] Редактирование SOUL.md / AGENT.md / USER_SOUL.md / USER_AGENT.md
- [ ] Upload (multipart) и Download (Content-Disposition) работают (Phase 11.3)
- [ ] FileOperationsTool доступен агенту

### 2.7 Skills

- [ ] USER видит только публичные + разрешённые его роли (V33)
- [ ] POWER_USER/ADMIN создают skill через `/admin/skills` или API
- [ ] Visibility toggle (public/private)
- [ ] Allowlist редактируется через REST
- [ ] Выполнение skill пишется в `skill_usage_audit` (V37)
- [ ] `GET /api/skills/audit` — отчёт по использованию для ADMIN

### 2.8 MCP серверы

- [ ] `/admin/mcp` — список серверов + health status (V26, обновляется каждые 60с)
- [ ] Добавление HTTP и stdio сервера
- [ ] Tool discovery cached (5 мин), `GET /api/mcp-servers/tools`
- [ ] Public/private visibility + role allowlist (V34)
- [ ] **Personal MCP servers**: POWER_USER/USER добавляет свой → доступен только ему
- [ ] A2A discovery: `/.well-known/agent.json` отдаёт agent card
- [ ] JSON-RPC POST `/api/a2a`: tasks/send, tasks/get, tasks/cancel

### 2.9 Admin панель

- [ ] `/admin/users`: CRUD пользователей, смена роли, сброс пароля
- [ ] `/admin/roles`: создание custom role (V32), редактирование permissions (V31)
- [ ] Per-role agent model (V36): дефолт модель для роли → применяется в чате
- [ ] Per-role model allowlist (V38): добавление/удаление разрешённых моделей
- [ ] `/admin/skills`, `/admin/mcp`, `/admin/prompts` — управление
- [ ] ADMIN видит все audit-логи (chat, auth, skill usage, task)

### 2.10 Settings (пользовательские)

- [ ] `/settings`: смена своего пароля, имени, email
- [ ] Theme (light/dark)
- [ ] Language (i18n)
- [ ] Сохранение в virtual_files

### 2.11 Каналы (Discord / Telegram)

- [ ] Бот в Discord отвечает на сообщения, создаёт conversation с `source_channel`
- [ ] Telegram аналогично
- [ ] Задача, созданная в Discord, при завершении доставляет результат в тот же канал (channel routing, V13)

### 2.12 Memory / Dreamin (V27)

- [ ] Агент использует MemoryTool: store / recall / forget / list / search
- [ ] Recalled memories инжектятся в контекст следующего запроса

---

## 3. Матрица приоритетов для верификации

|     Приоритет     |                              Области                               |            Обоснование            |
|-------------------|--------------------------------------------------------------------|-----------------------------------|
| **P0 (критично)** | 2.1 Auth, 2.2 Чат, 2.4 Tasks+Approvals, 2.9 Admin users/roles      | Без них продукт не работает       |
| **P1 (важно)**    | 2.3 Share, 2.7 Skills, 2.8 MCP, 2.5 Cron, 2.9 role model allowlist | Ключевые enterprise-фичи Phase 13 |
| **P2 (важно)**    | 2.6 Files, 2.10 Settings, 2.11 Каналы, 2.12 Memory                 | Дополнительные сценарии           |

---

## 4. Выявленные потенциальные дыры (для отдельного gap-анализа)

Предварительные риски, требующие проверки на следующем этапе:
1. **UI для approvals** — существует ли отдельный экран или только через `/tasks`?
2. **UI для skill usage audit** — endpoint есть, отображается ли в `/admin`?
3. **UI для per-role model allowlist** — V38 реализована в backend, есть ли экран в `/admin/roles`?
4. **UI для personal MCP servers** — POWER_USER может добавить, но есть ли страница?
5. **Conversation sharing UI** — API готов, есть ли кнопка Share в `/conversations`?
6. **Agent quota UI** — 429 работает, но видит ли пользователь свой лимит?
7. **Memory UI** — агент использует, но пользователь не видит содержимого памяти?
8. **A2A agent card** — правильно ли публикуется personal MCP для внешних агентов?

Эти пункты стоит вынести в отдельный Explore + план.

---

## 5. Verification strategy (как прогонять)

1. **Backend-only проверка (быстро):**
   - Запустить `mvn test` (~1187+ тестов) — убедиться что всё зелёное
   - Curl/HTTPie прогон критичных endpoints из раздела 2 под разными ролями
2. **E2E через UI (финальный цикл):**
   - Поднять Docker Compose стек
   - Войти под ADMIN, USER, POWER_USER
   - Пройти P0 → P1 → P2 по чек-листу
   - Использовать Claude in Chrome или `javaclaw-e2e` для автоматизации критичных path
3. **Gap-анализ:**
   - По каждому пункту раздела 4 — Explore-проверка наличия UI
   - Завести задачи в roadmap Phase 16+

---

## 6. Следующие шаги (после этого плана)

1. Утвердить приоритеты (P0/P1/P2) с техлидом
2. По каждой дыре из раздела 4 — мини-Explore + отдельный план
3. Собрать результаты верификации в единый отчёт «Release Readiness»
4. Решить: что чиним сейчас, что переносим на Phase 16

---

## Критичные файлы для навигации

- Контроллеры: `javaclaw-api/javaclaw-api-chat/**/*Controller.java`, `javaclaw-api/javaclaw-api-admin/**/*Controller.java`
- Security: `javaclaw-core/**/PermissionService.java`, `SecurityConfig.java`
- Миграции: `javaclaw-core/src/main/resources/db/migration/V1__*.sql` … `V38__*.sql`
- Frontend роуты: `javaclaw-frontend/src/routes/**`
- Main pipeline: `ChatRestController → SseStreamingService → ChatService → MessageAssembler`

