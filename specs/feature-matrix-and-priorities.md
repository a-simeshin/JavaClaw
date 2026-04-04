# Enterprise JavaClaw: Матрица возможностей и приоритизация

> **Локальные пути и карта директорий:** [reference-repos.md](reference-repos.md)

## Сводная матрица возможностей

|    Возможность     |                                      OpenClaw (TS)                                       |                                         NullClaw (Zig)                                          |                              PicoClaw (Go)                               |                         JavaClaw (Java)                         |
|--------------------|------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------|-----------------------------------------------------------------|
| **UI**             | Lit web components, native apps (iOS/macOS/Android)                                      | CLI only                                                                                        | React 19 + Vite SPA, TUI, CLI, Android                                   | htmx + Pebble SSR                                               |
| **Каналы**         | 23+ (WhatsApp, Telegram, Discord, Slack, Signal, iMessage, Teams, Matrix, IRC, Nostr...) | 19+ (те же + Email, iMessage, A2A)                                                              | 18+ (те же + WeChat, QQ, LINE, VK, DingTalk)                             | 3 (Web, Discord, Telegram)                                      |
| **LLM-провайдеры** | 95+ расширений                                                                           | 50+ (vtable-driven)                                                                             | 30+                                                                      | 4 (OpenAI, Anthropic, Ollama, Google)                           |
| **MCP**            | mcporter (stdio)                                                                         | Stdio + HTTP JSON-RPC                                                                           | Native Go SDK                                                            | Stdio + Streamable HTTP (Spring AI)                             |
| **Память/история** | LanceDB vector + append-only                                                             | 10 движков (SQLite, PG, Redis, ClickHouse, LanceDB, Lucid) + hybrid retrieval (BM25+vector+RRF) | JSONL sessions + MEMORY.md                                               | JDBC (PostgreSQL) + FileSystem YAML                             |
| **Задачи/cron**    | Croner + task model                                                                      | Cron + JSON persistence                                                                         | Cron + heartbeat + spawn                                                 | JobRunr (dashboard, workers, cron)                              |
| **Инструменты**    | Playwright, web search, image gen, canvas, file ops, 60+ skills                          | 35+ (file, web, shell, cron, memory, spawn, browser, git)                                       | Web, exec, cron, skills, MCP, subagent, filesystem                       | TaskTool, CheckList, MCP, FileSystem, Skills, Brave, Playwright |
| **Безопасность**   | Token/password auth, device pairing, TLS, rate limiting                                  | ChaCha20 encryption, sandbox (Landlock/Firejail/Bubblewrap/Docker), pairing, audit              | Launcher token, session cookies, rate limiting, sensitive data filtering | allowedUser per channel, onboarding gate                        |
| **Streaming**      | SSE tool/block streaming                                                                 | SSE streaming                                                                                   | Real-time streaming                                                      | WebSocket streaming                                             |
| **Мульти-агент**   | Multi-workspace routing                                                                  | Subagent delegation, A2A                                                                        | SubTurn architecture, agent bindings                                     | Single agent                                                    |
| **Скиллы**         | 60+ bundled, ClawHub                                                                     | Skill Forge from GitHub                                                                         | ClawHub registry, markdown skills                                        | Workspace skills (runtime loading)                              |
| **Voice**          | TTS (ElevenLabs), STT (Deepgram), wake words                                             | Whisper STT                                                                                     | -                                                                        | -                                                               |
| **Observability**  | Structured logging, WebSocket logs                                                       | OpenTelemetry, health checks, audit                                                             | Event bus, log viewer                                                    | JobRunr dashboard                                               |
| **Deploy**         | Docker, npm global, native apps, systemd                                                 | 678KB binary, Docker, systemd/launchctl, Cloudflare Workers                                     | Single binary, Docker, Android APK, macOS .app                           | Docker (Jib), bootRun                                           |

---

## Приоритизация для Enterprise JavaClaw

### Контекст приоритизации

- Сервис в Docker/облаке, горизонтальный скейлинг
- Streamable MCP как основной протокол интеграции
- PostgreSQL как единый источник правды (синхронизация инстансов)
- Корпоративная авторизация (OAuth2/OIDC/SAML)
- GigaPlatform как целевой LLM-провайдер (пока OpenRouter)
- Красивый SPA UI (порт из OpenClaw/PicoClaw на Vite + React)
- Отзывчивость агента (streaming, async, non-blocking)

---

### P0 — Критический фундамент (без этого не enterprise)

