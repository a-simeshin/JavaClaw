# Enterprise JavaClaw: Roadmap

> Методология: SDD (Spec-Driven Development) + TDD + DDD
>
> Каждый этап: **Спека → Тесты → Разработка → Тестирование → Фиксация спеки**
>
> Правило: слона едим по частям. Каждая часть — самодостаточный инкремент, который можно запустить и пр��верить.
>
> **Референсные репозитории:** [reference-repos.md](reference-repos.md) — карта аналогов OpenClaw/NullClaw/PicoClaw по каждой фазе

---

## Принципы очерёдности

1. **Снизу вверх** — сначала фундамент (БД, домен), потом API, потом UI
2. **Зависимости** — нельзя строить то, что зависит от ещё не построенного
3. **Каждый шаг — рабочий продукт** — после каждого этапа система запускается и работает
4. **Spec first** — ни строчки кода без спеки. Спека = контракт
5. **Test first** — тесты пишутся ДО реализации. Красные → зелёные
6. **Spec fix last** — после реализации спека обновляется с учётом реальности

---

## Граф зависимостей (что от чего зависит)

```
              ┌─────────────┐
              │  React SPA  │
              │   (1.1-1.5) │
              └──────┬──────┘
                     │ зависит от
              ┌──────▼──────┐
              │   REST API  │
              │  + SSE/AG-UI│
              └──────┬──────┘
                     │ зависит от
       ┌─────────────┼─────────────┐
       │             │             │
┌──────▼──────┐ ┌───▼────┐ ┌──────▼──────┐
│  Basic Auth │ │ Agent  │ │  Virtual FS │
│  (10.0)     │ │  Core  │ │   in DB     │
└──────┬──────┘ │(3.1-3.4)│ │  (4.2)      │
       │        └───┬────┘ └──────┬──────┘
       │            │             │
       └────────────┼─────────────┘
                    │ всё зависит от
             ┌──────▼──────┐
             │  DB Schema  │
             │  + Flyway   │
             │  + Domain   │
             └─────────────┘
```

---

## Статус выполнения (срез на 2026-04-05)

Маркеры: ✅ сделано · ⚠️ частично · ❌ не сделано

