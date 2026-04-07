# JavaClaw (Fun Fork)

Fork of [JavaClaw](https://github.com/jobrunr/JavaClaw) for building an enterprise-ready AI agent on Java/Spring Boot.

Goal — transform a single-user pet project into a scalable service: multi-user support, role-based access, virtual FS in a persistent database, Streamable MCP, A2A, and a polished React SPA instead of htmx.

> Weekend coding project with serious ambitions: bring it into the enterprise.

## What's Already Here (from the original)

- Spring Boot 4 + Spring AI 2.0 + Spring Modulith
- Agent loop with tool calling (Spring AI ChatClient)
- MCP Client (stdio + streamable HTTP)
- JobRunr for background tasks and cron
- Plugin-based channels (Web, Telegram, Discord)
- Workspace skills (runtime loading)

## Conversation-Aware Channel Routing

Multi-user message routing through a per-conversation context table. Each channel saves routing data (chatId, threadId, channelId) when a message arrives; async tasks use this data to deliver notifications back to the correct user.

### How it works

```
User message → Channel.consume()
  → ChannelContextService.saveContext(conversationId, channelName, routingData)
  → agent.respondTo(conversationId, message)
    → TaskTool.createTask(name, desc, conversationId)

Task completes → TaskHandler.notifyUser()
  → ChannelContextService.getContext(conversationId)
  → channelRegistry.getChannel(ctx.channelName)
  → channel.sendMessage(routingContext, message)
```

### Routing data per channel

| Channel | conversationId | routingData |
|---------|---------------|-------------|
| Telegram | `telegram-{chatId}[-{threadId}]` | `{chatId, threadId}` |
| Discord | `discord-{channelId}` | `{channelId}` |
| Web Chat | `web-{uuid}` | `{conversationId}` |

### Multi-user isolation

Two Telegram users chatting simultaneously:

- User A (chatId=100) &rarr; context saved as `telegram-100`
- User B (chatId=200) &rarr; context saved as `telegram-200`

Task from User A completes &rarr; lookup `telegram-100` &rarr; `{chatId: 100}` &rarr; notification goes to User A only. No instance-level state, no cross-talk.

### Recurring (cron) tasks

Recurring tasks are not tied to a conversation. Notifications fall back to the default channel (first registered, typically Web Chat). To bind a recurring task to a specific user, `conversationId` support in `RecurringTask` can be added later.

### Comparison with other Claw implementations

| Aspect | JavaClaw | OpenClaw (TS) | NullClaw (Zig) | PicoClaw (Go) |
|--------|----------|---------------|----------------|---------------|
| **Send signature** | `sendMessage(RoutingContext, String)` | `sendMessage(MessageSendParams): Promise` | `send(target, message, media): !void` | `Send(ctx, OutboundMessage): ([]string, error)` |
| **Routing mechanism** | DB lookup by conversationId | 10-step binding hierarchy + session keys | Channel vtable dispatch + registry | OutboundMessage Channel+ChatID pair |
| **Multi-user isolation** | `conversation_channel_context` table (per-conversation routing data in DB) | Config-driven bindings + session key encoding (agentId/peer/thread) | Per-entry account_id + listener supervision | Sender allowList + IsAllowed check |
| **State storage** | PostgreSQL (survives restarts) | File-based JSON sessions | In-memory vtable + on-disk agent sessions | In-memory Manager maps |
| **Thread support** | threadId in routing data | Session key encodes threadId/topicId | MessageRef (target + message_id) | Placeholder tracking + message ID return |
| **Task notification routing** | TaskHandler &rarr; ChannelContextService &rarr; Channel | Deterministic reply to source channel via session | Dispatch via ChannelRegistry | Manager.SendMessage via worker queue |
| **Survives restart** | Yes (DB-backed) | Partially (file sessions) | No (in-memory) | No (in-memory) |

## What We're Building

Turning it into an enterprise platform. Full catalog — in [specs/capabilities-catalog.md](specs/capabilities-catalog.md).

## Roadmap

Full details — in [specs/roadmap.md](specs/roadmap.md).

Methodology: **Spec → Test → Dev → Verify → Fix Spec** (SDD + TDD)

### Phase 0: Preparation

- Architectural decisions (frontend stack, streaming protocol, API contract)
- Legacy cleanup (onboarding wizard, FileSystem memory, Playwright/Brave plugins)

### Phase 1: Data Foundation

- DB Schema + Flyway migrations
- Virtual filesystem in PostgreSQL (replacing files on disk)
- JDBC Chat Memory
- Skills, MCP servers, Tasks — all in DB

### Phase 2: Agent Core

- Agent loop + SSE streaming
- System prompt from DB (AGENT.md + SOUL.md + USER_AGENT.md)
- Agent environment (per-user context)
- Skill management tool

### Phase 3: REST API

- OpenAPI contract
- Chat API + SSE streaming
- Files, Skills, MCP Servers API
- Actuator + health checks

### Phase 4: Basic Auth

- Spring Security + Basic Auth (admin/user from config)
- Per-user isolation of dialogs and files

### Phase 5: React SPA

- Vite + React 19 + TypeScript + TanStack Router
- Chat UI with markdown, streaming, tool call visualization
- File manager, Admin UI, User workspace UI

### Phase 6: Docker

- Production-ready image (Jib)
- Docker Compose (app + PostgreSQL)
- Graceful shutdown, health checks

> **=== P0 DONE ===**

### Phase 7-12: P1 — Enterprise Core

- Users in DB, ADMIN/USER roles
- Steering / interruption (correcting the agent on the fly)
- Dreamin (long-term memory a la Anthropic Memory)
- FewShotExamples for GigaChat API
- MCP as server + A2A
- Dark/light theme, config generator CLI, SQLite backend

### Phase 13-16: P2 — Full RBAC and Scale

- Granular permissions, role hierarchy
- Skill/MCP access per role
- Vector memory + hybrid retrieval
- Multi-tenancy, horizontal scaling

### Phase 17+: P3-P5

- External channels (Slack, Teams, Telegram...)
- OAuth2/OIDC/SSO
- Voice, multimedia
- GraalVM native

## Tech Stack (target)

|   Layer    |                      Technology                       |
|------------|-------------------------------------------------------|
| Language   | Java 21+                                              |
| Framework  | Spring Boot 4, Spring Modulith, Spring Security       |
| AI         | Spring AI 2.0 (ChatClient, MCP Client, Observability) |
| Frontend   | React 19 + Vite + TanStack Router                     |
| Database   | PostgreSQL (primary), SQLite (dev/single-node)        |
| Migrations | Flyway                                                |
| Jobs       | JobRunr                                               |
| Streaming  | SSE / AG-UI                                           |
| Deploy     | Docker (Jib), Docker Compose                          |

## Quick Start

```bash
# Start PostgreSQL
docker compose -f docker-compose.dev.yml up -d

# Run the application
./gradlew :app:bootRun
```

## Project Structure

```
JavaClaw/
├── base/           # Core: agent, tasks, tools, channels, memory
├── app/            # Spring Boot entry, web chat, API
├── plugins/        # Channel plugins (Discord, Telegram)
├── providers/      # LLM providers (OpenAI, Anthropic, Ollama, Google)
├── specs/          # Specifications, roadmap, capability catalogs
└── .claude/        # Claude Code: skills, templates, references, hooks
```

## Specifications

|                                  Document                                  |                        Description                         |
|----------------------------------------------------------------------------|------------------------------------------------------------|
| [capabilities-catalog.md](specs/capabilities-catalog.md)                   | Full capability catalog (~130 items) with P0-P5 priorities |
| [roadmap.md](specs/roadmap.md)                                             | Detailed roadmap with sequencing and dependencies          |
| [skills-catalog.md](specs/skills-catalog.md)                               | Skills catalog from the Claw ecosystem                     |
| [feature-matrix-and-priorities.md](specs/feature-matrix-and-priorities.md) | Comparison matrix: OpenClaw/NullClaw/PicoClaw/JavaClaw     |

## Tests

```bash
./gradlew test
```

## License

See [LICENSE](license.md).
