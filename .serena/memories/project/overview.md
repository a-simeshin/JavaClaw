# JavaClaw — Project Overview

## Purpose

Enterprise AI agent platform (Spring Boot backend + React SPA frontend). Multi-tenant, multi-channel (Web, Telegram, Discord) AI assistant with task management, approval workflows, delivery queues, and audit logging.

## Tech Stack

- **Java 21**, Spring Boot 4.0.5, Spring AI 2.0.0-M4, Spring Modulith 2.0.3
- **Database**: PostgreSQL (primary, Flyway migrations) + SQLite (debug/lightweight mode)
- **Frontend**: React 19, TypeScript, Vite, TailwindCSS 4, Radix UI, TanStack Router + Query, Jotai, i18next
- **Testing**: JUnit 5, AssertJ, Mockito, Testcontainers (PostgreSQL), Playwright (E2E)
- **Build**: Maven (mvnw wrapper), pnpm (frontend)
- **Channels**: Telegram (telegrambots 9.4.0), Discord (JDA 6.1.1)
- **Background jobs**: JobRunr 8.5.1
- **Code quality**: Spotless (Palantir Java Format), PMD, SpotBugs, JaCoCo
- **MCP**: Spring AI MCP server (Streamable HTTP at /api/mcp)

## Module Structure

```
javaclaw-parent (pom)
├── javaclaw-core/javaclaw-memory  — Chat memory persistence (Spring Data JDBC)
├── javaclaw-core                  — Domain: agent, pipeline, tasks, delivery, audit, channels, files, users, skills, mcp, conversations
├── javaclaw-security              — Auth, session, RBAC
├── javaclaw-api/
│   ├── javaclaw-api-admin         — Admin REST API
│   └── javaclaw-api-chat          — Chat REST API (SSE streaming)
├── javaclaw-channel/
│   ├── javaclaw-channel-telegram  — Telegram bot integration
│   └── javaclaw-channel-discord   — Discord bot integration
├── javaclaw-provider/
│   ├── javaclaw-provider-anthropic
│   ├── javaclaw-provider-google
│   ├── javaclaw-provider-ollama
│   └── javaclaw-provider-openai   — LLM provider adapters
├── javaclaw-frontend              — React SPA (Vite, pnpm)
├── javaclaw-app                   — Spring Boot application, CLI config generator, integration tests
└── javaclaw-e2e                   — End-to-end tests (Playwright + live API)
```

## Key Domain Concepts

- **Agent/ChatService**: LLM pipeline — prompt assembly → streaming/call → persist
- **Tasks**: Background tasks with executions, approvals, cancellation tokens, watchdog, rate limiter
- **Delivery**: Notification queue with recovery for channel message delivery
- **Audit**: Chat, task, delivery, auth audit logs
- **Channels**: RoutingContext + ChannelContextService for multi-channel routing
- **Conversations**: Isolated per-user, with sharing and summaries
- **Skills**: Pluggable agent skills with visibility and usage audit
- **MCP**: Model Context Protocol server + external MCP connections with health checks

## Database

- Flyway migrations in `javaclaw-core/src/main/resources/db/migration/{vendor}/` (postgresql/ and sqlite/)
- Spring profiles: `postgres` (default), `sqlite` (debug), `contracttest` (Testcontainers)