|                Пункт                | Статус |                                                            Примечание                                                             |
|-------------------------------------|--------|-----------------------------------------------------------------------------------------------------------------------------------|
| 0.1 Архитектурные решения           | ✅      | React 19 + Vite + TanStack Router, SSE (Vercel AI SDK), Maven multi-module, PostgreSQL                                            |
| 0.2 Чистка кодовой базы             | ✅      | Всё удалено: Onboarding, Playwright, Brave, FileSystemChatMemoryRepository, Pebble/htmx                                           |
| 1.1 Flyway миграции                 | ✅      | V1-V10: users, conversations, virtual_files, skills, mcp_servers, config, seed. 31 тест зелёный.                                  |
| 1.2 Virtual filesystem в DB         | ✅      | VirtualFile JDBC entity + Repository + Service + Seeder. FileController DB-backed. 32 новых теста.                                |
| 1.3 JDBC Chat Memory                | ✅      | JdbcAppendableChatMemoryRepository реализована, @Primary bean                                                                     |
| 1.4 Skills в DB                     | ✅      | Skill JDBC entity + SkillRepository + SkillService. SkillStore удалён. 16 новых тестов.                                           |
| 1.5 MCP-серверы в DB                | ✅      | McpServer JDBC entity + Repository + Service + JSONB converter. McpServerStore удалён. 14 тестов.                                 |
| 1.6 Tasks в DB                      | ✅      | TaskRepository, RecurringTaskRepository (JDBC) + V1 миграция                                                                      |
| 2.1 Agent loop + streaming          | ✅      | DefaultAgent + ChatClient + SseStreamingService (Vercel AI SDK v4)                                                                |
| 2.2 System prompt из DB             | ✅      | SystemPromptProvider читает AGENT.md/SOUL.md/INFO.md из virtual_files (owner_id=NULL). 6 тестов.                                  |
| 2.3 Agent environment               | ✅      | AgentEnvironment с user.dir/.git/os/Java                                                                                          |
| 2.4 Tool calling + auto-discovery   | ✅      | TaskTool ✅, CheckListTool ✅, McpTool ✅, FileOperationsTool ✅, SkillsTool ✅, AuditTool ✅                                           |
| 2.5 Skill management tool           | ✅      | SkillsTool: addSkill/removeSkill/listSkills/enableSkill/disableSkill + 9 тестов                                                   |
| 2.6 Web fetch tool                  | ✅      | WebFetchTool (Jsoup): fetchPage + fetchSelector, AutoDiscoveredTool bean, 7 тестов                                                |
| 3.1 API contract (OpenAPI)          | ✅      | `specs/openapi.yaml` (3.1.0) + 39 contract-тестов + 6 Playwright E2E                                                              |
| 3.2 Chat API + SSE streaming        | ✅      | ChatRestController, ConversationController, SSE через ResponseBodyEmitter                                                         |
| 3.3 Files API                       | ✅      | FileController CRUD + tree (но filesystem-backed)                                                                                 |
| 3.4 Skills API                      | ✅      | SkillController CRUD (но yaml-backed)                                                                                             |
| 3.5 MCP Servers API                 | ✅      | McpServerController CRUD + status (yaml-backed)                                                                                   |
| 3.6 Actuator + health               | ✅      | Actuator health/info/metrics exposed; /api/health с реальной DB-проверкой; readiness/liveness probes; 7 тестов                    |
| 4.1 Spring Security Basic Auth      | ✅      | SecurityConfig + SecurityProperties, HTTP Basic, RBAC (ADMIN/USER), admin-only skills/mcp endpoints, 13 тестов                    |
| 4.2 Per-user conversation isolation | ✅      | UserResolver + AppUserRepository; conversations фильтруются по user_id; ownership check на delete/messages; 8 тестов              |
| 4.3 Per-user virtual FS isolation   | ✅      | VirtualFile.newUserFile + per-user Repository/Service/Controller с Principal+UserResolver; 10 тестов FileIsolationIntegrationTest |
| 5.1 Scaffold SPA                    | ✅      | Vite + React 19 + TS + TanStack Router + proxy                                                                                    |
| 5.2 Login page                      | ✅      | login-form.tsx, routes/login.tsx                                                                                                  |
| 5.3 Chat UI — базовый               | ✅      | chat-page.tsx, useJavaClawChat (@ai-sdk/react), react-markdown, typing-indicator                                                  |
| 5.4 Streaming display               | ✅      | AssistantMessage, streaming через useChat                                                                                         |
| 5.5 Tool call visualization         | ✅      | tool-call-card.tsx, reasoning-block.tsx                                                                                           |
| 5.6 Conversation switcher           | ✅      | conversation-history-menu.tsx, routes/conversations.tsx                                                                           |
| 5.7 File manager UI                 | ✅      | routes/files.tsx: file tree sidebar + textarea editor, create/delete/save, Cmd+S, language badges; nav link в sidebar             |
| 5.8 Admin UI                        | ✅      | routes/admin/{mcp,skills,prompts}.tsx                                                                                             |
| 5.9 User workspace UI               | ✅      | routes/overview.tsx + admin/prompts.tsx                                                                                           |
| 5.10 Удаление htmx/Pebble           | ✅      | Pebble templates, ChatHtml, Htmx, IndexController полностью удалены; только REST API + React SPA                                  |
| 6.1 Docker image                    | ✅      | Multi-stage Dockerfile (frontend+backend+runtime), docker-compose.yml (app+postgres), healthchecks, 5 тестов                      |
| 6.2 Graceful shutdown               | ✅      | server.shutdown=graceful, lifecycle timeout 30s, 3 теста                                                                          |
| 6.3 Health checks (ready/live)      | ✅      | Actuator readiness/liveness probes enabled, протестированы в ActuatorHealthIntegrationTest                                        |

**Итого P0 (38 пунктов):** ✅ 38 · ⚠️ 0 · ❌ 0 — **P0 COMPLETE**

**Критический путь к P0 COMPLETE:**
1. Spring Security Basic Auth (4.1) — вся Phase 4 пуста
2. Расширить Flyway миграции (1.1): users, conversations, virtual_files, skills, mcp_servers, config
3. Мигрировать SkillStore → SkillRepository JDBC (1.4)
4. Мигрировать McpServerStore → McpServerRepository JDBC (1.5)
5. VirtualFile entity + FileOperationsTool через БД (1.2)
6. Удалить FileSystemChatMemoryRepository (0.2)
7. OpenAPI spec (3.1) + Actuator (3.6) + Graceful shutdown (6.2) + Health probes (6.3)
8. Jib image (6.1)

