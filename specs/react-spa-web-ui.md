# Plan: React SPA Web UI — Full Stack (REST API + Frontend)

## Task Description

Создание полноценного Web UI для JavaClaw на базе React 19 + Vite + TypeScript как SPA (Single Page Application), интегрированного со Spring Boot бэкендом через REST API и SSE streaming (Vercel AI SDK протокол). План включает:

1. **REST API endpoints** (Phase 3 из roadmap) — Chat API с SSE streaming, Conversations CRUD, базовые Admin API
2. **React SPA frontend** (Phase 5 из roadmap) — чат с streaming, tool call визуализация, conversation management, admin UI
3. **Maven модуль javaclaw-frontend** с frontend-maven-plugin для единой сборки
4. **Фазированная реализация**: Phase A (Chat MVP) → Phase B (Admin) → Phase C (Overview/Logs/Cron)

**Референсы (UI должен быть МАКСИМАЛЬНО похож на оба):**
- **PicoClaw** (`../picoclaw/web/frontend/`) — архитектура React, компоненты, стейт-менеджмент, layout, chat UI, sidebar
- **OpenClaw** (`../openclaw/ui/src/`) — полный набор фич и экранов (15+ страниц), визуальный дизайн, темы, navigation structure
- **Визуальное соответствие КРИТИЧНО**: каждый экран/компонент должен быть визуально максимально близким к референсам (layout, отступы, цвета, типографика, поведение)
- **Стили**: impeccable скиллы для UI дизайна (`/teach-impeccable`, `/frontend-design`, `/audit`, `/polish`)
- **Процесс для каждого экрана**: (1) сделать скриншот/прочитать код референса → (2) воспроизвести layout → (3) применить Tailwind v4 + shadcn/ui стили → (4) сравнить с референсом → (5) итерировать до совпадения

**Streaming протокол:** Vercel AI SDK (SSE, `useChat` хук) с заделом на будущую миграцию к AG-UI.

## Objective

После выполнения плана:
- JavaClaw имеет полнофункциональный React SPA доступный на `localhost:8080`
- Chat с SSE streaming, tool call cards, markdown rendering
- Conversation management (список, переключение, удаление)
- Admin UI: skills, MCP servers, AGENT.md editor
- Overview dashboard, system logs, cron management
- `mvn package` собирает фронтенд и включает в JAR
- Deprecated Pebble/htmx код помечен для удаления (но не удалён в этом плане)

## Problem Statement

JavaClaw имеет устаревший SSR-фронтенд на Pebble + htmx (помечен `@Deprecated`). REST API не реализован — существуют только `GET /` и `GET /chat` рендерящие шаблоны. Для enterprise AI-платформы нужен современный SPA с streaming, tool call visualization, и полноценным admin UI по образцу OpenClaw.

## Solution Approach

### Архитектурные решения

1. **Streaming: Vercel AI SDK протокол (SSE)**
   - Backend: Spring Boot `SseEmitter` / `ResponseBodyEmitter` с Vercel AI SDK форматом событий
   - Frontend: `@ai-sdk/react` `useChat` хук — управляет messages, streaming, tool calls, abort
   - Формат: `text/event-stream` с заголовком `x-vercel-ai-ui-message-stream: v1`
   - События: `text-start/delta/end`, `tool-input-start/delta/available`, `tool-output-available`, `reasoning-start/delta/end`
   - **Будущая миграция на AG-UI**: SSE транспорт тот же, формат событий аналогичен — миграция = замена event parser
2. **Frontend стек (по PicoClaw)**
   - React 19 + TypeScript
   - Vite 8 (bundler + dev server с proxy на Spring Boot)
   - TanStack Router (file-based routing, code splitting)
   - Jotai (state management — атомы)
   - React Query (серверные данные, кэширование)
   - Tailwind CSS v4 + shadcn/ui (компоненты)
   - react-markdown + remark-gfm (рендеринг markdown)
   - i18next (EN/RU интернационализация)
3. **Maven модуль `javaclaw-frontend`**
   - `frontend-maven-plugin` (com.github.eirslett) — запускает `pnpm install` и `pnpm build`
   - Build output: `javaclaw-frontend/dist/` → копируется в `javaclaw-app/src/main/resources/static/`
   - `maven-resources-plugin` для копирования артефактов
   - Dev режим: `pnpm dev` с Vite proxy на `localhost:8080`
4. **Spring Boot SPA support**
   - `WebMvcConfigurer` → `ResourceHandler` для static assets
   - SPA fallback: все non-API пути → `index.html` (TanStack Router обрабатывает на клиенте)
   - CORS для dev mode (Vite на :5173, Spring Boot на :8080)