| # |              Возможность              |                 Источник вдохновения                 |                                                                 Обоснование                                                                  |
|---|---------------------------------------|------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------|
| 1 | **Vite + React SPA UI**               | PicoClaw (React 19 + TanStack), OpenClaw (Lit)       | Замена htmx на полноценный SPA. Бут отдаёт статику. Real-time через WebSocket/SSE. Красивый чат, conversation management, settings UI        |
| 2 | **JDBC-first persistence everywhere** | NullClaw (10 движков), текущий JavaClaw (уже начато) | Все состояния через PostgreSQL: чаты, задачи, конфиги, MCP-серверы. Основа для скейлинга — любой инстанс читает одну БД                      |
| 3 | **Streaming agent responses**         | Все три Claw                                         | Spring AI ChatClient streaming → SSE/WebSocket push. Токен-по-токену отображение в UI. Non-blocking reactor pattern                          |
| 4 | **Корпоративная авторизация**         | NullClaw (pairing + audit), OpenClaw (token/OAuth)   | Spring Security + OAuth2/OIDC. Интеграция с внутренним IdP. Role-based access. Session management через БД                                   |
| 5 | **Docker-native deployment**          | Все (у всех Docker)                                  | Jib уже есть. Добавить: health checks (/actuator/health), graceful shutdown, externalized config, secrets через env/vault, multi-stage build |

### P1 — Ключевые возможности агента

| #  |             Возможность              |                        Источник вдохновения                         |                                                                     Обоснование                                                                     |
|----|--------------------------------------|---------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------|
| 6  | **Расширенная система инструментов** | NullClaw (35+ tools), OpenClaw (Playwright, web search, image gen)  | Унифицированный Tool interface. Auto-discovery. Web search (Brave/DuckDuckGo), web fetch, file ops, shell exec с sandbox. Каждый tool — Spring bean |
| 7  | **Продвинутый MCP**                  | PicoClaw (native MCP), NullClaw (stdio + HTTP)                      | Streamable HTTP MCP как first-class. Динамическая регистрация. Health check MCP-серверов. Reconnect. Tool discovery и caching                       |
| 8  | **Мульти-агент / Sub-agent**         | PicoClaw (SubTurn), NullClaw (delegate), OpenClaw (multi-workspace) | Agent routing по каналу/пользователю. Delegation tool для запуска подзадач. Изолированные контексты. Очередь задач между агентами                   |
| 9  | **Система скиллов**                  | PicoClaw (ClawHub), OpenClaw (60+ skills)                           | Markdown-based skills в workspace. Горячая загрузка. Skill registry (внутренний hub или Spring Cloud Config). Версионирование                       |
| 10 | **Расширенная память**               | NullClaw (hybrid retrieval), OpenClaw (LanceDB)                     | JDBC chat memory + vector search. Semantic retrieval для длинных историй. Summarization при превышении окна контекста                               |

### P2 — Масштабирование и операционная зрелость

| #  |         Возможность          |                        Источник вдохновения                        |                                                                         Обоснование                                                                         |
|----|------------------------------|--------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 11 | **Observability stack**      | NullClaw (OpenTelemetry), OpenClaw (structured logging)            | Micrometer + Prometheus metrics. Distributed tracing (OTLP). Structured logging (JSON). Dashboard метрик агента (tokens, latency, errors)                   |
| 12 | **Rate limiting & quotas**   | NullClaw (per-IP/token), OpenClaw (rate limiting)                  | Per-user/per-org лимиты на API-запросы и LLM-токены. Cost tracking. Quota classes                                                                           |
| 13 | **Multi-tenancy**            | Уникально для enterprise                                           | Изоляция по организациям/командам. Tenant-aware routing. Shared infrastructure, isolated data                                                               |
| 14 | **Horizontal scaling**       | Архитектурное требование                                           | Stateless app instances. Shared DB state. Distributed job scheduling (JobRunr уже поддерживает). WebSocket session affinity или Redis pub/sub для broadcast |
| 15 | **Configuration management** | PicoClaw (env overrides + hot reload), NullClaw (30+ config types) | Spring Cloud Config или DB-backed config. Hot reload без рестарта. Per-tenant config overrides. Feature flags                                               |

### P3 — Расширение каналов и интеграций