---

## PHASE 0: Подготовка (до к��да)

### 0.1 Архитектурные решения ✅

- [x] Спека: выбор стека фронтенда (React 19 + Vite + TanStack Router vs альтернативы)
- [x] Спека: AG-UI vs SSE vs WebSocket — протокол streaming (SSE + Vercel AI SDK v4)
- [x] Спека: структура модулей Gradle (что остаётся, что выкидываем, что добавляем) — перешли на Maven multi-module
- [x] Спека: API contract (OpenAPI schema) — endpoints, форматы, ошибки — `specs/openapi.yaml` (3.1.0, 24 ops, 15 schemas)
- [x] Спека: DB schema v1 (все таблицы для P0) — `specs/db-schema-v1.md` (7 таблиц, Mermaid ER, 31 тест зелёные)
- [x] Решение: что делаем с текущим htmx/Pebble кодом (удаляем сразу или параллельно) — помечаем @Deprecated, не удаляем

### 0.2 Чистка текущей кодовой базы ✅

- [x] Удалить onboarding wizard (заменяется config generator CLI позже)
- [x] Удалить FileSystem chat memory (заменяется JDBC) — FileSystemChatMemoryRepository всё ещё в core
- [x] Удалить Playwright plugin (переедет в MCP P4)
- [x] Удалить Brave plugin (web search через MCP P4)
- [x] Зафиксировать интерфейсы, которые остаются: Channel, Agent, Tool, ChatMemoryRepository

---

## PHASE 1: Фундамент данных

> Цель: всё состояние в PostgreSQL, Flyway-миграции, домен-модель

### 1.1 DB Schema + Flyway миграции (11.12) ✅

```
Спека → Тесты → Разработка → Тест → Фиксация
```

- Таблицы: users (минимальная), conversations, messages, virtual_files, skills, mcp_servers, tasks, recurring_tasks, config
- Flyway V1__initial_schema.sql
- **Выход:** пустая БД с правильной схемой поднимается при старте

### 1.2 Virtual filesystem в DB (4.2, 13.10) ✅

```
Спека → Тесты → Разработка → Тест → Фиксация
```

- VirtualFileRepository (Spring Data JDBC — работает с любой БД через коннекторы)
- VirtualFile entity: id, user_id, path, content, content_type, created_at, updated_at
- FileOperationsTool переписать на DB-backed
- **Выход:** агент читает/пишет файлы в БД, не на диск

### 1.3 JDBC Chat Memory (6.1) ✅

```
Спека → Тесты → Разработка → Тест → Фиксация
```

- JdbcAppendableChatMemoryRepository — уже есть, валидировать и дописать тесты
- Conversation ownership: conversation_id содержит user_id
- Message window (6.3) — уже есть
- **Выход:** история чатов в PostgreSQL, работает с текущим агентом

### 1.4 Skills в DB (4.9) ✅

```
Спека → Тесты → Разработка → Тест → Фиксация
```

- SkillRepository (JDBC)
- Skill entity: id, name, content, owner_id (nullable = global), active, created_at
- SkillsTool переписать: загрузка из БД вместо filesystem
- **Выход:** скиллы хранятся в БД, агент читает их оттуда

### 1.5 MCP-серверы в DB (4.8, 5.3) ✅

```
Спека → Тесты → Разработка → Тест → Фиксация
```

- McpServerRepository (JDBC)
- McpServer entity: id, name, type (stdio/http), config_json, active, created_at
- McpTool переписать: dynamic registration сохраняет в БД, загружает при старте
- **Выход:** MCP-серверы персистентны, переживают рестарт

### 1.6 Tasks в DB (4.1, 8.1-8.4) ✅

```
Спека → Тесты → Разработка → Тест → Фиксация
```

- TaskRepository, RecurringTaskRepository — уже в процессе миграции на JDBC
- Валидация, дописать тесты
- **Выход:** задачи и расписания в PostgreSQL

---

