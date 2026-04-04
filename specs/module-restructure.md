# Module Restructure: Атомарная модульная архитектура

> Статус: DRAFT
> Дата: 2026-04-04

---

## Мотивация

Текущая структура смешивает ответственности:
- `base/` — монолитное ядро с Spring AI хаками
- `app/` — main + web chat + onboarding wizard + WebSocket (совмещает assembler и бизнес-логику)
- `plugins/` — каналы и тулы в одной директории (разная природа)

---

## Целевая структура

```
javaclaw/
├── core/                      — ядро (agent, channels, tasks, tools, domain)
├── api/
│   ├── admin/                 — CRUD: MCP, skills, prompts, users
│   └── chat/                  — отправка через Channel + чтение данных
├── channels/
│   ├── discord/               — Channel impl (JDA)
│   └── telegram/              — Channel impl (TelegramBots)
├── providers/
│   ├── anthropic/
│   ├── openai/
│   ├── ollama/
│   └── google/
└── app/                       — main + application.yaml + assembler pom.xml
```

### Будущие модули (НЕ создаём сейчас)

```
├── api/
│   ├── a2a/                   — Agent-to-Agent protocol
│   └── mcp-server/            — JavaClaw как MCP сервер
├── tools/
│   ├── web-fetch/             — Jsoup web fetch
│   └── shell-exec/            — sandbox shell execution
```

---

## Маппинг: текущая → целевая

