# Specification: javaclaw-service

Version 1.0

* [1. General Information](#1-general-information)
* [2. Functional Requirements](#2-functional-requirements)
  * [2.1. Primary Use Cases](#21-primary-use-cases)
  * [2.2. Alternative and Error Scenarios](#22-alternative-and-error-scenarios)
  * [2.3. Business Rules and Processing Logic](#23-business-rules-and-processing-logic)
  * [2.4. API](#24-api)
  * [2.5. Integrations](#25-integrations)
  * [2.6. Data Models](#26-data-models)
  * [2.7. Configuration](#27-configuration)
  * [2.8. Logging](#28-logging)
  * [2.9. Input Validation](#29-input-validation)
  * [2.10. Error Handling](#210-error-handling)
  * [2.11. Headers and Metadata](#211-headers-and-metadata)
* [3. Acceptance Criteria Recommendations](#3-acceptance-criteria-recommendations)
* [4. Non-Functional Requirements](#4-non-functional-requirements)
  * [4.1. Security](#41-security)
  * [4.2. Performance](#42-performance)
  * [4.3. Reliability](#43-reliability)
  * [4.4. Monitoring](#44-monitoring)
  * [4.5. Audit](#45-audit)

---

## 1. General Information

**javaclaw-service** is an open-source platform for building AI agents, designed for Java developers. It provides an easily extensible service for both individual and enterprise use.

The platform enables developers to:
- Deploy an AI agent with a conversational interface accessible via Web UI, Telegram, or Discord
- Connect any AI provider (Anthropic, OpenAI, Google GenAI, Ollama) through a unified onboarding flow
- Extend the agent's capabilities at runtime by registering MCP (Model Context Protocol) servers
- Manage one-time and recurring tasks that the agent executes autonomously
- Customize agent behavior through a system prompt file (AGENT.md — analogous to SOUL in the Claw ecosystem)

The service is built on Spring Boot 4, Spring AI 2, and Spring Modulith. It is distributed under an open-source license and targets multi-user deployments with high load and auto-scaling capabilities.

**Technology stack:**
- Java 21, Spring Boot 4.0.3, Spring AI 2.0.0-SNAPSHOT, Spring Modulith 2.0.3
- Spring Data JDBC, PostgreSQL, Flyway
- JobRunr 8.5.1 (task scheduling)
- Pebble 4.1.1 (HTML templating)
- WebSocket + HTMX (Web UI)
- JDA 6.1.1 (Discord), TelegramBots 9.4.0 (Telegram)
- Playwright 1.52.0 (browser automation)
- Maven multi-module build

**Module structure:**
- **base** — core framework: agent, memory, channels, tasks, tools, configuration, MCP, onboarding abstractions
- **app** — Spring Boot application: entry point, Web UI chat, onboarding controller, WebSocket handler
- **providers/anthropic** — Anthropic Claude provider integration
- **providers/google** — Google GenAI provider integration
- **providers/openai** — OpenAI provider integration
- **providers/ollama** — Ollama (local LLM) provider integration
- **plugins/telegram** — Telegram channel plugin
- **plugins/discord** — Discord channel plugin
- **plugins/playwright** — Playwright browser automation tool
- **plugins/brave** — Brave Web Search tool

---

## 2. Functional Requirements

### 2.1. Primary Use Cases

#### 2.1.1. Onboarding — Initial Agent Configuration

1. User navigates to the application root URL and is redirected to `/onboarding`.
2. The system presents a 6-step wizard:
   - **Step 1 (Welcome)**: Introduction page with no user input required.
   - **Step 2 (Provider)**: User selects an AI provider (Anthropic, OpenAI, Google GenAI, Ollama). The system displays available providers with labels and slogans. If a provider has a system-wide token (e.g., Anthropic Claude Code OAuth token), it is detected automatically.
   - **Step 3 (Credentials)**: User enters the API key and optionally a base URL for the selected provider. Providers with auto-detected tokens MAY skip this step.
   - **Step 4 (Agent MD)**: User reviews and edits the agent system prompt (`AGENT.private.md`). A default prompt is loaded from the workspace.
   - **Step 5 (MCP)**: User optionally configures MCP server connections (Streamable HTTP or Stdio).
   - **Step 6 (Complete)**: Summary page. The system sets `agent.onboarding.completed=true` in configuration.
3. Configuration is persisted to `application.private.yaml` via ConfigurationManager.
4. The application restarts automatically (2-second delay) to apply the new configuration.
5. User is redirected to the chat interface.

#### 2.1.2. Conversational Interaction via Web UI

1. User opens `/chat` in a browser. The system renders the chat page with a WebSocket connection.
2. Upon WebSocket connection establishment, the system:
   - Loads the list of available conversation IDs (default: `"web"`).
   - Renders conversation history as HTML bubbles (user and agent messages).
   - Renders the chat input area.
3. User types a message and submits.
4. The system displays a typing indicator animation.
5. The message is dispatched to the agent via `ChatChannel.chat(conversationId, message)`.
6. The agent processes the message using the configured ChatClient with advisors:
   - **MessageChatMemoryAdvisor** — injects conversation history from memory.
   - **ToolCallAdvisor** — enables automatic tool invocation by the AI model.
   - **SyncMcpToolCallbackProvider** — provides MCP tool definitions.
   - **SimpleLoggerAdvisor** — logs request/response.
     The agent also receives environment context via `AgentEnvironment.info()` injected into the system prompt, including: working directory, OS platform, timezone, current timestamp, and git repository status.
7. The agent's response is returned as a string.
8. The system renders the response as an HTML agent bubble and pushes it to the WebSocket.
9. Conversation history is persisted (JDBC primary, FileSystem fallback).

#### 2.1.3. Conversational Interaction via Telegram

1. TelegramChannel connects to the Telegram Bot API using long polling.
2. When a message arrives, the system:
   - Filters by `allowedUsername` — only messages from the configured user are processed.
   - Extracts the conversation ID from `chatId` and `messageThreadId`.
   - Passes the message text to the agent via `agent.respondTo(conversationId, message)`.
3. The agent's response is sent back to the same Telegram chat via `telegramClient.execute(SendMessage)`.

#### 2.1.4. Conversational Interaction via Discord

1. DiscordChannel connects to Discord via JDA and listens for message events.
2. When a message arrives:
   - Filters by `allowedUserId` — only messages from the configured user are processed.
   - Accepts messages from private DMs or mentions of the bot.
   - Conversation ID is derived from the Discord channel ID.
   - The message is passed to the agent.
3. The agent's response is sent back to the Discord channel via `lastChannel.sendMessage()`.

#### 2.1.5. Task Creation and Execution

1. The agent (or external caller) creates a task using TaskTool:
   - **Immediate task**: `createTask(name, description, sourceChannelName)` — creates a task and schedules it for immediate execution via JobRunr.
   - **Scheduled task**: `scheduleTask(executionTime, name, description, sourceChannelName)` — schedules a task for a specific date/time.
   - **Recurring task**: `scheduleRecurringTask(cronExpression, name, description)` — registers a cron-based recurring task.
   - **Delete recurring task**: `deleteRecurringTask(name)` — deletes a recurring task and cancels the associated JobRunr job.
   - **List recurring tasks**: `listRecurringTasks()` — returns a list of all recurring tasks.
2. When a task is due, JobRunr invokes `TaskHandler.executeTask(taskId)`:
   - The task status is set to `in_progress`.
   - The agent is called with the task description via `agent.prompt(taskId, description, TaskResult.class)`.
   - The agent processes the task and returns a structured `TaskResult(newStatus, feedback)`.
   - The task is updated with the result and the user is notified via the active channel only if the resulting status is `completed` or `awaiting_human_input`.
3. For recurring tasks, `RecurringTaskHandler` creates a new Task instance from the RecurringTask definition each time the cron fires.

#### 2.1.6. MCP Server Registration at Runtime

1. The agent (or user via agent) calls `McpTool.addStreamableHttpMcpServer(name, url, headers)` or `McpTool.addStdioMcpServer(name, commandWithArgs, env)`.
2. The system validates the server name (letters, numbers, hyphens, underscores only).
3. The new MCP connection is persisted to `application.yaml` under `spring.ai.mcp.client.streamable-http.connections.*`.
4. A `ConfigurationChangedEvent` is published, triggering an application restart to load the new MCP server.
5. After restart, the MCP server's tools become available to the agent via `SyncMcpToolCallbackProvider`.

#### 2.1.8. Agent Tool Auto-Discovery and Extension

The platform supports automatic discovery of agent tools through the `AutoDiscoveredTool<T>` mechanism:
1. Plugin modules expose tools as Spring beans of type `AutoDiscoveredTool<T>`.
2. At startup, `JavaClawConfiguration` collects all `AutoDiscoveredTool` beans and registers their inner tools with the ChatClient.
3. This enables plugins to contribute tools without modifying core configuration.

Additionally, the following tools are registered from the `spring-ai-community` library:
- **FileSystemTools** — file read/write/edit operations within the workspace (planned, not yet fully implemented)
- **SkillsTool** — loading and executing skills from the `{workspace}/skills/` directory (planned, not yet fully implemented)
- **SmartWebFetchTool** — intelligent web content fetching using the ChatClient for summarization (planned, not yet fully implemented)

#### 2.1.7. Conversation Switching (Web UI)

1. The Web UI displays a conversation selector dropdown listing all available conversation IDs.
2. User selects a different conversation.
3. The system sends a `channelChanged` WebSocket message with the new conversation ID.
4. The server loads the conversation history for the selected ID and pushes it as HTML to the client.
5. The input area is re-rendered for the selected conversation.

### 2.2. Alternative and Error Scenarios

#### 2.2.1. <font color="red">**Alt**</font> **Onboarding Not Completed**

1. If `agent.onboarding.completed` is not `true`, the application logs a message at startup directing the user to the onboarding URL.
2. The chat interface is available but operates with a fallback ChatModel (no-op or limited).

#### 2.2.2. <font color="red">**Alt**</font> **AI Provider Unavailable**

1. If the configured AI provider returns an error or times out during `agent.respondTo()`, the error propagates to the channel.
2. For Web UI: the error message is displayed in the chat as an agent bubble.
3. For Telegram/Discord: the error message is sent as a reply.

#### 2.2.3. <font color="red">**Alt**</font> **Task Execution Failure**

1. If the agent fails to process a task (exception during `agent.prompt()`), the task status is restored to `todo` (see section 2.3, rule 14).
2. JobRunr retries the task up to 3 times with built-in backoff.
3. After all retries exhausted, the task enters JobRunr's failed state.

#### 2.2.4. <font color="red">**Alt**</font> **WebSocket Disconnection (Web UI)**

1. When the WebSocket connection is closed (browser tab closed, network issue), `ChatWebSocketHandler.afterConnectionClosed()` clears the session from ChatChannel.
2. Messages sent while the WebSocket is disconnected are queued in an in-memory ConcurrentLinkedQueue for potential delivery.
3. Upon reconnection, the system loads the full conversation history from the persistent memory repository (JDBC or FileSystem), not from the in-memory queue.

#### 2.2.5. <font color="red">**Alt**</font> **Invalid MCP Server Name**

1. If the user attempts to register an MCP server with invalid characters in the name, the system returns an error message describing the naming rules (letters, numbers, hyphens, underscores).
2. The MCP server is NOT registered.

#### 2.2.6. <font color="blue">**Opt**</font> **Brave Web Search**

1. If `agent.browser.brave.api-key` is configured, the BraveWebSearchTool is auto-discovered and registered as an agent tool.
2. The agent MAY invoke web search during conversations or task execution.
3. Default result count: 15 results per query.

#### 2.2.7. <font color="blue">**Opt**</font> **Playwright Browser Automation**

1. If the Playwright plugin is on the classpath and enabled, PlaywrightBrowserTool is registered.
2. Chromium is auto-installed on first use if not present.
3. The agent can navigate URLs, click elements, fill inputs, take screenshots, and extract text.
4. Text extraction is limited to 10,000 characters per call.

#### 2.2.8. <font color="blue">**Opt**</font> **CheckList Tool**

1. The agent MAY use CheckListTool to manage structured checklists for complex multi-step tasks.
2. At most one checklist item MAY be in `in_progress` status at any time.
3. Statuses: `pending` → `in_progress` → `completed`.

### 2.3. Business Rules and Processing Logic

#### Channel Registration and Default Channel

1. **IF** a Channel implementation bean is created (ChatChannel, TelegramChannel, DiscordChannel),
   **THEN** it MUST register itself with the ChannelRegistry.
2. **IF** this is the first channel to register,
   **THEN** it becomes the default channel for notifications (task results, background messages).
3. **IF** the requested channel name is not found in the registry,
   **THEN** the default channel is used as a fallback.

#### ChatChannel Priority

4. **IF** `javaclaw.chat.transport=spring-websocket` (default),
   **THEN** ChatChannel is created with `HIGHEST_PRECEDENCE` order, making it the default channel.

#### Conversation ID Generation

5. **IF** the channel is Web UI,
   **THEN** the primary conversation ID is `"web"`.
6. **IF** the channel is Telegram,
   **THEN** the conversation ID is composed as `"telegram-{chatId}[-{messageThreadId}]"`.
7. **IF** the channel is Discord,
   **THEN** the conversation ID is derived from the Discord channel ID.

#### Configuration Hot-Reload

8. **IF** any property is updated via ConfigurationManager,
   **THEN** a `ConfigurationChangedEvent` is published.
9. **IF** the application receives a `ConfigurationChangedEvent`,
   **THEN** it schedules a restart with a 2-second delay.
10. **IF** multiple configuration changes occur within the 2-second window,
    **THEN** only one restart is triggered.

#### Task Lifecycle

11. **IF** a task is created via `TaskManager.create()`,
    **THEN** the task is saved to the database with status `todo` and scheduled for immediate execution via JobRunr.
12. **IF** TaskHandler picks up a task with status other than `todo`,
    **THEN** an IllegalStateException is thrown, causing JobRunr to retry the task.
13. **IF** the agent returns `TaskResult` with `newStatus = awaiting_human_input`,
    **THEN** the task is saved with that status and the user is notified; automatic re-execution does NOT occur.
14. **IF** an exception occurs during task execution by the agent,
    **THEN** the task status is restored to `todo` before the exception is re-thrown, allowing JobRunr to correctly retry the task.

#### Onboarding Provider Selection

15. **IF** the user selects a provider in Step 2 and then changes it,
    **THEN** all downstream session state (model, apiKey, baseUrl) MUST be cleared.
16. **IF** a provider has a `systemWideToken` (e.g., Anthropic Claude Code OAuth),
    **THEN** the token is auto-detected and the credentials step MAY be pre-filled.

#### Memory Persistence Strategy

17. **IF** JdbcAppendableChatMemoryRepository is available (database configured),
    **THEN** it is used as the primary memory store (`@Primary`).
18. **IF** only FileSystemChatMemoryRepository is available,
    **THEN** conversations are persisted as YAML files in `{workspace}/conversations/`.
19. **IF** `appendAll()` is called on JdbcAppendableChatMemoryRepository,
    **THEN** it reads existing messages, merges with new ones, and saves the complete list (read-merge-write pattern).

#### MCP Header Customization

20. **IF** an MCP Streamable HTTP connection has custom headers defined in configuration,
    **THEN** McpHeaderCustomizer adds those headers to every request to that MCP server.

### 2.4. API

#### WebSocket: `/chat` — Conversational Chat Interface

The primary interaction API uses WebSocket over the `/chat` endpoint.

**Client → Server Messages (JSON):**

Switch conversation:

```json
{
  "type": "channelChanged",
  "conversationId": "web"
}
```

Send user message:

```json
{
  "type": "userMessage",
  "conversationId": "web",
  "message": "Hello, what can you do?"
}
```

**Server → Client Messages (HTML fragments via WebSocket text frames):**

The server pushes HTML fragments that are inserted into the DOM:
- Agent message bubble (contains rendered Markdown)
- User message bubble
- Typing indicator animation
- Conversation selector dropdown
- Chat input area

#### GET /chat — Chat Page

**Description:** Renders the main chat HTML page.

**Response (200 OK):** HTML page with embedded WebSocket connection logic.

**Query parameters:** None.

#### GET /onboarding — Onboarding Redirect

**Description:** Redirects to the first onboarding step.

**Response (302 Found):** Redirect to `/onboarding/welcome`.

#### GET /onboarding/{stepId} — Render Onboarding Step

**Description:** Renders the specified onboarding step page.

**Path parameters:**

| Parameter |  Type  | Required |                                     Description                                      |
|-----------|--------|----------|--------------------------------------------------------------------------------------|
| stepId    | string | yes      | Step identifier: `welcome`, `provider`, `credentials`, `agent-md`, `mcp`, `complete` |

**Response (200 OK):** HTML page for the step. Model attributes include: `currentStep`, `stepNumber`, `totalSteps`, `steps`, `previousUrl`, `nextUrl`, `isOptional`, `stepTemplate`.

#### POST /onboarding/{stepId} — Process Onboarding Step

**Description:** Processes the form submission for the specified step and redirects to the next step.

**Path parameters:** Same as GET.

**Request body (form-encoded):** Depends on the step:
- **provider**: `provider` (string — provider ID)
- **credentials**: `apiKey` (string), `baseUrl` (string, optional)
- **agent-md**: `agentMd` (string — agent system prompt content)
- **mcp**: MCP server configuration fields
- **complete**: No parameters (triggers finalization)

**Response (302 Found):** Redirect to the next step, or to `/chat` on completion.

**Response (200 OK with error):** If step validation fails, re-renders the step page with an error message.

### 2.5. Integrations

#### AI Providers (outgoing, HTTP)

|   Provider   |       Protocol        |        Configuration Key         |   Default Model   |                                    Description                                    |
|--------------|-----------------------|----------------------------------|-------------------|-----------------------------------------------------------------------------------|
| Anthropic    | HTTPS (Anthropic API) | `spring.ai.anthropic.api-key`    | claude-sonnet-4-6 | Claude models via Anthropic API. Supports Claude Code OAuth token auto-detection. |
| OpenAI       | HTTPS (OpenAI API)    | `spring.ai.openai.api-key`       | —                 | GPT models via OpenAI-compatible API                                              |
| Google GenAI | HTTPS (Google AI API) | `spring.ai.google.genai.api-key` | —                 | Google Generative AI models                                                       |
| Ollama       | HTTP (local)          | `spring.ai.ollama.*`             | —                 | Local LLM inference via Ollama                                                    |

- **Timeout**: Governed by Spring AI and provider-specific configuration.
- **Retry**: Delegated to Spring AI ChatClient internals.
- **On unavailability**: Error propagated to the calling channel as a text message.

#### MCP Servers (outgoing, HTTP/Stdio)

- **Protocol**: Streamable HTTP or Stdio (subprocess)
- **Configuration**: `spring.ai.mcp.client.streamable-http.connections.*`
- **Headers**: Custom headers per connection via McpHeaderCustomizer
- **Registration**: Runtime via McpTool or static via configuration
- **On unavailability**: Tool call fails, error returned to the agent for handling

#### Telegram Bot API (outgoing, HTTPS)

- **Protocol**: HTTPS long polling
- **Library**: TelegramBots 9.4.0
- **Configuration**: `telegram.bot.token`, `telegram.bot.allowed-username`
- **On unavailability**: Long polling reconnects automatically

#### Discord API (outgoing, WebSocket)

- **Protocol**: WebSocket (JDA gateway)
- **Library**: JDA 6.1.1
- **Configuration**: `discord.bot.token`, `discord.bot.allowed-user-id`
- **On unavailability**: JDA handles reconnection internally

#### PostgreSQL Database (read/write)

- **Protocol**: JDBC
- **Driver**: PostgreSQL
- **Tables**: `tasks`, `recurring_tasks`, `spring_ai_chat_memory` (Spring AI managed), Flyway migrations, JobRunr tables
- **Connection pool**: HikariCP (Spring Boot default)
- **Usage**: Task persistence, conversation memory (primary), JobRunr job storage

#### JobRunr Dashboard (outgoing, HTTP)

- **Protocol**: HTTP
- **Default port**: 8081
- **Description**: Built-in dashboard for monitoring scheduled/recurring tasks
- **Configuration**: `jobrunr.dashboard.port`

#### Brave Web Search API (outgoing, HTTPS)

- **Protocol**: HTTPS
- **Configuration**: `agent.browser.brave.api-key`
- **Conditional**: Only active when API key is configured
- **Results per query**: 15

### 2.6. Data Models

#### Task (PostgreSQL: `tasks`)

|        Field        |    Type     | Required |                               Description                               |
|---------------------|-------------|----------|-------------------------------------------------------------------------|
| id                  | VARCHAR(36) | PK       | Unique task identifier, auto-generated                                  |
| name                | string      | yes      | Human-readable task name                                                |
| description         | string      | no       | Detailed task description for the agent                                 |
| status              | enum        | yes      | Task status: `todo`, `in_progress`, `completed`, `awaiting_human_input` |
| feedback            | string      | no       | Agent's execution result or feedback                                    |
| source_channel_name | string      | no       | Channel that initiated the task                                         |
| created_at          | timestamp   | yes      | Creation timestamp                                                      |
| updated_at          | timestamp   | yes      | Last update timestamp                                                   |

#### RecurringTask (PostgreSQL: `recurring_tasks`)

|      Field      |     Type      | Required |           Description            |
|-----------------|---------------|----------|----------------------------------|
| id              | string (UUID) | PK       | Unique recurring task identifier |
| name            | string        | yes      | Task name                        |
| description     | string        | no       | Task description template        |
| cron_expression | string        | yes      | Cron schedule expression         |
| job_id          | string        | no       | JobRunr recurring job ID         |
| created_at      | timestamp     | yes      | Creation timestamp               |

#### Conversation Memory (YAML file format — FileSystem fallback)

```yaml
---
createdAt: "2026-03-21T10:00:00Z"
updatedAt: "2026-03-21T10:05:30Z"
---
- user: |
    User message text
- assistant: |
    Agent response text
- system: |
    System message text
```

|    Field     |         Type         | Required |                          Description                           |
|--------------|----------------------|----------|----------------------------------------------------------------|
| createdAt    | ISO 8601 timestamp   | yes      | Conversation creation time (frontmatter)                       |
| updatedAt    | ISO 8601 timestamp   | yes      | Last message time (frontmatter)                                |
| body entries | list of role:content | yes      | Ordered list of messages. Roles: `user`, `assistant`, `system` |

File path pattern: `{workspace}/conversations/chat-{conversationId}.yaml`

#### Conversation Memory (PostgreSQL: `spring_ai_chat_memory`)

Schema is managed by project Flyway migrations (V2__init_chat_memory.sql, V3__allow_null_content_in_chat_memory.sql). Data access is provided by Spring AI's `JdbcChatMemoryRepository`.

#### MCP Connection Configuration

|  Field   |        Type         |  Required   |                       Description                        |
|----------|---------------------|-------------|----------------------------------------------------------|
| name     | string              | yes         | Connection name (letters, numbers, hyphens, underscores) |
| url      | string              | yes (HTTP)  | MCP server URL                                           |
| endpoint | string              | no          | Custom endpoint path                                     |
| headers  | map<string, string> | no          | Custom HTTP headers for the connection                   |
| command  | string              | yes (Stdio) | Command with arguments for Stdio transport               |
| env      | map<string, string> | no          | Environment variables for Stdio subprocess               |

#### YamlDocument (internal model)

|    Field    |        Type         | Required |                 Description                 |
|-------------|---------------------|----------|---------------------------------------------|
| frontmatter | map<string, string> | no       | Key-value pairs from YAML frontmatter block |
| body        | string              | no       | Content after the frontmatter separator     |

#### CheckList (tool model)

|       Field        |         Type          | Required |                  Description                  |
|--------------------|-----------------------|----------|-----------------------------------------------|
| checkList          | list of CheckListItem | yes      | Ordered list of checklist items               |
| items[].content    | string                | yes      | Description of the checklist item             |
| items[].status     | enum                  | yes      | Status: `pending`, `in_progress`, `completed` |
| items[].activeForm | string                | no       | Additional context for the active item        |

Constraint: At most one item MAY have status `in_progress` at any given time.

### 2.7. Configuration

|                      Parameter                       | Required |  Type  |      Default      |                            Description                             |
|------------------------------------------------------|----------|--------|-------------------|--------------------------------------------------------------------|
| `agent.workspace`                                    | no       | string | `./workspace`     | Workspace directory for conversations, agent files, skills         |
| `agent.onboarding.completed`                         | no       | bool   | false             | Whether onboarding has been completed                              |
| `agent.browser.brave.api-key`                        | no       | string | —                 | Brave Web Search API key. Enables web search tool when set         |
| `spring.ai.model.chat`                               | no       | string | —                 | Selected AI model identifier                                       |
| `spring.ai.anthropic.api-key`                        | no       | string | —                 | Anthropic API key                                                  |
| `spring.ai.anthropic.chat.options.model`             | no       | string | claude-sonnet-4-6 | Anthropic model name                                               |
| `spring.ai.openai.api-key`                           | no       | string | —                 | OpenAI API key                                                     |
| `spring.ai.openai.chat.options.model`                | no       | string | —                 | OpenAI model name                                                  |
| `spring.ai.google.genai.api-key`                     | no       | string | —                 | Google GenAI API key                                               |
| `spring.ai.ollama.*`                                 | no       | —      | —                 | Ollama provider configuration                                      |
| `spring.ai.mcp.client.streamable-http.connections.*` | no       | map    | —                 | MCP Streamable HTTP server connections                             |
| `spring.datasource.url`                              | yes      | string | —                 | PostgreSQL JDBC URL                                                |
| `spring.datasource.username`                         | yes      | string | —                 | Database username                                                  |
| `spring.datasource.password`                         | yes      | string | —                 | Database password                                                  |
| `javaclaw.chat.transport`                            | no       | string | spring-websocket  | Chat transport type. Enables Web UI when set to `spring-websocket` |
| `jobrunr.dashboard.port`                             | no       | int    | 8081              | JobRunr dashboard HTTP port                                        |
| `telegram.bot.token`                                 | no       | string | —                 | Telegram bot token. Enables Telegram channel when set              |
| `telegram.bot.allowed-username`                      | no       | string | —                 | Telegram username filter                                           |
| `discord.bot.token`                                  | no       | string | —                 | Discord bot token. Enables Discord channel when set                |
| `discord.bot.allowed-user-id`                        | no       | string | —                 | Discord user ID filter                                             |

**Configuration files (precedence order):**
1. `application.private.yaml` — generated during onboarding, contains secrets
2. `application.yaml` — default configuration, checked into VCS
3. `AGENT.private.md` — custom agent system prompt (takes precedence)
4. `AGENT.md` — default agent system prompt
5. `INFO.md` — additional context injected into the agent

### 2.8. Logging

| Level |                   Event                   |                        Message Format                        |
|-------|-------------------------------------------|--------------------------------------------------------------|
| INFO  | Application started successfully          | Startup log with onboarding URL if not completed             |
| INFO  | Configuration changed, scheduling restart | "Configuration changed, restarting application in 2 seconds" |
| INFO  | Task created                              | Task ID, name, scheduled execution time                      |
| INFO  | Task execution started                    | "Executing task: {taskId}"                                   |
| INFO  | Task execution completed                  | Task ID, resulting status, feedback summary                  |
| INFO  | Recurring task triggered                  | Recurring task ID, created task ID                           |
| INFO  | MCP server registered                     | Server name, type (HTTP/Stdio)                               |
| INFO  | Channel registered                        | Channel name, type                                           |
| INFO  | Onboarding step processed                 | Step ID, success/error                                       |
| WARN  | Task execution skipped (non-todo status)  | Task ID, current status                                      |
| WARN  | Telegram message from unauthorized user   | Username attempted                                           |
| WARN  | Discord message from unauthorized user    | User ID attempted                                            |
| ERROR | AI provider call failed                   | Provider name, error message                                 |
| ERROR | Task execution failed                     | Task ID, exception details                                   |
| ERROR | Configuration write failed                | IOException details                                          |
| ERROR | MCP server connection failed              | Server name, error                                           |
| DEBUG | WebSocket connection established          | Session ID                                                   |
| DEBUG | WebSocket connection closed               | Session ID, close status                                     |
| DEBUG | Conversation history loaded               | Conversation ID, message count                               |
| DEBUG | Agent prompt with tools                   | Tool names registered                                        |

### 2.9. Input Validation

|             Field (context)              | Validation Type |                               Rule                               |                           Error Message                            |
|------------------------------------------|-----------------|------------------------------------------------------------------|--------------------------------------------------------------------|
| MCP server name (McpTool)                | regex           | `^[a-zA-Z0-9_-]+$` — letters, numbers, hyphens, underscores only | "Name must contain only letters, numbers, hyphens and underscores" |
| MCP server URL (McpTool)                 | format          | Valid URL string                                                 | Tool-level validation                                              |
| Onboarding provider ID (S2_ProviderStep) | enum            | Must match a registered AgentOnboardingProvider ID               | Step re-rendered with error                                        |
| Onboarding API key (S3_CredentialsStep)  | length          | Non-empty string when provider requires API key                  | Step re-rendered with error                                        |
| Task name (TaskTool)                     | length          | Non-empty string                                                 | Tool-level validation                                              |
| Task cron expression (TaskTool)          | format          | Valid cron expression accepted by JobRunr                        | JobRunr validation error                                           |
| CheckList items (CheckListTool)          | business        | At most 1 item in `in_progress` status                           | Tool-level validation error                                        |
| WebSocket message (ChatWebSocketHandler) | format          | Valid JSON with `type` field                                     | Message silently dropped                                           |

### 2.10. Error Handling

**WebSocket /chat:**

|          Error Scenario          |                Behavior                 |                   User Notification                   |
|----------------------------------|-----------------------------------------|-------------------------------------------------------|
| AI provider timeout              | Exception caught in ChatChannel         | Error text displayed as agent bubble                  |
| AI provider returns error        | Exception caught in ChatChannel         | Error text displayed as agent bubble                  |
| Invalid WebSocket message format | JSON parse exception caught             | Message silently dropped, no response                 |
| Tool call failure (MCP/other)    | Spring AI ToolCallAdvisor handles error | Agent receives error and may retry or explain to user |

**POST /onboarding/{stepId}:**

| HTTP Code |          Scenario           |                     Behavior                      |
|-----------|-----------------------------|---------------------------------------------------|
| 200       | Validation error on step    | Step page re-rendered with error message in model |
| 302       | Step processed successfully | Redirect to next step                             |
| 302       | Unknown stepId              | Redirect to the first onboarding step             |

**Task execution (JobRunr):**

|            Error Scenario             |                        Behavior                        |
|---------------------------------------|--------------------------------------------------------|
| Agent prompt throws exception         | JobRunr catches, retries up to 3 times                 |
| All retries exhausted                 | Task enters JobRunr failed state, visible in dashboard |
| Database unavailable during task save | Exception propagates, JobRunr retries                  |

### 2.11. Headers and Metadata

|    Transport    |      Name      | Direction | Required | Format |                                                Description                                                 |
|-----------------|----------------|-----------|----------|--------|------------------------------------------------------------------------------------------------------------|
| HTTP (MCP)      | Custom headers | request   | optional | string | Per-connection custom headers defined in `spring.ai.mcp.client.streamable-http.connections.{name}.headers` |
| WebSocket       | —              | —         | —        | —      | No custom headers; messages are JSON text frames                                                           |
| HTTP (Telegram) | —              | —         | —        | —      | Managed by TelegramBots library                                                                            |
| HTTP (Discord)  | —              | —         | —        | —      | Managed by JDA library                                                                                     |

---

## 3. Acceptance Criteria Recommendations

| #  |                                      WHEN                                       |                                                               THEN                                                                |
|----|---------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------|
| 1  | User navigates to the application for the first time (onboarding not completed) | The system redirects to `/onboarding/welcome` and displays the welcome page                                                       |
| 2  | User completes all 6 onboarding steps with valid Anthropic API key              | Configuration is saved to `application.private.yaml`, `agent.onboarding.completed=true`, application restarts, chat is accessible |
| 3  | User sends a message via Web UI WebSocket                                       | The agent responds, both messages are persisted in conversation memory, response is rendered as HTML bubble                       |
| 4  | User switches conversation in Web UI                                            | History for the selected conversation is loaded and displayed, input area is re-rendered                                          |
| 5  | WebSocket connection drops and reconnects                                       | Full conversation history is re-sent to the client upon reconnection                                                              |
| 6  | Telegram message arrives from allowed username                                  | Agent processes and responds in the same Telegram chat                                                                            |
| 7  | Telegram message arrives from unauthorized username                             | Message is ignored, no response sent                                                                                              |
| 8  | Discord message arrives from allowed user ID                                    | Agent processes and responds in the same Discord channel                                                                          |
| 9  | Agent creates an immediate task via TaskTool                                    | Task is saved with status `todo`, JobRunr picks it up, agent executes it, result is saved, user is notified via active channel    |
| 10 | Agent creates a recurring task with cron `0 0 9 * * *`                          | RecurringTask is saved, JobRunr registers the recurring job, a new Task is created each day at 09:00                              |
| 11 | Task execution fails 3 times                                                    | Task enters JobRunr failed state, visible in JobRunr dashboard at configured port                                                 |
| 12 | Agent registers a new MCP server via McpTool with valid name and URL            | Server is persisted to configuration, application restarts, new MCP tools are available to the agent                              |
| 13 | Agent attempts to register MCP server with invalid name (special characters)    | Registration is rejected with error message, configuration is NOT modified                                                        |
| 14 | Brave API key is configured                                                     | BraveWebSearchTool is auto-discovered and available to the agent                                                                  |
| 15 | Brave API key is NOT configured                                                 | BraveWebSearchTool is not registered, no errors on startup                                                                        |
| 16 | Playwright plugin is active and agent navigates to a URL                        | Page content is returned (max 10K chars), screenshot can be taken                                                                 |
| 17 | User changes provider during onboarding (Step 2)                                | Downstream session state (apiKey, model, baseUrl) is cleared                                                                      |
| 18 | ConfigurationManager updates a property                                         | `ConfigurationChangedEvent` is published, application restarts within ~2 seconds                                                  |
| 19 | Multiple configuration changes within 2 seconds                                 | Only one restart occurs                                                                                                           |
| 20 | PostgreSQL is available at startup                                              | JDBC memory repository is used as primary (`@Primary`) for conversation persistence                                               |

---

## 4. Non-Functional Requirements

### 4.1. Security

1. Secrets (API keys, bot tokens) MUST NOT be stored in source-controlled configuration files. They MUST be stored in `application.private.yaml` (gitignored) or injected via environment variables.
2. Telegram channel MUST filter messages by `allowedUsername` — messages from other users MUST be ignored.
3. Discord channel MUST filter messages by `allowedUserId` — messages from other users MUST be ignored.
4. Confidential information (API keys, tokens) MUST be masked in logs.
5. Authentication and authorization for multi-user access is not yet implemented. It is planned as a future enhancement and SHOULD be added before production deployment in shared environments.
6. MCP server connections with custom headers MAY carry sensitive tokens — these MUST be persisted securely (not in VCS-tracked files).

### 4.2. Performance

|            Parameter             |               Value               |                        Description                         |
|----------------------------------|-----------------------------------|------------------------------------------------------------|
| WebSocket message queue          | Unbounded (ConcurrentLinkedQueue) | In-memory queue for messages when WS is disconnected       |
| Playwright text extraction limit | 10,000 chars                      | Maximum text extracted per tool call                       |
| Brave search results             | 15                                | Results per web search query                               |
| JobRunr task retries             | 3                                 | Maximum retry attempts per task                            |
| Configuration restart delay      | 2,000 ms                          | Delay before application restart after config change       |
| Conversation memory              | MessageWindowChatMemory           | Windowed memory — retains last N messages per conversation |

### 4.3. Reliability

1. The service MUST support graceful shutdown (Spring Boot default).
2. Conversation memory MUST be persisted to durable storage (PostgreSQL primary, filesystem fallback).
3. Task state MUST be persisted to PostgreSQL via Spring Data JDBC — no in-memory-only tasks.
4. JobRunr MUST use a persistent storage backend (PostgreSQL) to survive restarts.
5. The WebSocket ChatChannel MUST queue outgoing messages when the client is disconnected.
6. The configuration hot-reload mechanism MUST not lose in-flight requests — the 2-second delay allows current requests to complete.
7. For multi-instance deployments, MCP configuration synchronization through database and Spring Modulith events is planned but not yet implemented. Currently, each instance manages its own configuration independently.

### 4.4. Monitoring

#### 4.4.1. Standard Metrics

The service provides standard Spring Boot Actuator metrics:
- `http_server_requests_seconds` — HTTP request duration
- `jvm_memory_used_bytes` — JVM memory usage
- `hikaricp_connections_active` — active database connections

Health-check endpoints:
- `/actuator/health` — overall status
- `/actuator/health/readiness` — readiness (PostgreSQL connectivity)
- `/actuator/health/liveness` — liveness

#### 4.4.2. JobRunr Dashboard

- **URL**: `http://{host}:{jobrunr.dashboard.port}` (default 8081)
- **Description**: Built-in web dashboard for monitoring tasks — scheduled, recurring, succeeded, failed
- **Metrics**: Job count by state, processing time, queue depth

#### 4.4.3. Custom Business Metrics

Not yet defined. SHOULD be added as the platform matures, covering:
- Messages processed per channel (counter)
- Agent response latency (timer)
- Task completion rate (counter by status)
- MCP tool call success/failure rate (counter)

### 4.5. Audit

Not defined at the current stage. Will be added when authentication/authorization is implemented. SHOULD cover:
- User authentication events
- Configuration changes (who changed what, when)
- Task creation and execution audit trail
- MCP server registration/removal events