5. **SSE Hardening — митигация известных багов Spring MVC SseEmitter**

   ⚠️ Критичные известные проблемы Spring Boot 3.x/4.x (обязательно митигировать):
   - **Memory leak** в `earlySendAttempts` (Spring #33340, #25442) — если клиент отключился до init → события накапливаются до OOM
   - **Deadlock** между SseEmitter и StandardServletAsyncWebRequest при disconnect (Spring #33421)
   - **IOException on HTTP/2 close** (Spring #33832) — логи заспамлены при каждом EventSource.close()
   - **AsyncRequestTimeoutException invasive logs** (Boot #14237) — warnings каждые 30 сек
   - **Client disconnect detection** — Servlet API не уведомляет, только при write failure

   Обязательные митигации в `SseStreamingService` / `ChatRestController`:

   ```java
   // 1. Heartbeat каждые 15s для detection disconnect
   emitter.send(SseEmitter.event().comment("keepalive"));

   // 2. Global @ControllerAdvice для подавления шума:
   @ExceptionHandler({IOException.class, AsyncRequestTimeoutException.class})
   void handleSseClientDisconnect(Exception e) { /* silent */ }

   // 3. Timeout из конфигурации (НЕ hardcode)
   @ConfigurationProperties("javaclaw.chat.sse")
   record SseProperties(Duration timeout, Duration heartbeatInterval, int maxConcurrent) {}
   // application.yaml: javaclaw.chat.sse.timeout: 30m
   new SseEmitter(sseProperties.timeout().toMillis());

   // 4. Cleanup callbacks
   emitter.onCompletion(() -> emitters.remove(emitter));
   emitter.onTimeout(() -> emitter.complete());
   emitter.onError(ex -> emitters.remove(emitter));

   // 5. НЕ вызывать complete() из IOException handler — фреймворк сделает сам

   // 6. Synchronize send() если несколько threads пишут в один emitter
   ```

   Конфигурация `application.yaml`:

   ```yaml
   spring:
     mvc:
       async:
         request-timeout: -1   # не таймаутить SSE на уровне Spring MVC
     threads:
       virtual:
         enabled: true   # Java 21 virtual threads (дешёвые threads для streaming)

   javaclaw:
     chat:
       sse:
         timeout: 30m              # SSE connection timeout
         heartbeat-interval: 15s   # heartbeat для disconnect detection
         max-concurrent: 1000      # лимит одновременных streams (защита от OOM)
   ```

   Все значения SSE конфигурируются через `javaclaw.chat.sse.*` — hardcode запрещён. Все env-overridable: `JAVACLAW_CHAT_SSE_TIMEOUT`, etc.

   Использовать **Tomcat** (default Spring Boot) — на Undertow `onError()` не вызывается при disconnect (Boot #27574).

6. **SSE Auth + CSRF стратегия**

   - ⚠️ Browser native `EventSource` НЕ поддерживает custom headers (Basic Auth невозможен)
   - Решение: `@ai-sdk/react` `useChat` использует `fetch` + `ReadableStream` (не EventSource) → headers работают
   - Basic Auth header отправляется через fetch credentials/headers
   - POST endpoint возвращающий SSE (не GET) — стандартный паттерн для chat API
   - CSRF: Spring Security `.csrf().disable()` для `/api/**` (stateless REST с Basic Auth), или CSRF token header для session-based auth
   - Spring Security config: `httpBasic()` + stateless session policy для REST API
7. **Дизайн: impeccable скиллы**
   - `/teach-impeccable` для базовых guidelines
   - `/frontend-design` для каждого экрана
   - `/audit` + `/polish` перед финализацией

### Фазы реализации

**Phase A — Chat MVP** (минимальный рабочий продукт):
- REST API: Chat send + SSE stream + Conversations CRUD + /api/me
- Frontend scaffold: Vite + React + TanStack Router + Tailwind + shadcn
- Chat UI: messages, streaming, markdown, tool calls
- Login page (Basic Auth)
- Conversation sidebar

**Phase B — Admin UI**:
- REST API: Skills CRUD, MCP Servers CRUD, Files CRUD
- Skills page: список, вкл/выкл
- MCP Servers page: список, добавить, удалить
- AGENT.md / SOUL.md editor
- User workspace (USER_AGENT.md)

**Phase C — Dashboard & Operations**:
- Overview dashboard (status cards, event log)
- System logs viewer
- Cron job management
- Conversations table (admin)
- Settings/Config page

## Relevant Files

### Existing Files (Backend)

- `javaclaw-api/javaclaw-api-chat/pom.xml` — Chat API модуль POM, добавить зависимости
- `javaclaw-api/javaclaw-api-chat/src/main/java/ai/javaclaw/api/chat/ChatController.java` — текущий контроллер (Pebble), переписать на REST
- `javaclaw-api/javaclaw-api-chat/src/main/java/ai/javaclaw/api/chat/ws/WebSocketConfig.java` — существующий WS конфиг
- `javaclaw-api/javaclaw-api-chat/src/main/java/ai/javaclaw/api/chat/ws/ChatWebSocketHandler.java` — существующий WS handler (референс для логики)
- `javaclaw-api/javaclaw-api-chat/src/main/java/ai/javaclaw/api/chat/ChatChannel.java` — канал чата
- `javaclaw-core/src/main/java/ai/javaclaw/JavaClawConfiguration.java` — конфигурация ядра (ChatClient, ChatMemory)
- `javaclaw-core/src/main/java/ai/javaclaw/agent/DefaultAgent.java` — агент для вызова
- `javaclaw-core/src/main/resources/db/migration/` — Flyway миграции
- `javaclaw-app/pom.xml` — App POM, добавить зависимость на javaclaw-frontend
- `javaclaw-app/src/main/java/ai/javaclaw/api/IndexController.java` — редирект, переписать для SPA
- `javaclaw-app/src/main/resources/application.yaml` — конфигурация приложения
- `javaclaw-app/src/main/resources/templates/` — Pebble шаблоны (@Deprecated, не трогаем)
- `pom.xml` — корневой POM, добавить модуль javaclaw-frontend

### New Files

#### Maven Module

- `javaclaw-frontend/pom.xml` — Maven модуль с frontend-maven-plugin
- `javaclaw-frontend/package.json` — npm зависимости
- `javaclaw-frontend/pnpm-lock.yaml` — lockfile
- `javaclaw-frontend/vite.config.ts` — Vite конфигурация с proxy
- `javaclaw-frontend/tsconfig.json` — TypeScript конфигурация
- `javaclaw-frontend/tsconfig.app.json` — App-specific TS config
- `javaclaw-frontend/tailwind.config.ts` — Tailwind (если нужен для v4)
- `javaclaw-frontend/index.html` — HTML entry point
- `javaclaw-frontend/components.json` — shadcn/ui конфигурация

#### Frontend Source

- `javaclaw-frontend/src/main.tsx` — React entry point
- `javaclaw-frontend/src/index.css` — Tailwind CSS + тема
- `javaclaw-frontend/src/lib/utils.ts` — cn() утилита
- `javaclaw-frontend/src/api/http.ts` — HTTP клиент (fetch wrapper)
- `javaclaw-frontend/src/api/chat.ts` — Chat API client
- `javaclaw-frontend/src/api/conversations.ts` — Conversations API
- `javaclaw-frontend/src/api/skills.ts` — Skills API
- `javaclaw-frontend/src/api/mcp.ts` — MCP Servers API
- `javaclaw-frontend/src/api/files.ts` — Files API
- `javaclaw-frontend/src/api/system.ts` — System/health API
- `javaclaw-frontend/src/store/chat.ts` — Chat state atom (Jotai)
- `javaclaw-frontend/src/store/auth.ts` — Auth state atom
- `javaclaw-frontend/src/store/index.ts` — Store re-exports
- `javaclaw-frontend/src/hooks/use-chat.ts` — Chat hook (Vercel AI SDK useChat wrapper)
- `javaclaw-frontend/src/hooks/use-auth.ts` — Auth hook
- `javaclaw-frontend/src/hooks/use-conversation-history.ts` — Session history
- `javaclaw-frontend/src/hooks/use-theme.ts` — Theme management
- `javaclaw-frontend/src/hooks/use-mobile.ts` — Mobile detection
- `javaclaw-frontend/src/i18n/index.ts` — i18next config
- `javaclaw-frontend/src/i18n/locales/en.json` — English translations
- `javaclaw-frontend/src/i18n/locales/ru.json` — Russian translations
- `javaclaw-frontend/src/routes/__root.tsx` — Root layout
- `javaclaw-frontend/src/routes/index.tsx` — / → redirect to /chat
- `javaclaw-frontend/src/routes/chat.tsx` — Chat page
- `javaclaw-frontend/src/routes/login.tsx` — Login page
- `javaclaw-frontend/src/routes/admin.tsx` — Admin layout
- `javaclaw-frontend/src/routes/admin/skills.tsx` — Skills management
- `javaclaw-frontend/src/routes/admin/mcp.tsx` — MCP servers
- `javaclaw-frontend/src/routes/admin/prompts.tsx` — AGENT.md / SOUL.md editor
- `javaclaw-frontend/src/routes/overview.tsx` — Dashboard
- `javaclaw-frontend/src/routes/logs.tsx` — System logs
- `javaclaw-frontend/src/routes/conversations.tsx` — Conversations table
- `javaclaw-frontend/src/routes/config.tsx` — Configuration page
- `javaclaw-frontend/src/routes/cron.tsx` — Cron management
- `javaclaw-frontend/src/components/ui/` — shadcn/ui components (button, dialog, sidebar, etc.)
- `javaclaw-frontend/src/components/chat/chat-page.tsx` — Chat page component
- `javaclaw-frontend/src/components/chat/assistant-message.tsx` — Assistant message bubble
- `javaclaw-frontend/src/components/chat/user-message.tsx` — User message bubble
- `javaclaw-frontend/src/components/chat/chat-composer.tsx` — Input area
- `javaclaw-frontend/src/components/chat/typing-indicator.tsx` — Typing dots
- `javaclaw-frontend/src/components/chat/tool-call-card.tsx` — Tool call visualization
- `javaclaw-frontend/src/components/chat/model-selector.tsx` — Model selector
- `javaclaw-frontend/src/components/chat/conversation-history-menu.tsx` — Session list
- `javaclaw-frontend/src/components/chat/chat-empty-state.tsx` — Empty state
- `javaclaw-frontend/src/components/app-layout.tsx` — Main layout with sidebar
- `javaclaw-frontend/src/components/app-sidebar.tsx` — Navigation sidebar
- `javaclaw-frontend/src/components/app-header.tsx` — Top header
- `javaclaw-frontend/src/components/page-header.tsx` — Page header

#### Backend REST API

- `javaclaw-api/javaclaw-api-chat/src/main/java/ai/javaclaw/api/chat/rest/ChatRestController.java` — POST /api/chat/send, GET /api/chat/stream/{id}
- `javaclaw-api/javaclaw-api-chat/src/main/java/ai/javaclaw/api/chat/rest/ConversationController.java` — Conversations CRUD
- `javaclaw-api/javaclaw-api-chat/src/main/java/ai/javaclaw/api/chat/rest/SseStreamingService.java` — SSE streaming (Vercel AI SDK format)
- `javaclaw-api/javaclaw-api-chat/src/main/java/ai/javaclaw/api/chat/rest/dto/` — DTO classes
- `javaclaw-api/javaclaw-api-admin/src/main/java/ai/javaclaw/api/admin/SkillController.java` — Skills CRUD
- `javaclaw-api/javaclaw-api-admin/src/main/java/ai/javaclaw/api/admin/McpServerController.java` — MCP Servers CRUD
- `javaclaw-api/javaclaw-api-admin/src/main/java/ai/javaclaw/api/admin/FileController.java` — Virtual FS CRUD
- `javaclaw-api/javaclaw-api-admin/src/main/java/ai/javaclaw/api/admin/SystemController.java` — /api/me, /api/health
- `javaclaw-app/src/main/java/ai/javaclaw/api/SpaWebConfig.java` — SPA resource handling + fallback

### Reference Files (read-only)

- `../picoclaw/web/frontend/src/` — PicoClaw React frontend (архитектура, компоненты)
- `../picoclaw/web/frontend/package.json` — зависимости
- `../picoclaw/web/frontend/vite.config.ts` — Vite конфиг
- `../openclaw/ui/src/ui/` — OpenClaw UI (полный набор фич)
- `.claude/refs/react-patterns.md` — React coding standards
- `.claude/refs/java-patterns.md` — Java coding standards
- `.claude/refs/java-testing.md` — Java testing standards

## Implementation Phases

### Phase 1: Foundation

- Maven модуль javaclaw-frontend с frontend-maven-plugin
- Vite + React 19 + TypeScript scaffold
- Tailwind v4 + shadcn/ui базовые компоненты
- Spring Boot SPA resource handling (SpaWebConfig)
- Пустая React страница открывается на localhost:8080
- `/teach-impeccable` для установки design guidelines

### Phase 2: Core Implementation (Phase A — Chat MVP)

- REST API: Chat send + SSE stream (Vercel AI SDK протокол)
- REST API: Conversations CRUD + /api/me
- Chat UI: messages, streaming, markdown, tool call cards
- Login page (Basic Auth)
- Conversation sidebar + switcher
- App layout (sidebar + header + content)
- Dark/light theme

### Phase 3: Integration & Polish (Phase B + C)

- Phase B: Admin UI (skills, MCP, prompts editor)
- Phase C: Overview, Logs, Cron, Conversations (admin), Config
- REST API для admin endpoints
- i18n (EN/RU)
- `/audit` + `/polish` финальная проверка
- Интеграционные тесты

## Team Orchestration

- Вы оперируете как team lead и оркестрируете команду для выполнения плана.
- ВАЖНО: Вы НИКОГДА не работаете с кодом напрямую. Используете `Task` и `Task*` инструменты для делегирования.
- При работе с UI экранами используйте impeccable скиллы: `/teach-impeccable`, `/frontend-design`, `/audit`, `/polish`
- **КРИТИЧНО**: Каждый UI builder должен ВНАЧАЛЕ прочитать соответствующий компонент из PicoClaw (`../picoclaw/web/frontend/src/components/`) И/ИЛИ OpenClaw (`../openclaw/ui/src/ui/`) и воспроизвести его визуальный дизайн. Передавайте конкретные пути к референсным файлам в prompt для каждого builder.

### Team Members

- Builder Backend API
  - Name: builder-api
  - Role: Реализация REST API endpoints на Spring Boot (Chat, Conversations, Admin, SSE streaming)
  - Agent Type: builder
  - Resume: true
- Builder Frontend Scaffold
  - Name: builder-scaffold
  - Role: Инициализация Maven модуля, Vite, React, Tailwind, shadcn, TanStack Router, базовый layout
  - Agent Type: builder
  - Resume: true
- Builder Frontend Chat
  - Name: builder-chat-ui
  - Role: Chat UI компоненты, streaming, tool calls, markdown, conversation management
  - Agent Type: builder
  - Resume: true
- Builder Frontend Admin
  - Name: builder-admin-ui
  - Role: Admin UI pages — skills, MCP, prompts, overview, logs, cron
  - Agent Type: builder
  - Resume: true
- Builder Tests
  - Name: builder-tests
  - Role: Unit + Integration тесты для API и фронтенда
  - Agent Type: builder
  - Resume: true
- Validator
  - Name: validator
  - Role: Финальная валидация — компиляция, тесты, линтинг, acceptance criteria
  - Agent Type: validator
  - Resume: false

## Testing Strategy

Test pyramid ratio: **80% unit / 15% integration-API / 5% UI e2e**

### Unit Tests (80%)

**Java Backend:**
- `SseStreamingServiceTest` — формат Vercel AI SDK событий, streaming flow
- `ChatRestControllerTest` — send message, validate request/response DTOs
- `ConversationControllerTest` — CRUD operations, validation
- `SkillControllerTest` — CRUD + enable/disable
- `McpServerControllerTest` — CRUD + status

**React Frontend:**
- `chat-composer.test.tsx` — input, submit, clear
- `assistant-message.test.tsx` — markdown rendering, tool calls
- `tool-call-card.test.tsx` — states (pending, running, complete, error)
- `conversation-history-menu.test.tsx` — session list, switch, delete
- `use-chat.test.ts` — hook state management, streaming parsing
- `use-auth.test.ts` — login, logout, token management

### Integration / API Tests (15%)

**Java Backend (MockMvc):**
- `ChatApiIntegrationTest` — POST /api/chat/send → SSE stream, verify Vercel AI SDK event format
- `ConversationApiIntegrationTest` — full CRUD lifecycle
- `AdminApiIntegrationTest` — skills, MCP servers CRUD with auth
- `SpaFallbackTest` — non-API routes → index.html
- `AuthIntegrationTest` — Basic Auth protect all /api/** endpoints

### UI E2E Tests (5%)

- `chat-flow.e2e.ts` — login → send message → see streaming response → tool call card
- `conversation-management.e2e.ts` — create, switch, delete conversations
- `admin-skills.e2e.ts` — view skills list, enable/disable

## Step by Step Tasks

### 1. Scaffold Maven Module + Vite + React

- **Task ID**: scaffold-frontend-module
- **Depends On**: none
- **Assigned To**: builder-scaffold
- **Agent Type**: builder
- **Stack**: React Vite component hook useState tsx maven
- **Parallel**: true
- **Tests**: none (scaffold only)
- Создать `javaclaw-frontend/pom.xml` с `frontend-maven-plugin` (pnpm install + pnpm build)
- Создать `package.json` с зависимостями: react 19, react-dom, @tanstack/react-router, @tanstack/router-plugin, @tanstack/react-query, jotai, @ai-sdk/react, tailwindcss v4, @tailwindcss/vite, shadcn, react-markdown, remark-gfm, i18next, react-i18next, dayjs, clsx, tailwind-merge, class-variance-authority, @tabler/icons-react, sonner
- Создать `vite.config.ts` с proxy на localhost:8080 для /api/**, TanStack Router plugin, Tailwind plugin
- Создать `tsconfig.json` + `tsconfig.app.json` (target ES2022, strict, paths: @/* → ./src/*)
- Создать `index.html`, `src/main.tsx`, `src/index.css` (Tailwind + тема)
- Создать `src/lib/utils.ts` с `cn()` утилитой
- Установить базовые shadcn/ui компоненты: button, input, dialog, sidebar, card, scroll-area, separator, badge, tooltip, sheet, skeleton, dropdown-menu, textarea, select, switch, label, collapsible
- Добавить `javaclaw-frontend` как модуль в корневой `pom.xml`
- Проверить: `cd javaclaw-frontend && pnpm install && pnpm build` проходит

### 2. Spring Boot SPA Config + Base Layout

- **Task ID**: spa-config-and-layout
- **Depends On**: scaffold-frontend-module
- **Assigned To**: builder-scaffold
- **Agent Type**: builder
- **Stack**: Java Spring Boot controller react component hook tsx vite
- **Parallel**: false
- **Tests**: Unit: SpaWebConfigTest — non-API routes serve index.html
- Создать `SpaWebConfig.java` в javaclaw-app: `WebMvcConfigurer` с `ResourceHandler` для static/, SPA fallback на index.html для не-API путей
- Добавить CORS config для dev mode (Vite :5173 → Spring Boot :8080)
- Настроить `maven-resources-plugin` в `javaclaw-app/pom.xml`: копировать `javaclaw-frontend/dist/` → `src/main/resources/static/`
- Создать frontend: `src/routes/__root.tsx` (root layout), `src/routes/index.tsx` (redirect → /chat)
- Создать `src/components/app-layout.tsx` — основной layout (sidebar + header + content area)
- Создать `src/components/app-sidebar.tsx` — навигация (Chat, Overview, Admin, Settings — по OpenClaw)
- Создать `src/components/app-header.tsx` — topbar (title, theme toggle)
- Создать `src/components/page-header.tsx` — заголовок страницы
- Создать `src/hooks/use-theme.ts` — dark/light theme management
- Создать `src/hooks/use-mobile.ts` — mobile detection
- Проверить: React SPA открывается на localhost:8080 после `mvn package`
- **DESIGN**: использовать `/teach-impeccable` для установки design guidelines, затем `/frontend-design` для layout

### 3. Chat REST API + SSE Streaming

- **Task ID**: chat-rest-api
- **Depends On**: none
- **Assigned To**: builder-api
- **Agent Type**: builder
- **Stack**: Java Spring Boot controller exception error handling SSE controlleradvice
- **Parallel**: true (параллельно с scaffold-frontend-module)
- **Tests**: Unit: ChatRestControllerTest, SseStreamingServiceTest (включая disconnect/cleanup сценарии). Integration: ChatApiIntegrationTest (verify heartbeat, timeout, disconnect handling)
- Создать `ChatRestController.java`:
  - `POST /api/chat/send` — принимает `{content: string, conversationId?: string}`, запускает агента, возвращает SSE stream
  - `GET /api/chat/stream/{conversationId}` — SSE endpoint для reconnect
- Создать `SseStreamingService.java` — форматирует ответы Spring AI ChatClient в Vercel AI SDK SSE формат:
  - `text-start` / `text-delta` / `text-end` — для текстового streaming
  - `tool-input-start` / `tool-input-delta` / `tool-input-available` — для tool calls
  - `tool-output-available` — для результатов tool calls
  - `message-start` / `finish-step` / `finish` — lifecycle
  - Header: `x-vercel-ai-ui-message-stream: v1`
- Создать DTOs: `ChatSendRequest`, `ChatSendResponse`, `VercelSseEvent`
- Интегрировать с `DefaultAgent` / `ChatClient` из javaclaw-core
- Убедиться что streaming работает: `curl -N -H "Accept: text/event-stream" POST localhost:8080/api/chat/send -d '{"content":"hello"}'`

### 4. Conversations + User API

- **Task ID**: conversations-api
- **Depends On**: none
- **Assigned To**: builder-api
- **Agent Type**: builder
- **Stack**: Java Spring Boot controller entity jpa error handling
- **Parallel**: true (параллельно с chat-rest-api)
- **Tests**: Unit: ConversationControllerTest. Integration: ConversationApiIntegrationTest
- Создать `ConversationController.java`:
  - `GET /api/conversations` — список диалогов текущего пользователя
  - `GET /api/conversations/{id}/messages` — история сообщений (пагинация)
  - `DELETE /api/conversations/{id}` — удалить диалог
  - `POST /api/conversations` — создать новый диалог
- Создать `SystemController.java`:
  - `GET /api/me` — текущий пользователь + роль (из SecurityContext или конфига)
  - `GET /api/health` — health status
- Создать DTOs: `ConversationDto`, `MessageDto`, `UserInfoDto`
- Использовать существующий `JdbcAppendableChatMemoryRepository` для данных

### 5. Chat UI — Messages + Streaming

- **Task ID**: chat-ui-core
- **Depends On**: spa-config-and-layout, chat-rest-api
- **Assigned To**: builder-chat-ui
- **Agent Type**: builder
- **Stack**: React component hook useState useEffect tsx vite react-router
- **Parallel**: false
- **Tests**: Unit: assistant-message.test.tsx, user-message.test.tsx, chat-composer.test.tsx, typing-indicator.test.tsx
- Создать `src/routes/chat.tsx` — Chat page route
- Создать `src/components/chat/chat-page.tsx` — основной компонент чата
- Создать `src/hooks/use-chat.ts` — обёртка над `useChat` из `@ai-sdk/react`:
  - Настройка API endpoint: `POST /api/chat/send`
  - Streaming через Vercel AI SDK protocol
  - Message state management
  - Tool call tracking
  - Abort/cancel support
- Создать `src/components/chat/chat-composer.tsx`:
  - Auto-expanding textarea (react-textarea-autosize)
  - Send button + keyboard shortcut (Enter/Cmd+Enter)
  - Abort button во время streaming
- Создать `src/components/chat/assistant-message.tsx`:
  - Markdown rendering (react-markdown + remark-gfm)
  - Syntax highlighting для code blocks
  - Copy to clipboard
  - Streaming text display
- Создать `src/components/chat/user-message.tsx` — пользовательское сообщение
- Создать `src/components/chat/typing-indicator.tsx` — анимация набора
- Создать `src/components/chat/chat-empty-state.tsx` — пустое состояние чата
- Создать `src/store/chat.ts` — Jotai атом для chat state
- Создать `src/api/chat.ts` — API клиент
- **DESIGN**: `/frontend-design` для Chat UI, референс — OpenClaw chat page

### 6. Tool Call Visualization

- **Task ID**: tool-call-ui
- **Depends On**: chat-ui-core
- **Assigned To**: builder-chat-ui
- **Agent Type**: builder
- **Stack**: React component hook tsx
- **Parallel**: false
- **Tests**: Unit: tool-call-card.test.tsx — states (pending, running, complete, error)
- Создать `src/components/chat/tool-call-card.tsx`:
  - Название инструмента + иконка
  - Параметры (JSON, сворачиваемые)
  - Статус: pending → running → complete/error
  - Результат (сворачиваемый, с markdown rendering)
  - Анимация при running
- Интегрировать с `useChat` — Vercel AI SDK автоматически парсит tool-input/tool-output события в `message.parts`
- Рендеринг в потоке сообщений между текстовыми блоками
- **DESIGN**: `/frontend-design` для tool call cards, референс — OpenClaw tool stream sidebar

### 7. Login Page + Auth

- **Task ID**: login-auth
- **Depends On**: spa-config-and-layout
- **Assigned To**: builder-chat-ui
- **Agent Type**: builder
- **Stack**: React component hook tsx Java Spring Boot controller
- **Parallel**: true (параллельно с chat-ui-core)
- **Tests**: Unit: use-auth.test.ts. Integration: AuthIntegrationTest
- Создать `src/routes/login.tsx` — Login page
- Создать `src/hooks/use-auth.ts`:
  - Basic Auth (username + password → base64 header)
  - Token storage в localStorage
  - Auto-redirect на /login если 401
  - /api/me для проверки авторизации
- Создать `src/store/auth.ts` — Jotai атом: user, isAuthenticated, role
- Создать `src/api/http.ts` — fetch wrapper с auto-auth header
- Обновить `__root.tsx` — auth guard (redirect на /login если не авторизован)
- **DESIGN**: `/frontend-design` для Login page

### 8. Conversation Management UI

- **Task ID**: conversation-ui
- **Depends On**: chat-ui-core, conversations-api
- **Assigned To**: builder-chat-ui
- **Agent Type**: builder
- **Stack**: React component hook tsx react-router
- **Parallel**: false
- **Tests**: Unit: conversation-history-menu.test.tsx, use-conversation-history.test.ts
- Создать `src/components/chat/conversation-history-menu.tsx`:
  - Список диалогов в sidebar
  - Поиск/фильтрация
  - Создать новый диалог
  - Удалить диалог (с подтверждением)
  - Переключение между диалогами
  - Активный диалог подсвечен
- Создать `src/hooks/use-conversation-history.ts`:
  - Загрузка списка через React Query
  - Пагинация
  - Optimistic updates
- Создать `src/api/conversations.ts` — API клиент для conversations
- Интегрировать с app-sidebar

### 9. Admin REST API (Skills, MCP, Files)

- **Task ID**: admin-rest-api
- **Depends On**: none
- **Assigned To**: builder-api
- **Agent Type**: builder
- **Stack**: Java Spring Boot controller entity error handling
- **Parallel**: true
- **Tests**: Unit: SkillControllerTest, McpServerControllerTest, FileControllerTest. Integration: AdminApiIntegrationTest
- Создать `SkillController.java`:
  - `GET /api/skills` — список скиллов
  - `POST /api/skills` — создать скилл
  - `PUT /api/skills/{id}` — обновить (вкл/выкл)
  - `DELETE /api/skills/{id}` — удалить
- Создать `McpServerController.java`:
  - `GET /api/mcp-servers` — список MCP серверов
  - `POST /api/mcp-servers` — добавить
  - `PUT /api/mcp-servers/{id}` — обновить (вкл/выкл)
  - `DELETE /api/mcp-servers/{id}` — удалить
  - `GET /api/mcp-servers/{id}/status` — статус подключения
- Создать `FileController.java`:
  - `GET /api/files` — дерево файлов
  - `GET /api/files/{path}` — содержимое файла
  - `PUT /api/files/{path}` — сохранить файл
  - `DELETE /api/files/{path}` — удалить файл
  - `POST /api/files` — создать файл
- DTOs для каждого контроллера

### 10. Admin UI — Skills + MCP Pages

- **Task ID**: admin-ui-skills-mcp
- **Depends On**: spa-config-and-layout, admin-rest-api
- **Assigned To**: builder-admin-ui
- **Agent Type**: builder
- **Stack**: React component hook tsx react-router
- **Parallel**: false
- **Tests**: none (UI visual, covered by e2e)
- Создать `src/routes/admin.tsx` — Admin layout (ADMIN role guard)
- Создать `src/routes/admin/skills.tsx` — Skills page:
  - Таблица/grid скиллов
  - Enable/disable toggle
  - Поиск/фильтр
  - Add/Delete действия
  - Статус-badge (active/inactive)
- Создать `src/routes/admin/mcp.tsx` — MCP Servers page:
  - Список серверов с типом (stdio/http)
  - Status indicator (connected/disconnected)
  - Add server form (dialog)
  - Delete с подтверждением
  - Enable/disable toggle
- Создать `src/api/skills.ts`, `src/api/mcp.ts` — API клиенты
- **DESIGN**: `/frontend-design` для каждой страницы, референс — OpenClaw agents/skills pages

### 11. Admin UI — Prompts Editor

- **Task ID**: admin-ui-prompts
- **Depends On**: admin-ui-skills-mcp
- **Assigned To**: builder-admin-ui
- **Agent Type**: builder
- **Stack**: React component hook tsx
- **Parallel**: false
- **Tests**: none (UI visual)
- Создать `src/routes/admin/prompts.tsx` — AGENT.md / SOUL.md editor:
  - Табы: AGENT.md, SOUL.md, INFO.md
  - Textarea с monospace font для markdown
  - Save/Discard buttons
  - Draft persistence (localStorage)
  - Preview rendered markdown
- User workspace: USER_AGENT.md editor (доступен всем ролям)
- Интеграция с Files API (`GET/PUT /api/files/AGENT.md`)
- **DESIGN**: `/frontend-design`, референс — OpenClaw agents files panel

### 12. Overview Dashboard

- **Task ID**: overview-dashboard
- **Depends On**: spa-config-and-layout
- **Assigned To**: builder-admin-ui
- **Agent Type**: builder
- **Stack**: React component hook tsx
- **Parallel**: true (после layout готов)
- **Tests**: none (UI visual)
- Создать `src/routes/overview.tsx` — Dashboard page:
  - Status cards: connection status, active conversations count, skills count, health
  - System info panel
  - Recent activity / event log (если API доступен)
  - Quick actions
- Создать `src/api/system.ts` — System/health API клиент
- **DESIGN**: `/frontend-design`, референс — OpenClaw overview page

### 13. System Logs + Conversations + Config Pages

- **Task ID**: operations-pages
- **Depends On**: overview-dashboard
- **Assigned To**: builder-admin-ui
- **Agent Type**: builder
- **Stack**: React component hook tsx
- **Parallel**: false
- **Tests**: none (UI visual)
- Создать `src/routes/logs.tsx` — Logs viewer:
  - Actuator logfile endpoint
  - Log level filtering
  - Auto-scroll / follow mode
  - Search in logs
- Создать `src/routes/conversations.tsx` — Conversations table (admin view для всех диалогов в системе):
  - Sortable columns (created, last activity, messages count, owner)
  - Delete conversation
  - Navigate to chat
- Создать `src/routes/config.tsx` — Config page:
  - application.yaml viewer
  - Key configuration parameters
- Создать `src/routes/cron.tsx` — Cron management (если API доступен):
  - Job list + status
  - Enable/disable
  - Manual trigger
- **DESIGN**: `/frontend-design` для каждой страницы

### 14. i18n + Theme Polish

- **Task ID**: i18n-theme-polish
- **Depends On**: operations-pages
- **Assigned To**: builder-chat-ui
- **Agent Type**: builder
- **Stack**: React component hook tsx
- **Parallel**: false
- **Tests**: none
- Настроить i18next: EN (primary) + RU
- Создать `src/i18n/locales/en.json` + `ru.json` со всеми строками UI
- Language detector (browser language)
- Финализировать dark/light theme
- **DESIGN**: `/audit` + `/polish` для финальной проверки качества всего UI

### 15. E2E Tests + Test Coverage Audit

- **Task ID**: e2e-tests-audit
- **Depends On**: chat-rest-api, conversations-api, admin-rest-api, chat-ui-core, tool-call-ui, login-auth, conversation-ui, admin-ui-skills-mcp, admin-ui-prompts
- **Assigned To**: builder-tests
- **Agent Type**: builder
- **Stack**: Java MockMvc Mockito assertj integration test React jest testing-library tsx selenide e2e
- **Parallel**: false
- **Note**: Unit + integration tests пишутся каждым builder'ом в своих tasks (tasks 2-13). Эта задача — ТОЛЬКО E2E + audit.
- **Audit step**: проверить что unit + integration тесты из tasks 2-13 существуют и проходят (список в Testing Strategy). Добить недостающие.
- **E2E Tests (Playwright):**
  - `chat-flow.e2e.ts` — login → send message → see streaming response → tool call card
  - `conversation-management.e2e.ts` — create/switch/delete conversations
  - `admin-skills.e2e.ts` — skills enable/disable

### 16. Final Validation

- **Task ID**: validate-all
- **Depends On**: scaffold-frontend-module, spa-config-and-layout, chat-rest-api, conversations-api, chat-ui-core, tool-call-ui, login-auth, conversation-ui, admin-rest-api, admin-ui-skills-mcp, admin-ui-prompts, overview-dashboard, operations-pages, i18n-theme-polish, e2e-tests-audit
- **Assigned To**: validator
- **Agent Type**: validator
- **Stack**: Java Spring Boot controller entity test React component hook tsx maven MockMvc assertj
- **Parallel**: false
- `mvn compile` — проект компилируется
- `mvn spotless:check` — Java code style
- `cd javaclaw-frontend && pnpm build` — frontend builds
- `cd javaclaw-frontend && npx tsc --noEmit` — TypeScript type check
- `cd javaclaw-frontend && npx eslint .` — ESLint
- `mvn test` — Java unit + integration tests pass
- `cd javaclaw-frontend && pnpm test` — frontend tests pass
- Verify: `mvn package` creates JAR with frontend assets in resources/static/
- Verify: localhost:8080 serves React SPA
- Verify: /api/chat/send returns SSE stream
- Verify: /api/conversations returns conversation list
- Verify acceptance criteria

## Acceptance Criteria

1. `mvn package` собирает единый JAR с React SPA внутри
2. `localhost:8080` открывает React SPA (не Pebble шаблон)
3. Login page работает с Basic Auth (admin/admin, user/user)
4. Chat: отправка сообщения → SSE streaming ответ в реальном время
5. Chat: tool calls отображаются как карточки с параметрами и результатами
6. Chat: markdown (заголовки, код, списки, ссылки) рендерится корректно
7. Conversation management: создать, переключить, удалить диалог
8. Admin: Skills page — список, вкл/выкл, удалить
9. Admin: MCP Servers page — список, добавить, удалить
10. Admin: AGENT.md editor — редактировать и сохранить
11. Overview: dashboard со статусом системы
12. Logs: просмотр системных логов
13. Dark/light theme переключение
14. Русский и английский язык интерфейса
15. Все Java тесты проходят (`mvn test`)
16. Все frontend тесты проходят (`pnpm test`)
17. TypeScript компилируется без ошибок
18. Deprecated Pebble/htmx код НЕ удалён (помечен, оставлен)

## Validation Commands

```bash
# Backend compilation
mvn compile -q

# Backend code style
mvn spotless:check

# Frontend build
cd javaclaw-frontend && pnpm install && pnpm build

# Frontend type check
cd javaclaw-frontend && npx tsc --noEmit

# Frontend lint
cd javaclaw-frontend && npx eslint .

# Backend tests
mvn test

# Frontend tests
cd javaclaw-frontend && pnpm test

# Full package (frontend + backend JAR)
mvn package

# Smoke test — SPA serves
curl -s http://localhost:8080/ | grep -q "<!DOCTYPE html"

# Smoke test — API works
curl -s http://localhost:8080/api/me -u admin:admin | grep -q "admin"

# Smoke test — SSE streaming
curl -N -H "Accept: text/event-stream" -u admin:admin \
  -X POST http://localhost:8080/api/chat/send \
  -H "Content-Type: application/json" \
  -d '{"content":"Hello"}'
```

## Notes

- **Vercel AI SDK** (`@ai-sdk/react`) используется только на фронтенде для `useChat` хука. На бэкенде реализуем SSE формат вручную (не нужен Node.js).
- **AG-UI миграция**: в будущем можно заменить формат SSE событий на AG-UI, добавить `agentscope-agui-spring-boot-starter`, на фронте заменить `useChat` на AG-UI client. SSE транспорт остаётся тот же.
- **Pebble/htmx**: НЕ удаляем в этом плане. `@Deprecated` аннотации остаются. Удаление — отдельная задача после полной проверки SPA.
- **Basic Auth**: используем hardcoded users из application.yaml (admin/admin, user/user). Полноценная auth с users в DB — Phase 7 из roadmap.
- **Библиотеки для установки**:
  - Frontend: `pnpm add react react-dom @tanstack/react-router @tanstack/react-query jotai @ai-sdk/react tailwindcss @tailwindcss/vite @tailwindcss/typography class-variance-authority clsx tailwind-merge react-markdown remark-gfm rehype-raw rehype-sanitize i18next react-i18next i18next-browser-languagedetector dayjs @tabler/icons-react sonner react-textarea-autosize`
  - Frontend dev: `pnpm add -D typescript vite @vitejs/plugin-react @tanstack/router-plugin @types/react @types/react-dom eslint prettier vitest @testing-library/react @testing-library/jest-dom`
  - Java: Spring Web, Spring Security (если ещё не в pom.xml)
- **impeccable скиллы**: ОБЯЗАТЕЛЬНО использовать `/teach-impeccable` при scaffold, `/frontend-design` для каждого экрана, `/audit` + `/polish` при финализации