|     Текущий модуль      |    Целевой модуль    |                             Действие                             |
|-------------------------|----------------------|------------------------------------------------------------------|
| `base/`                 | `core/`              | Переименовать. Убрать `org.springframework.ai.chat.*` хаки       |
| `app/` (main, yaml)     | `app/`               | Оставить только main + yaml + зависимости                        |
| `app/` (chat/*, ws/*)   | `api/chat/`          | Вынести ChatController, ChatChannel, WebSocket                   |
| `app/` (onboarding/*)   | —                    | Удалить (legacy, roadmap Phase 0.2)                              |
| `app/` (ChatHtml, Htmx) | —                    | Удалить (htmx legacy)                                            |
| `plugins/discord/`      | `channels/discord/`  | Переместить                                                      |
| `plugins/telegram/`     | `channels/telegram/` | Переместить                                                      |
| `plugins/brave/`        | —                    | Удалить (web search через MCP)                                   |
| `plugins/playwright/`   | —                    | Удалить (browser через MCP)                                      |
| `providers/*`           | `providers/*`        | Без изменений (переместить на уровень выше если были в plugins/) |

---

## Детали по модулям

### core/ (ex base/)

**artifactId:** `javaclaw-core`

**Содержимое (без изменений):**
- `ai.javaclaw.agent` — Agent interface, DefaultAgent
- `ai.javaclaw.channels` — Channel interface, ChannelRegistry, events
- `ai.javaclaw.tasks` — Task, RecurringTask, TaskManager, repositories
- `ai.javaclaw.tools` — Tool interfaces (McpTool, TaskTool, CheckListTool)
- `ai.javaclaw.configuration` — ConfigurationManager
- `ai.javaclaw.files` — YamlParser, YamlDocument
- `ai.javaclaw.mcp` — McpConnectionsProperties, McpHeaderCustomizer
- `ai.javaclaw.providers` — AgentProvider

**Удалить:**
- `org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor` — Spring AI хак
- `org.springframework.ai.chat.memory.AppendableChatMemoryRepository` — Spring AI хак
- `org.springframework.ai.chat.memory.MessageWindowChatMemory` — Spring AI хак
- `ai.javaclaw.onboarding` — legacy onboarding interfaces

**TODO:** Spring AI хаки требуют отдельного исследования — либо upstream PR, либо обёртки в собственном namespace `ai.javaclaw.ai`.

**Зависимости:** spring-boot-starter, spring-modulith, spring-ai-client-chat, spring-ai-mcp-client, spring-data-jdbc, jobrunr, commons-lang3

### api/chat/ (NEW)

**artifactId:** `javaclaw-api-chat`

**Содержимое (из app/):**
- `ai.javaclaw.api.chat.ChatController` (ex `ai.javaclaw.chat.api.ChatController`)
- `ai.javaclaw.api.chat.ChatChannel` (ex `ai.javaclaw.chat.ChatChannel`)
- `ai.javaclaw.api.chat.ws.ChatWebSocketHandler` (ex `ai.javaclaw.chat.ws.ChatWebSocketHandler`)
- `ai.javaclaw.api.chat.ws.WebSocketConfig` (ex `ai.javaclaw.chat.ws.WebSocketConfig`)

**Зависимости:** javaclaw-core, spring-boot-starter-webmvc, spring-boot-starter-websocket

### api/admin/ (NEW, пустой scaffold)

**artifactId:** `javaclaw-api-admin`

**Содержимое:** пока пустой, заполнится в Phase 3 roadmap (REST API для MCP, skills, files, users).

**Зависимости:** javaclaw-core, spring-boot-starter-webmvc

### channels/discord/ (ex plugins/discord/)

**artifactId:** `javaclaw-channel-discord`

**Содержимое:** без изменений — DiscordChannel, DiscordChannelAutoConfiguration, DiscordOnboardingProvider.

**Зависимости:** javaclaw-core, JDA

### channels/telegram/ (ex plugins/telegram/)

**artifactId:** `javaclaw-channel-telegram`

**Содержимое:** без изменений — TelegramChannel, TelegramChannelAutoConfiguration, TelegramOnboardingProvider.

**Зависимости:** javaclaw-core, telegrambots

### providers/* (без изменений)

artifactId'ы: `javaclaw-provider-anthropic`, `javaclaw-provider-openai`, `javaclaw-provider-ollama`, `javaclaw-provider-google`

### app/ (slim assembler)

**artifactId:** `javaclaw-app`

**Содержимое:**
- `JavaClawApplication` (main)
- `application.yaml`
- Flyway миграции (`db/migration/`)
- `IndexController` (корневой redirect, если нужен)

**Зависимости:** все остальные модули

---

## Порядок выполнения

### Step 1: Подготовка

- [ ] Создать директории `core/`, `api/chat/`, `api/admin/`, `channels/`
- [ ] Создать pom.xml для каждого нового модуля
- [ ] Обновить корневой pom.xml — modules секция

### Step 2: core/ (переименование base/)

- [ ] Переместить `base/src/` → `core/src/`
- [ ] Обновить artifactId в pom.xml: `javaclaw-base` → `javaclaw-core`
- [ ] Обновить все ссылки на `javaclaw-base` во всех pom.xml

### Step 3: channels/ (перемещение из plugins/)

- [ ] Переместить `plugins/discord/` → `channels/discord/`
- [ ] Переместить `plugins/telegram/` → `channels/telegram/`
- [ ] Обновить artifactId'ы если нужно

### Step 4: api/chat/ (вынос из app/)

- [ ] Создать `api/chat/src/main/java/ai/javaclaw/api/chat/`
- [ ] Переместить ChatController, ChatChannel, WebSocket* из app
- [ ] Обновить пакеты и imports

### Step 5: Чистка app/

- [ ] Удалить onboarding/ (6 step-классов + controller)
- [ ] Удалить ChatHtml, Htmx
- [ ] Удалить Pebble templates и зависимость
- [ ] Оставить: main, yaml, IndexController, Flyway миграции

### Step 6: Удаление legacy plugins

- [ ] Удалить `plugins/brave/`
- [ ] Удалить `plugins/playwright/`
- [ ] Удалить `plugins/build.gradle` (если остался)
- [ ] Удалить директорию `plugins/` целиком

### Step 7: Верификация

- [ ] `mvn clean compile` — всё собирается
- [ ] `mvn test` — все тесты зелёные
- [ ] Приложение стартует и отвечает на запросы

---

## Acceptance Criteria

1. Структура директорий соответствует целевой схеме
2. Все модули собираются через Maven
3. Нет circular dependencies между модулями
4. `app/` содержит только main + yaml + Flyway + assembler зависимости
5. Все существующие тесты проходят
6. Spring Boot autoconfiguration работает для channels и providers
7. `plugins/` директория удалена

