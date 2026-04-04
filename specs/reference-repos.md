# Reference Repos: откуда брать аналоги

> Все репозитории выкачаны локально в `/Users/artemsimeisn/IdeaProjects/sber/aihub/`

---

## Репозитории

|    Проект    |            Язык            |      Путь      |
|--------------|----------------------------|----------------|
| **OpenClaw** | TypeScript (Lit + Express) | `../openclaw/` |
| **NullClaw** | Zig                        | `../nullclaw/` |
| **PicoClaw** | Go (React frontend)        | `../picoclaw/` |

---

## Карта директорий по фичам

### UI

|                Что смотреть                |  Проект  |                   Путь                   |
|--------------------------------------------|----------|------------------------------------------|
| React SPA (основной референс)              | PicoClaw | `web/frontend/src/`                      |
| Компоненты чата, хаб скиллов               | PicoClaw | `web/frontend/src/components/`           |
| Роуты, стор, хуки                          | PicoClaw | `web/frontend/src/{routes,store,hooks}/` |
| Web API (Go, но архитектура полезна)       | PicoClaw | `web/backend/api/`                       |
| Lit web components (альтернативный подход) | OpenClaw | `ui/src/ui/`                             |

### Agent Core

|          Что смотреть           |  Проект  |                            Путь                            |
|---------------------------------|----------|------------------------------------------------------------|
| Agent orchestration, dispatcher | NullClaw | `src/agent/` — `agent.zig`, `dispatcher.zig`, `prompt.zig` |
| Agent context, event bus, hooks | PicoClaw | `pkg/agent/` — `context.go`, `eventbus.go`                 |
| Agent engine (737 файлов)       | OpenClaw | `src/agents/`                                              |
| Streaming                       | NullClaw | `src/streaming.zig`                                        |

### Channels

|               Что смотреть               |  Проект  |                   Путь                    |
|------------------------------------------|----------|-------------------------------------------|
| 19 каналов (Discord, Telegram, Slack...) | NullClaw | `src/channels/`                           |
| 18 каналов + channel manager             | PicoClaw | `pkg/channels/` — `manager.go`, `base.go` |
| 95+ расширений-каналов                   | OpenClaw | `extensions/`                             |

### Tools

|                     Что смотреть                     |  Проект  |                    Путь                     |
|------------------------------------------------------|----------|---------------------------------------------|
| 35+ инструментов (shell, git, browser, memory, cron) | NullClaw | `src/tools/`                                |
| Registry, MCP tool, filesystem, cron                 | PicoClaw | `pkg/tools/` — `registry.go`, `mcp_tool.go` |
| Plugin SDK для расширений                            | OpenClaw | `src/plugin-sdk/`                           |

### MCP

|         Что смотреть          |  Проект  |                  Путь                  |
|-------------------------------|----------|----------------------------------------|
| MCP manager + tool интеграция | PicoClaw | `pkg/mcp/` — `manager.go`              |
| MCP config + composio         | NullClaw | `src/tools/composio.zig`, config types |
| MCP + plugin SDK              | OpenClaw | `src/mcp/`                             |

### Persistence / Memory

|                    Что смотреть                    |  Проект  |                           Путь                            |
|----------------------------------------------------|----------|-----------------------------------------------------------|
| 5 движков (SQLite, PG, Redis, ClickHouse, LanceDB) | NullClaw | `src/memory/` — `sqlite.zig`, `postgres.zig`, `redis.zig` |
| Hybrid retrieval (BM25 + vector + RRF)             | NullClaw | `src/memory/` — `engine.zig`, `query_expansion.zig`       |
| JSONL sessions                                     | PicoClaw | `pkg/session/` — `jsonl_backend.go`                       |
| LanceDB vector + sessions                          | OpenClaw | `src/sessions/`, `src/context-engine/`                    |

### Auth / Security

|                      Что смотреть                      |  Проект  |                            Путь                            |
|--------------------------------------------------------|----------|------------------------------------------------------------|
| Sandbox (Landlock, Firejail, Docker), pairing, secrets | NullClaw | `src/security/` — `auth.zig`, `sandbox.zig`, `pairing.zig` |
| OAuth, PKCE, token store                               | PicoClaw | `pkg/auth/` — `oauth.go`, `store.go`                       |
| Token/password auth, device pairing                    | OpenClaw | `src/security/`, `src/secrets/`, `src/acp/`                |

### Skills

