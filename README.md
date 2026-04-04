# JavaClaw (Enterprise Fork)

Форк [JavaClaw](https://github.com/jobrunr/JavaClaw) для создания enterprise-ready AI-агента на Java/Spring Boot.

Цель — превратить single-user pet-project в масштабируемый сервис: много пользователей, ролевая модель, виртуальная FS в PostgreSQL, Streamable MCP, A2A, красивый React SPA вместо htmx.

> Weekend coding project с серьёзными амбициями: затащить в большой enterprise.

## Что уже есть (из оригинала)

- Spring Boot 4 + Spring AI 2.0 + Spring Modulith
- Agent loop с tool calling (Spring AI ChatClient)
- MCP Client (stdio + streamable HTTP)
- JobRunr для фоновых задач и cron
- Plugin-based каналы (Web, Telegram, Discord)
- Workspace skills (runtime loading)

## Что делаем

Превращаем в enterprise-платформу. Полный каталог — в [specs/capabilities-catalog.md](specs/capabilities-catalog.md).

## Roadmap

Полная детализация — в [specs/roadmap.md](specs/roadmap.md).

Методология: **Spec → Test → Dev → Verify → Fix Spec** (SDD + TDD)

### Phase 0: Подготовка
- Архитектурные решения (стек фронтенда, протокол streaming, API contract)
- Чистка легаси (onboarding wizard, FileSystem memory, Playwright/Brave plugins)

### Phase 1: Фундамент данных
- DB Schema + Flyway миграции
- Virtual filesystem в PostgreSQL (замена файлов на диске)
- JDBC Chat Memory
- Skills, MCP-серверы, Tasks — всё в БД

### Phase 2: Агентное ядро
- Agent loop + SSE streaming
- System prompt из DB (AGENT.md + SOUL.md + USER_AGENT.md)
- Agent environment (per-user контекст)
- Skill management tool

### Phase 3: REST API
- OpenAPI contract
- Chat API + SSE streaming
- Files, Skills, MCP Servers API
- Actuator + health checks

### Phase 4: Basic Auth
- Spring Security + Basic Auth (admin/user из конфига)
- Per-user изоляция диалогов и файлов

### Phase 5: React SPA
- Vite + React 19 + TypeScript + TanStack Router
- Chat UI с markdown, streaming, tool call visualization
- File manager, Admin UI, User workspace UI

### Phase 6: Docker
- Production-ready образ (Jib)
- Docker Compose (app + PostgreSQL)
- Graceful shutdown, health checks

> **=== P0 DONE ===**

### Phase 7-12: P1 — Enterprise ядро
- Users в БД, роли ADMIN/USER
- Steering / interruption (коррекция агента на лету)
- Dreamin (long-term memory а-ля Anthropic Memory)
- FewShotExamples для GigaChat API
- MCP as server + A2A
- Dark/light theme, config generator CLI, SQLite backend

### Phase 13-16: P2 — Полный RBAC и масштаб
- Гранулярные permissions, role hierarchy
- Skill/MCP access per role
- Vector memory + hybrid retrieval
- Multi-tenancy, horizontal scaling

### Phase 17+: P3-P5
- Внешние каналы (Slack, Teams, Telegram...)
- OAuth2/OIDC/SSO
- Voice, multimedia
- GraalVM native

## Tech Stack (target)

| Слой | Технология |
|---|---|
| Language | Java 21+ |
| Framework | Spring Boot 4, Spring Modulith, Spring Security |
| AI | Spring AI 2.0 (ChatClient, MCP Client, Observability) |
| Frontend | React 19 + Vite + TanStack Router |
| Database | PostgreSQL (primary), SQLite (dev/single-node) |
| Migrations | Flyway |
| Jobs | JobRunr |
| Streaming | SSE / AG-UI |
| Deploy | Docker (Jib), Docker Compose |

## Быстрый старт

```bash
# Поднять PostgreSQL
docker compose -f docker-compose.dev.yml up -d

# Запустить приложение
./gradlew :app:bootRun
```

## Структура проекта

```
JavaClaw/
├── base/           # Core: agent, tasks, tools, channels, memory
├── app/            # Spring Boot entry, web chat, API
├── plugins/        # Channel plugins (Discord, Telegram)
├── providers/      # LLM providers (OpenAI, Anthropic, Ollama, Google)
├── specs/          # Спецификации, roadmap, каталоги возможностей
└── .claude/        # Claude Code: skills, templates, references, hooks
```

## Спецификации

| Документ | Описание |
|---|---|
| [capabilities-catalog.md](specs/capabilities-catalog.md) | Полный каталог возможностей (~130 пунктов) с приоритетами P0-P5 |
| [roadmap.md](specs/roadmap.md) | Детальный roadmap с очерёдностью и зависимостями |
| [skills-catalog.md](specs/skills-catalog.md) | Каталог скиллов из экосистемы Claw |
| [feature-matrix-and-priorities.md](specs/feature-matrix-and-priorities.md) | Сравнительная матрица OpenClaw/NullClaw/PicoClaw/JavaClaw |

## Тесты

```bash
./gradlew test
```

## License

See [LICENSE](license.md).