| #  |        Возможность        |                Источник вдохновения                 |                                                    Обоснование                                                     |
|----|---------------------------|-----------------------------------------------------|--------------------------------------------------------------------------------------------------------------------|
| 16 | **Дополнительные каналы** | OpenClaw (23+), NullClaw (19+), PicoClaw (18+)      | Slack, Teams, Matrix, Email — по приоритету корпоративных потребностей. Plugin-based architecture уже есть         |
| 17 | **Voice/STT/TTS**         | OpenClaw (ElevenLabs, Deepgram), NullClaw (Whisper) | Голосовой ввод/вывод для accessibility и мобильных сценариев                                                       |
| 18 | **A2A Protocol**          | NullClaw (Agent-to-Agent)                           | Взаимодействие между инстансами JavaClaw и внешними агентами. Service mesh для AI                                  |
| 19 | **Skill Hub**             | PicoClaw (ClawHub), OpenClaw (ClawHub)              | Централизованный реестр скиллов. Push updates. Версионирование. Approval workflow для корпоративного использования |
| 20 | **Browser automation**    | OpenClaw (Playwright), текущий JavaClaw (plugin)    | Расширение Playwright plugin: screenshots, form filling, scraping. Sandbox execution                               |

### P4 — Продвинутые возможности

| #  |          Возможность          |                  Источник вдохновения                   |                                      Обоснование                                      |
|----|-------------------------------|---------------------------------------------------------|---------------------------------------------------------------------------------------|
| 21 | **Sensitive data filtering**  | PicoClaw (auto-redaction), NullClaw (workspace scoping) | Автоматическая фильтрация секретов из tool output перед отправкой в LLM. DLP policies |
| 22 | **Audit logging**             | NullClaw (security audit), OpenClaw (audit trails)      | Полный audit trail: кто, что, когда, через какой канал. Compliance-ready              |
| 23 | **Sandbox execution**         | NullClaw (Landlock/Firejail/Bubblewrap/Docker)          | Изолированное выполнение shell/code tools. Контейнерная песочница для tool execution  |
| 24 | **Cost tracking & analytics** | NullClaw (per-call cost)                                | Учёт стоимости LLM-вызовов per user/org/agent. Dashboards. Budget alerts              |
| 25 | **Workflow engine**           | Архитектурное расширение                                | Сложные multi-step workflows с условиями, параллельностью, human-in-the-loop approval |

---

## Рекомендуемый порядок реализации (роадмап)

```
Phase 1 — MVP Enterprise (8-10 недель)
├── P0.2  JDBC persistence everywhere (чаты, задачи, конфиг — всё в PG)
├── P0.3  Streaming agent responses (SSE → WebSocket)
├── P0.1  Vite + React SPA UI (порт чата, онбординг, настройки)
├── P0.4  Spring Security + OAuth2/OIDC
└── P0.5  Docker hardening (health, graceful shutdown, secrets)

Phase 2 — Agent Power (6-8 недель)
├── P1.6  Расширенные инструменты (web search, fetch, exec)
├── P1.7  Продвинутый Streamable MCP
├── P1.9  Система скиллов с hot reload
├── P1.10 Расширенная память (vector + summarization)
└── P1.8  Multi-agent routing

Phase 3 — Scale & Ops (4-6 недель)
├── P2.11 Observability (metrics, tracing, logging)
├── P2.14 Horizontal scaling (stateless + Redis pub/sub)
├── P2.12 Rate limiting & quotas
└── P2.15 Configuration management

Phase 4 — Ecosystem (ongoing)
├── P3.16 Новые каналы (Slack, Teams, Matrix)
├── P3.19 Internal Skill Hub
├── P4.21 Sensitive data filtering
├── P4.22 Audit logging
└── P4.24 Cost tracking
```

---

## Ключевые архитектурные решения

1. **UI**: Vite + React (как PicoClaw) вместо htmx. Boot раздаёт статику через ResourceHandler. API — REST + WebSocket
2. **State**: PostgreSQL everywhere. Flyway миграции. Spring Data JDBC. Нет in-memory state кроме кэшей
3. **Scaling**: Stateless Spring Boot instances за load balancer. JobRunr distributed scheduling. Redis для WebSocket broadcast между инстансами
4. **Auth**: Spring Security OAuth2 Resource Server. JWT от корпоративного IdP. Per-endpoint authorization
5. **MCP**: Spring AI MCP Client (Streamable HTTP). Persistent connections. Auto-reconnect. Tool capability caching
6. **Provider abstraction**: Spring AI ChatModel interface. GigaPlatform как custom ChatModel implementation. Fallback chains
7. **Plugins**: Spring Boot auto-configuration. Каждый канал/провайдер — отдельный модуль. Classpath-based discovery