|            Что смотреть             |  Проект  |                   Путь                   |
|-------------------------------------|----------|------------------------------------------|
| ClawHub registry, loader, installer | PicoClaw | `pkg/skills/` — `clawhub_registry.go`    |
| ClawHub UI (marketplace)            | PicoClaw | `web/frontend/src/components/agent/hub/` |
| 55+ bundled skills                  | OpenClaw | `skills/`                                |
| Skill Forge                         | NullClaw | workspace templates                      |

### Providers (LLM)

|           Что смотреть           |  Проект  |                              Путь                              |
|----------------------------------|----------|----------------------------------------------------------------|
| 50+ провайдеров (vtable-driven)  | NullClaw | `src/providers/` — `anthropic.zig`, `openai.zig`, `ollama.zig` |
| Factory, fallback, rate limiting | PicoClaw | `pkg/providers/` — `factory_provider.go`, `fallback.go`        |
| 95+ расширений-провайдеров       | OpenClaw | `extensions/`                                                  |

### Tasks / Cron

|       Что смотреть       |  Проект  |           Путь            |
|--------------------------|----------|---------------------------|
| Cron + JSON persistence  | NullClaw | `src/tools/cron_*.zig`    |
| Cron + heartbeat + spawn | PicoClaw | `pkg/tools/cron.go`       |
| Croner + task model      | OpenClaw | `src/cron/`, `src/tasks/` |

### Observability

|            Что смотреть            |  Проект  |                  Путь                  |
|------------------------------------|----------|----------------------------------------|
| OpenTelemetry, health, audit       | NullClaw | `src/` (distributed)                   |
| Event bus, logging                 | PicoClaw | `pkg/logger/`, `pkg/agent/eventbus.go` |
| Structured logging, WebSocket logs | OpenClaw | `src/logging/`                         |

### Config

|        Что смотреть         |  Проект  |                Путь                 |
|-----------------------------|----------|-------------------------------------|
| 30+ config types, bootstrap | NullClaw | `src/bootstrap/`, `src/config*.zig` |
| Env overrides, hot reload   | PicoClaw | `pkg/config/`                       |
| 262 config modules          | OpenClaw | `src/config/`                       |

---

## Маппинг: фаза Roadmap → что смотреть

|         Фаза Roadmap          |                       Ключевой референс                        |                  Что именно смотреть                  |
|-------------------------------|----------------------------------------------------------------|-------------------------------------------------------|
| **Phase 0** Подготовка        | Все три                                                        | Архитектура модулей, API contracts                    |
| **Phase 1** Фундамент данных  | **NullClaw** `src/memory/`                                     | PostgreSQL engine, schema, migrations                 |
| **Phase 2** Агентное ядро     | **NullClaw** `src/agent/`, **PicoClaw** `pkg/agent/`           | Agent loop, streaming, prompt assembly, tool calling  |
| **Phase 3** REST API          | **PicoClaw** `web/backend/api/`                                | REST endpoints, SSE streaming, CRUD                   |
| **Phase 4** Basic Auth        | **PicoClaw** `pkg/auth/`, **NullClaw** `src/security/`         | OAuth flow, session management, per-user isolation    |
| **Phase 5** React SPA         | **PicoClaw** `web/frontend/src/`                               | React компоненты, роутинг, стор, чат UI, file manager |
| **Phase 6** Docker            | Все три                                                        | Dockerfile, health checks, graceful shutdown          |
| **Phase 7** User management   | **NullClaw** `src/security/auth.zig`                           | User entity, roles, DB-backed auth                    |
| **Phase 8** Расширение агента | **NullClaw** `src/agent/`, **PicoClaw** `pkg/agent/`           | Steering, context management, few-shot, thinking mode |
| **Phase 9** MCP + A2A         | **PicoClaw** `pkg/mcp/`, **NullClaw** `src/tools/composio.zig` | MCP health, caching, server mode, A2A                 |
| **Phase 10** Memory + Tools   | **NullClaw** `src/memory/`, `src/tools/`                       | Hybrid retrieval, summarization, tool deny patterns   |
| **Phase 11** UI расширение    | **PicoClaw** `web/frontend/src/`                               | Theme, settings, file upload, timeline                |
| **Phase 12** Security         | **NullClaw** `src/security/`                                   | Audit trail, quotas, sandbox                          |
| **Phase 13-16** RBAC + Scale  | **NullClaw** `src/security/`, **OpenClaw** `src/acp/`          | Granular permissions, vector memory, multi-tenancy    |

---

## Дополнительные ресурсы

|        Ресурс        |           Путь            |
|----------------------|---------------------------|
| OpenSpec дистиллят   | `../openspec-distillate/` |
| OpenSpec skills      | `../openspec-skills/`     |
| Skills training data | `../skills-training/`     |