## PHASE 2: Агентное ядро

> Цель: агент стримит ответы, вызывает инструменты, читает AGENT.md/SOUL.md из DB

### 2.1 Agent loop + streaming (3.1, 3.3) ✅

```
Спека → Тесты → Разработка → Тест → Фиксация
```

- Валидация текущего DefaultAgent
- Streaming через Spring AI ChatClient → SSE endpoint
- **Выход:** агент отвечает, ответ стримится через HTTP SSE

### 2.2 System prompt из DB (3.4, 3.4a, 3.4b) ✅

```
Спека → Тесты → Разработка → Тест → Фиксация
```

- AGENT.md → глобальный, загружается из virtual_files (owner_id = null, path = 'AGENT.md')
- SOUL.md → глобальный, аналогично
- USER_AGENT.md → per-user, загружается из virtual_files (owner_id = user_id)
- Промпт-сборка: AGENT.md + SOUL.md + USER_AGENT.md → system prompt
- **Выход:** system prompt собирается из DB, разный для разных пользователей

### 2.3 Agent environment (3.9) ✅

```
Спека → Тесты → Разработка → Тест → Фиксация
```

- Переработка AgentEnvironment: дата/время, timezone (пока серверный), имя пользователя, роль
- Инъекция в промпт
- **Выход:** агент знает дату и с кем разговаривает

### 2.4 Tool calling + auto-discovery (3.2, 4.18) ✅

```
Спека → Тесты → Разработка → Тест → Фиксация
```

- Валидация текущего @Tool механизма
- Проверить: TaskTool, CheckListTool, McpTool, FileOperationsTool (DB-backed), SkillsTool (DB-backed)
- **Выход:** все P0 инструменты работают, agent loop вызывает их корректно

### 2.5 Skill management tool (4.9a) ✅

```
Спека → Тесты → Разработка → Тест → Фиксация
```

- SkillsTool: addSkill, removeSkill, listSkills, enableSkill, disableSkill
- Сохраняет в SkillRepository, зарегистрирован как AutoDiscoveredTool
- **Выход:** агент может добавлять/удалять скиллы через чат — 9 тестов зелёные

### 2.6 Web fetch tool (4.4) ✅

```
Спека → Тесты → Разработка → Тест → Фиксация
```

- WebFetchTool на Jsoup: fetchPage (полная страница) + fetchSelector (по CSS-селектору)
- Валидация URL, очистка от nav/footer/script/style, truncation до 20K символов
- Зарегистрирован как AutoDiscoveredTool bean
- **Выход:** агент парсит веб-страницы серверно — 7 тестов зелёные

---

## PHASE 3: REST API

> Цель: полный API для фронтенда, задокументированный в OpenAPI

### 3.1 API contract (спека) ✅

```
Спека (OpenAPI YAML)
```

- POST /api/chat/send — отправить сообщение
- GET /api/chat/stream/{conversationId} — SSE поток ответа
- GET /api/conversations — список диалогов пользователя
- GET /api/conversations/{id}/messages — история сообщений
- DELETE /api/conversations/{id} — удалить диалог
- CRUD /api/files/** — виртуальная FS
- CRUD /api/skills/** — управление скиллами
- CRUD /api/mcp-servers/** — управление MCP-серверами
- GET /api/me — текущий пользователь + роль
- GET /actuator/health — health check
- **Выход:** OpenAPI-спека, по которой пишутся тесты

### 3.2 Chat API + SSE streaming (1.5, 3.3) ✅

```
Тесты → Разработка → Тест → Фиксация спеки
```

- ChatController: send message, stream response (SSE)
- AG-UI протокол или plain SSE (по результатам спеки 0.1)
- Conversation CRUD
- **Выход:** curl может отправить сообщение и получить streaming ответ

### 3.3 Files API (4.2) ✅ (filesystem-backed)

```
Тесты → Разработка → Тест → Фиксация спеки
```

- VirtualFileController: CRUD + tree listing
- **Выход:** REST API для работы с виртуальными файлами

### 3.4 Skills API (4.9) ✅ (yaml-backed)

```
Тесты → Разработка → Тест → Фиксация спеки
```

- SkillController: CRUD
- **Выход:** REST API для управления скиллами

### 3.5 MCP Servers API (5.1-5.3) ✅ (yaml-backed)

```
Тесты → Разработка → Тест → Фиксация спеки
```

- McpServerController: CRUD + status
- **Выход:** REST API для управления MCP-серверами

### 3.6 Actuator + health (12.0, 11.2) ✅

```
Разработка → Тест
```

- Подключить spring-boot-actuator
- /health, /info, /metrics
- Spring AI Observability (12.0a)
- **Выход:** мониторинг из коробки

---

## PHASE 4: Basic Auth

> Цель: минимальная авторизация — admin/user из конфига, Spring Security

### 4.1 Spring Security + Basic Auth (10.0, 15.1.0) ✅

```
Спека → Тесты → Разработка → Тест → Фиксация
```

- [x] Spring Security filter chain (SecurityConfig + SecurityFilterChain)
- [x] Два пользователя из application.yaml: admin/admin, user/user (SecurityProperties + InMemoryUserDetailsManager)
- [x] Роли: ADMIN, USER — admin-only: /api/skills/**, /api/mcp-servers/**
- [x] Защита всех API endpoints (кроме /api/health, /actuator/**, статика)
- [x] /api/me возвращает текущего пользователя + роль (через Authentication principal)
- [x] Stateless sessions (SessionCreationPolicy.STATELESS), CSRF отключён
- [x] 13 новых тестов (AuthIntegrationTest): unauthenticated 401, valid/invalid credentials, RBAC
- **Выход:** без логина ничего не работает, admin и user имеют разный доступ. Все 207 тестов зелёные.

### 4.2 Per-user conversation isolation (базовая) ✅

```
Спека → Тесты → Разработка → Тест → Фиксация
```

- conversationId = "user-{username}-{seq}"
- Пользователь видит только свои диалоги
- Admin видит все (пока не реализуем, но не ломаем)
- **Выход:** admin и user имеют изолированные диалоги

### 4.3 Per-user virtual filesystem isolation ✅

```
Спека → Тесты → Разработка → Тест → Фиксация
```

- [x] VirtualFile.newUserFile(ownerId, path, content, contentType) — фабрика для per-user файлов
- [x] VirtualFileRepository: findByOwnerIdAndPath, findAllByOwnerId, deleteByOwnerIdAndPath, existsByOwnerIdAndPath
- [x] VirtualFileService: treeForUser (user + global), readForUser (user fallback global), writeForUser, createForUser, deleteForUser (только свои)
- [x] FileController: Principal + UserResolver во всех endpoints (tree, read, write, create, delete)
- [x] Глобальные файлы (AGENT.md, SOUL.md): owner_id = null, видны всем в tree и read
- [x] Пользовательские файлы: owner_id = user_id, изолированы между пользователями
- [x] 10 новых тестов (FileIsolationIntegrationTest): CRUD isolation, cross-user read/delete denied, tree merge
- **Выход:** user не видит файлы admin и наоборот; глобальные файлы доступны всем. Все 225 тестов зелёные.

---

## PHASE 5: React SPA

> Цель: полностью новый фронтенд, заменяющий htmx/Pebble

### 5.1 Scaffold проекта (1.1, 1.2) ✅

```
Разработка
```

- Vite + React 19 + TypeScript + TanStack Router
- Подключение к Spring Boot: proxy в vite.config → /api/**
- Spring Boot раздаёт статику из resources/static
- **Выход:** пустая React-страница открывается на localhost:8080

### 5.2 Login page ✅

```
Спека (wireframe) → Разработка → Тест
```

- Форма логина (Basic Auth)
- Редирект на /chat после успешного входа
- **Выход:** можно залогиниться в браузере

### 5.3 Chat UI — базовый (1.3, 1.8, 1.10) ✅

```
Спека (wireframe) → Разработка → Тест
```

- Чат с пузырьками сообщений
- Markdown rendering (react-markdown + rehype-highlight)
- Typing indicator
- Отправка сообщения → вызов POST /api/chat/send
- Получение ответа → SSE /api/chat/stream
- **Выход:** рабочий чат с агентом в браузере

### 5.4 Streaming display (1.5) ✅

```
Разработка → Тест
```

- Токен-по-токену отображение ответа
- AG-UI или SSE клиент
- **Выход:** ответ появляется плавно, не целиком

### 5.5 Tool call visualization (1.10a) ✅

```
Спека (wireframe) → Разработка → Тест
```

- Карточки вызовов инструментов: название, параметры, статус, результат (сворачиваемый)
- Рендеринг в потоке сообщений между текстовыми блоками
- **Выход:** видно что агент делает в реальном времени

### 5.6 Conversation switcher (1.4) ✅

```
Разработка → Тест
```

- Sidebar или dropdown с списком диалогов
- Создание нового диалога
- Загрузка истории при переключении
- **Выход:** можно вести несколько диалогов

### 5.7 File manager UI (1.7c) ✅

```
Спека (wireframe) → Разработка → Тест
```

- [x] Дерево файлов (из /api/files) — рекурсивный TreeNode с expand/collapse, сортировка dirs-first
- [x] Встроенный редактор (textarea с mono font, Cmd+S сохранение, dirty tracking)
- [x] Создание файлов (inline input с path)
- [x] Удаление файлов (кнопка в tree с иконкой)
- [x] Save/Discard в editor toolbar, language badge по расширению
- [x] Навигация: "Files" link в sidebar с IconFiles
- **Выход:** полноценный файловый менеджер в браузере — routes/files.tsx, 152 frontend-теста зелёные

### 5.8 Admin UI — базовый (1.7a) ✅

```
Спека (wireframe) → Разработка → Тест
```

- Страница MCP-серверов: список, добавить, удалить, вкл/выкл
- Страница скиллов: список, добавить, удалить, вкл/выкл
- Страница AGENT.md / SOUL.md: редактор глобальных промптов
- Видна только для роли ADMIN
- **Выход:** admin управляет системой через UI

### 5.9 User workspace UI (1.7b) ✅

```
Спека (wireframe) → Разработка → Тест
```

- USER_AGENT.md / USER_SOUL.md: редактор пользовательских промптов
- Список доступных скиллов (вкл/выкл для себ��)
- Список доступных MCP-серверов
- **Выход:** пользователь настраивает своё пространство

### 5.10 Удаление htmx/Pebble ✅

```
Разработка
```

- Удалить templates/, Pebble dependency, ChatHtml, OnboardingController
- Оставить только REST API + SPA
- **Выход:** чистый проект без легаси фронтенда

---

## PHASE 6: Docker + Deploy

> Цель: production-ready Docker-образ

### 6.1 Docker image (11.1, 11.6) ✅

```
Спека → Разработка → Тест
```

- Jib: собрать образ с React SPA внутри (multi-stage: vite build → spring boot)
- Docker Compose: app + PostgreSQL
- Environment config (11.4): DB_HOST, DB_PORT, etc.
- **Выход:** docker compose up → работающий JavaClaw

### 6.2 Graceful shutdown (11.3) ✅

```
Разработка → Тест
```

- Spring Boot graceful shutdown
- Дождаться завершения текущих SSE-стримов и JobRunr задач
- **Выход:** контейнер останавливается без потери данных

### 6.3 Health checks (11.2) ✅

```
Разработка → Тест
```

- Actuator /health: DB, MCP-серверы (если есть)
- Readiness + Liveness probes
- **Выход:** load balancer знает жив ли инстанс

---

## === P0 COMPLETE ===

> После Phase 6 имеем: работающий enterprise JavaClaw с React SPA, Basic Auth (admin/user), streaming чатом, виртуальной FS в DB, скиллами, MCP, Docker-образ.

---

## PHASE 7: P1 — User management

> Цель: пользователи в БД, нормальные роли

### 7.1 User entity в DB (15.1.1) ✅

- V23 миграция: password_hash populated для seed users ({noop} format)
- AppUser расширен: +passwordHash, +active, +create() factory
- AppUserRepository: +findAllActive, +deactivate, +updatePassword, +updateRole
- UserService CRUD: create/update/deactivate/list
- JdbcUserDetailsService: DB-backed Spring Security auth (заменяет InMemoryUserDetailsManager)
- UserController (admin-only /api/users/**): list, get, create, updateRole, updatePassword, deactivate
- 6 новых интеграционных тестов в UserManagementIntegrationTest

### 7.2 Роли ADMIN/USER в DB (15.2.1) ✅

- Роли хранятся в колонке role таблицы users (CHECK constraint: ADMIN/USER)
- JdbcUserDetailsService загружает роль из DB и маппит в Spring Security GrantedAuthority
- /api/users/{id}/role endpoint для смены роли (admin-only)
- Отдельные roles/user_roles таблицы не нужны — single-role model достаточен для текущих требований

### 7.3 Conversation ownership в DB (15.5.1, 15.5.2) ✅

- Реализовано в Phase 4.2: conversations.user_id + ownership check
- ConversationIsolationIntegrationTest: 8 тестов

---

## PHASE 8: P1 — Расширение агента

### 8.1 SOUL.md per-user (3.4c) ✅

- [x] SystemPromptProvider.loadIdentity(userId) — загружает global AGENT.md + SOUL.md, затем per-user USER_AGENT.md + USER_SOUL.md
- [x] userId протянут через весь pipeline: ChatRestController → SseStreamingService → ChatService → MessageAssembler → SystemPromptProvider
- [x] Backward-compatible: существующие no-userId методы делегируют с null (global-only)
- [x] 4 новых теста в SystemPromptProviderTest: per-user append, missing user files, null userId, DB error graceful skip

### 8.2 Steering / interruption (3.8)

- Механизм инъекции сообщений между tool calls
- Cancel кнопка в UI

### 8.3 Context window management (3.5)

- Auto-compaction при превышении окна
- Суммаризация старых сообщений

### 8.4 FewShotExamples (3.2a)

- Per-tool примеры в конфиге
- Инъекция в промпт при вызове tool calling

### 8.5 Thinking/reasoning mode (2.11)

- Поддержка extended thinking в streaming
- Отображение в UI (1.10b)

### 8.6 Model fallback chain (2.7)

- Fallback при недоступности основной модели
- Конфигурация цепочки в application.yaml

---

## PHASE 9: P1 — MCP и интеграции

### 9.1 MCP health check (5.4)

- Периодическая проверка доступности
- Auto-reconnect

### 9.2 Tool discovery caching (5.5)

- Кэш списка инструментов

### 9.3 MCP as server (5.7)

- JavaClaw выставляет свои инструменты по Streamable HTTP MCP

### 9.4 A2A — server (5.8)

- JavaClaw принимает задачи от внешних агентов

### 9.5 MCP admin management UI (15.4.4)

- Расширение Admin UI: полное управление MCP-серверами

---

## PHASE 10: P1 — Memory и Tools

### 10.1 Dreamin — long-term memory (6.7)

- Memory tools: store/recall/forget/list (4.11)
- Автоматическое извлечение фактов
- UI для просмотра/редактирования памяти

### 10.2 Auto-summarization (6.6)

- Сжатие длинных историй

### 10.3 Message tool (4.12)

- Отправка сообщений из фоновых задач

### 10.4 Tool deny patterns (4.17)

- Чёрный список опасных команд

### 10.5 Cron tool (4.14)

- Управление расписаниями через чат

---

## PHASE 11: P1 — UI расширение

### 11.1 Dark/light theme (1.6)

### 11.2 Settings UI (1.7)

### 11.3 File upload/download (1.9)

### 11.4 Agent thinking display (1.10b)

### 11.5 Execution timeline (1.10c)

### 11.6 Config generator CLI (13.8)

### 11.7 SQLite backend (6.1a)

---

## PHASE 12: P1 — Security basics

### 12.1 Compliance audit trail (16.6)

### 12.2 Agent quota per user (15.6.3)

### 12.3 Sub-agent / delegation (3.6)

---

## === P1 COMPLETE ===

> Имеем: users в DB, нормальные роли, steering, Dreamin, MCP as server, A2A, FewShot, dark theme, audit trail.

---

## PHASE 13-16: P2 — Полноценный RBAC + Scale

### Phase 13: RBAC

- 15.2.2 Гранулярные permissions
- 15.2.3 Role hierarchy (ADMIN > POWER_USER > USER)
- 15.2.4 Custom roles
- 15.3.1-15.3.2 Skill visibility + allowlist per role
- 15.4.1-15.4.3 MCP visibility + personal servers
- 15.5.3 Shared conversations
- 15.5.5 Admin conversation access
- 15.6.1 Agent assignment per role
- 15.6.4 Model access per role

### Phase 14: Память и поиск

- 6.4 Vector memory
- 6.5 Hybrid retrieval (BM25 + vector + RRF)
- 6.8 Conversation archival
- 2.10 Cost tracking

### Phase 15: Инфраструктура

- 11.7 Horizontal scaling (stateless + shared DB)
- 13.5 Spring Cloud Config
- 13.6 Feature flags
- 15.1.3-15.1.5 User profiles, sessions в DB, deactivation
- 16.5 Multi-tenancy

### Phase 16: Прочее P2

- 3.10 Autonomy levels
- 4.13 Git tool
- 4.15 Spawn tool
- 7.21 A2A (каналы)
- 15.4.6 MCP tool risk levels
- 15.5.6-15.5.7 Conversation export, retention policy
- 1.11 Log viewer
- 1.12 Task dashboard в SPA

---

## === P2 COMPLETE ===

---

## PHASE 17+: P3-P5

### P3

- 3.7 Multi-agent routing
- 15.5.4 Team conversations
- 15.1.6 Service accounts
- 15.3.4 Skill usage audit
- 15.4.5 MCP tool execution audit
- 16.12 SLA monitoring
- 4.16 Reaction tool

### P4

- Все внешние каналы (7.2-7.17)
- Все LLM-пров��йдеры (2.2-2.5, 2.8-2.9, 2.12-2.13)
- Voice/multimedia (14.1-14.7)
- OAuth2/OIDC/SSO (10.1-10.2, 15.1.2)
- Полный security stack (10.3-10.15)
- Observability (12.1-12.8 кроме Actuator)
- Browser automation (4.6), Shell (4.5), Image gen (4.10)
- WebChat widget (7.20)

### P5

- 11.13 Startup optimization
- 11.14 GraalVM native

---

## Таймлайн (ориентировочный)

```
Phase 0:  Подготовка           ██░░░░░░░░░░░░░░░░░░░░░░░░░���░░░░
Phase 1:  Фундамент данных     ░░██████░░░░░░░░░░░░░░░░░░░░░░░░
Phase 2:  Агентное ядро        ░░░░░░████░░░░░░░░░░░░░░░░░░░░░░
Phase 3:  REST API             ░░░░░░░░░░████░░░░░░░░░░░░░░░░░░
Phase 4:  Basic Auth           ░░░░░░░░░░░░░░██░░░░░░░░░░░░░░░░
Phase 5:  React SPA            ░░░░░░░░░░░░░░░░████████░░░░░░░░
Phase 6:  Docker + Deploy      ░░░░░░░░░░░░░░░░░░░░░░░░██░░░░��░
                               ─────────── P0 DONE ───────────
Phase 7:  User management      ░░░░░░░░░░░░░░░░░░░░░░░░░░██░░░░
Phase 8:  Расширение агента    ░░░░░░░░░░░░░░░░░░░░░░░░░░░░████
Phase 9:  MCP + A2A            ░░░░░░░░░░░░░░░░░░░░░░░░░░░░████
Phase 10: Memory + Tools       ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░██
Phase 11: UI расширение        ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░██
Phase 12: Security basics      ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░█
                               ─────────── P1 DONE ───────────
Phase 13-16: RBAC + Scale      ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░ →
                               ─────────── P2 DONE ───────────
```

---

## Чеклист для каждого этапа (шаблон)

```
Этап: [название]
Ссылки: [номера из capabilities-catalog.md]

□ 1. SPEC: Написать/обновить спеку в specs/
     - Контракт (API, интерфейсы, модели)
     - Acceptance criteria
     - Edge cases

□ 2. TEST: Написать тесты (красные)
     - Unit tests для domain/service
     - Integration tests для repository/API
     - Тесты отражают acceptance criteria из спеки

□ 3. DEV: Реализация (тесты → зелёные)
     - Минимальная реализация, проходящая тесты
     - Без over-engineering

□ 4. VERIFY: Ручное тестирование
     - Проверить через curl / browser / Postman
     - Smoke test в Docker

□ 5. SPEC FIX: Обновить спеку
     - Зафикси��овать отклонения от первоначального плана
     - Обновить API contract если изменился
     - Обновить capabilities-catalog.md если статус изменился
```

