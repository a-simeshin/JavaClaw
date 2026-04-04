# Specification: javaclaw-module-restructure

Specification version — 1.0

* [1. General Information](#1-general-information)
* [2. Functional Requirements](#2-functional-requirements)
  * [2.1. Primary Use Case](#21-primary-use-case)
  * [2.2. Alternative and Error Scenarios](#22-alternative-and-error-scenarios)
  * [2.3. Business Rules and Processing Logic](#23-business-rules-and-processing-logic)
  * [2.4. Target Module Structure](#24-target-module-structure)
  * [2.5. Current-to-Target Structure Mapping](#25-current-to-target-structure-mapping)
  * [2.6. Data Models (Modules)](#26-data-models-modules)
  * [2.7. Configuration Requirements](#27-configuration-requirements)
  * [2.8. Logging](#28-logging)
  * [2.9. Validation](#29-validation)
  * [2.10. Error Handling](#210-error-handling)
* [3. Acceptance Criteria Recommendations](#3-acceptance-criteria-recommendations)
* [4. Non-Functional Requirements](#4-non-functional-requirements)
  * [4.1. Security](#41-security)
  * [4.2. Performance](#42-performance)
  * [4.3. Reliability](#43-reliability)
  * [4.4. Monitoring](#44-monitoring)

---

## 1. General Information

**javaclaw-module-restructure** is an infrastructure refactoring of the JavaClaw platform aimed at bringing the module architecture in line with an atomic separation-of-concerns principle.

The current project structure violates the Single Responsibility Principle (SRP) at the Maven module level:
- The `base/` module combines the platform core with Spring AI hacks in a foreign namespace (`org.springframework.ai.chat.*`)
- The `app/` module combines the assembler role (main + yaml) with business logic (web chat, onboarding wizard, htmx/Pebble rendering)
- The `plugins/` directory mixes modules of fundamentally different natures: user communication channels (Discord, Telegram) and agent tools (Brave, Playwright)

The refactoring establishes clear architectural layers:
- **core** — platform core (domain model, interfaces, agent engine)
- **api** — input API surfaces (chat, admin, future a2a and mcp-server)
- **channels** — communication channel implementations (Discord, Telegram)
- **providers** — LLM provider integrations (Anthropic, OpenAI, Ollama, Google)
- **app** — assembler (entry point, configuration, migrations)

The refactoring includes removal of legacy components scheduled for deletion per roadmap Phase 0.2: onboarding wizard, htmx/Pebble templating, Brave and Playwright plugins (functionality migrates to external MCP servers).

**Consumers:** JavaClaw platform developers, CI/CD pipeline, future integrations.

**Architectural context:** affects the ENTIRE JavaClaw codebase — this is a structural refactoring that adds no functionality but provides the foundation for further development per roadmap (Phase 1–6).

---

## 2. Functional Requirements

### 2.1. Primary Use Case

#### 2.1.1. Rename base/ to javaclaw-core/

1. The `base/` directory is renamed to `javaclaw-core/`.
2. In `javaclaw-core/pom.xml`, the artifactId is changed from `javaclaw-base` to `javaclaw-core`.
3. All pom.xml files referencing `javaclaw-base` are updated to `javaclaw-core`.
4. The root `pom.xml` is updated: in the `<modules>` section, the `base` entry is replaced with `javaclaw-core`.
5. Classes in the `org.springframework.ai.chat.*` package are removed from `javaclaw-core/`:
   - `org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor`
   - `org.springframework.ai.chat.memory.AppendableChatMemoryRepository`
   - `org.springframework.ai.chat.memory.MessageWindowChatMemory`
   - Test: `org.springframework.ai.chat.memory.MessageWindowChatMemoryTest`
6. The `ai.javaclaw.onboarding` package with all subpackages is removed from `javaclaw-core/` (interfaces `AgentOnboardingProvider`, `OnboardingProvider`, `AgentOnboardingProviders`).
7. Flyway migrations (`javaclaw-core/src/main/resources/db/migration/`) are moved to `javaclaw-app/src/main/resources/db/migration/`. Migrations MUST reside in the assembler module, not in core.
8. All modules using removed classes MUST be adapted (see section 2.3, rule 5).

#### 2.1.2. Create javaclaw-api/javaclaw-api-chat/ module

1. The `javaclaw-api/javaclaw-api-chat/` directory is created with Maven structure (`src/main/java`, `src/test/java`, `pom.xml`). The `javaclaw-api/` aggregator POM is also created.
2. artifactId: `javaclaw-api-chat`.
3. The following classes are moved from `javaclaw-app/` (with package updates):
   - `ai.javaclaw.chat.ChatChannel` → `ai.javaclaw.api.chat.ChatChannel`
   - `ai.javaclaw.chat.api.ChatController` → `ai.javaclaw.api.chat.ChatController`
   - `ai.javaclaw.chat.ws.ChatWebSocketHandler` → `ai.javaclaw.api.chat.ws.ChatWebSocketHandler`
   - `ai.javaclaw.chat.ws.WebSocketConfig` → `ai.javaclaw.api.chat.ws.WebSocketConfig`
   - `ai.javaclaw.chat.ChatHtml` → `ai.javaclaw.api.chat.ChatHtml` (add `@Deprecated(forRemoval = true)`)
   - `ai.javaclaw.chat.Htmx` → `ai.javaclaw.api.chat.Htmx` (add `@Deprecated(forRemoval = true)`)
4. Tests related to the moved classes are relocated to `javaclaw-api/javaclaw-api-chat/src/test/java/`.
5. The `javaclaw-api-chat` dependency is added to `javaclaw-app/pom.xml`.
6. The root `pom.xml` is updated: `javaclaw-api` is added to the `<modules>` section (aggregator handles child modules).

#### 2.1.3. Create javaclaw-api/javaclaw-api-admin/ module

1. The `javaclaw-api/javaclaw-api-admin/` directory is created with Maven structure.
2. artifactId: `javaclaw-api-admin`.
3. The module is created as a scaffold — contains only `pom.xml` with a dependency on `javaclaw-core` and `spring-boot-starter-webmvc`.
4. The module is declared in the `javaclaw-api/pom.xml` aggregator `<modules>` section.

#### 2.1.4. Move channels from plugins/ to javaclaw-channel/

1. The `plugins/discord/` directory is moved to `javaclaw-channel/javaclaw-channel-discord/`.
2. The `plugins/telegram/` directory is moved to `javaclaw-channel/javaclaw-channel-telegram/`.
3. The `javaclaw-channel/` aggregator POM is created with child modules.
4. In each child `pom.xml`, the dependency on `javaclaw-base` is replaced with `javaclaw-core`.
5. artifactIds are renamed: `javaclaw-plugin-discord` → `javaclaw-channel-discord`, `javaclaw-plugin-telegram` → `javaclaw-channel-telegram`.
6. `*OnboardingProvider` classes and their tests are removed from each channel (they implement the removed `AgentOnboardingProvider` interface):
   - `javaclaw-channel/javaclaw-channel-discord/`: remove `DiscordOnboardingProvider`, `DiscordOnboardingProviderTest`
   - `javaclaw-channel/javaclaw-channel-telegram/`: remove `TelegramOnboardingProvider`
7. Autoconfiguration (`*ChannelAutoConfiguration`) is updated: bean definitions for `*OnboardingProvider` are removed.
8. The root `pom.xml` is updated: `plugins/discord` and `plugins/telegram` entries are replaced with `javaclaw-channel` (aggregator handles child modules).

#### 2.1.5. Move providers to javaclaw-provider/

1. Providers are moved under the `javaclaw-provider/` aggregator directory (e.g. `providers/anthropic/` → `javaclaw-provider/javaclaw-provider-anthropic/`).
2. The `javaclaw-provider/` aggregator POM is created with child modules.
3. Dependencies are updated from `javaclaw-base` to `javaclaw-core` in each child module.
4. In each provider module, code related to `AgentOnboardingProvider` (removed from core) is deleted.
5. **For google, ollama, openai modules:** after removing `*OnboardingProvider`, these modules become empty (contain only pom.xml with a dependency on the Spring AI starter). Modules SHOULD be kept as thin wrappers — they provide a uniform structure and a place for future customization (fallback chains, token extractors, custom model options). The pom.xml MUST retain the dependency on the corresponding `spring-ai-starter-model-*`.
6. **For the anthropic module:** after removing `AnthropicAgentOnboardingProvider`, the module still contains `AnthropicClaudeCodeBackend`, `AnthropicClaudeCodeOAuthTokenExtractor`, `AnthropticClaudeCodeConfiguration` — the module is NOT empty.
7. The root `pom.xml` is updated: `providers/*` entries are replaced with `javaclaw-provider` (aggregator handles child modules).

#### 2.1.6. Clean up the app/ module

1. The following classes and packages are removed from `app/`:
   - Package `ai.javaclaw.onboarding` (OnboardingController, S1–S6 step classes)
   - Onboarding Pebble templates from `src/main/resources/templates/onboarding/`
2. `ChatHtml` and `Htmx` are NOT deleted — they are moved to `javaclaw-api-chat` with `@Deprecated(forRemoval = true)` annotation (Javadoc: "Scheduled for removal — will be replaced when migrating from Pebble to REST/SPA"). They are actively used by `ChatChannel` and `ChatWebSocketHandler` (~20+ references); removing them would break runtime, violating rule 2.10.
3. Pebble templates `chat.html.peb` and `base.html.peb` are kept in `javaclaw-app/src/main/resources/templates/` with `{# @Deprecated: scheduled for removal when migrating from Pebble to REST/SPA #}` comment at top. The `pebble-spring-boot-starter` dependency is kept in `javaclaw-app/pom.xml`. Full Pebble removal is deferred to a separate future task.
4. What remains in `javaclaw-app/`:
   - `JavaClawApplication` (main class)
   - `application.yaml` (and profile yaml files)
   - Flyway migrations (`src/main/resources/db/migration/`)
   - `IndexController` — MUST be reworked: remove redirect to `/onboarding/` (deleted), keep redirect to `/chat` or root page
   - `chat.html.peb` and `base.html.peb` templates (deprecated, see above)
5. Flyway migrations from `javaclaw-core/src/main/resources/db/migration/` are moved to `javaclaw-app/src/main/resources/db/migration/` (if not done in step 2.1.1).
6. `javaclaw-app/pom.xml` dependencies are updated: instead of direct references to `plugins/*` — references to `javaclaw-channel-*` and `javaclaw-api-*`. The `pebble-spring-boot-starter` dependency is kept (see item 3). Dependencies `spring-boot-devtools` (runtime), test dependencies (`testcontainers`, `awaitility`, `playwright`) are preserved without changes.

#### 2.1.7. Remove legacy plugins

1. The `plugins/brave/` directory is deleted entirely.
2. The `plugins/playwright/` directory is deleted entirely.
3. Dependencies on `javaclaw-plugin-brave` and `javaclaw-plugin-playwright` are removed from `javaclaw-app/pom.xml`.
4. **IF** after removing brave and playwright the `plugins/` directory is empty, **THEN** it is deleted.
5. The root `pom.xml` is updated: `plugins/brave` and `plugins/playwright` entries are removed from the `<modules>` section.

#### 2.1.8. Verification

1. `mvn clean compile` is executed — all modules MUST compile successfully.
2. `mvn test` is executed — all existing tests MUST pass (except tests of removed components).
3. The application MUST start successfully via `mvn spring-boot:run -pl javaclaw-app`.
4. Spring Boot autoconfiguration MUST correctly discover channels and providers.

### 2.2. Alternative and Error Scenarios

#### 2.2.1. <font color="red">**Alt**</font> Circular dependency when extracting api/chat/

1. **IF** a circular dependency with `javaclaw-core` occurs when compiling `javaclaw-api-chat` (e.g., ChatChannel depends on Agent, and Agent somehow references ChatChannel),
   **THEN** the Channel interface from `javaclaw-core` MUST remain abstract, and the ChatChannel implementation in `javaclaw-api-chat` MUST depend only on `javaclaw-core` interfaces, not on concrete implementations.
2. Dependency direction: `javaclaw-api-chat → javaclaw-core` (one-way only).

#### 2.2.2. <font color="red">**Alt**</font> Spring AI hacks are used outside core

1. **IF** classes from `org.springframework.ai.chat.*` (being removed from core) are imported in other modules (api/chat, channels, providers),
   **THEN** before removal it is NECESSARY to:
   - Find all dependent modules via import search
   - Create wrappers in the `ai.javaclaw.ai.*` namespace in javaclaw-core
   - Update imports in dependent modules
   - Only then remove the original hacks

#### 2.2.3. <font color="red">**Alt**</font> Onboarding dependencies in providers

1. Each provider module contains a `*OnboardingProvider` class implementing the removed `AgentOnboardingProvider` interface.
2. **IF** `AgentOnboardingProvider` is removed from core, **THEN** in each provider module it is NECESSARY to:
   - Remove the `*OnboardingProvider` class
   - Remove `*OnboardingProvider` from autoconfiguration
   - Update `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
3. Provider functionality (providing ChatModel via Spring AI) MUST be preserved.

#### 2.2.4. <font color="red">**Alt**</font> E2E tests in app/ depend on removed components

1. **IF** E2E tests in `javaclaw-app/src/test/` reference onboarding, htmx, or removed plugins,
   **THEN** those tests MUST be removed or adapted.
2. Tests related to chat/WebSocket are moved to `javaclaw-api/javaclaw-api-chat/src/test/`.

### 2.3. Business Rules and Processing Logic

#### Module Separation Rules

1. **IF** a class defines an interface or abstraction used by multiple modules,
   **THEN** it MUST reside in `javaclaw-core`.

2. **IF** a class implements a specific API endpoint (REST controller, WebSocket handler),
   **THEN** it MUST reside in the corresponding `javaclaw-api/javaclaw-api-*` module.

3. **IF** a class implements the `Channel` interface for a specific messenger/platform,
   **THEN** it MUST reside in `javaclaw-channel/javaclaw-channel-<platform>/`.

4. **IF** a class provides integration with a specific LLM provider,
   **THEN** it MUST reside in `javaclaw-provider/javaclaw-provider-<provider>/`.

5. **IF** a class being removed from core is used in other modules,
   **THEN** a replacement MUST be created in the project's own namespace FIRST, THEN the original is removed.
   Removal without replacement is PROHIBITED.

6. **IF** the `javaclaw-app/` module contains business logic (not main, not yaml, not Flyway),
   **THEN** that logic MUST be extracted to the appropriate module or removed.

#### Inter-Module Dependency Rules

7. Dependencies MUST be unidirectional:
   - `javaclaw-app → javaclaw-api-*, javaclaw-channel-*, javaclaw-provider-*` — assembler depends on all
   - `javaclaw-api-* → javaclaw-core` — API depends on core
   - `javaclaw-channel-* → javaclaw-core` — channels depend on core
   - `javaclaw-provider-* → javaclaw-core` — providers depend on core
   - `javaclaw-core` MUST NOT depend on any other project module
8. Modules at the same layer MUST NOT depend on each other:
   - `javaclaw-api-chat` MUST NOT depend on `javaclaw-api-admin`
   - `javaclaw-channel-discord` MUST NOT depend on `javaclaw-channel-telegram`
   - `javaclaw-provider-anthropic` MUST NOT depend on `javaclaw-provider-openai`

#### Legacy Component Removal Rules

9. **IF** a component is marked for removal (brave, playwright, onboarding, htmx),
   **THEN** the following are removed: source code, tests, dependencies in pom.xml, entries in root pom.xml, templates, static resources.

10. **IF** a removed component had autoconfiguration (`META-INF/spring/*.imports`),
    **THEN** the entry in the `.imports` file MUST be removed.

### 2.4. Target Module Structure

```
javaclaw/
├── pom.xml                                        (parent POM, modules: javaclaw-core, javaclaw-api, javaclaw-channel, javaclaw-provider, javaclaw-app)
├── javaclaw-core/                                 (javaclaw-core)
│   ├── pom.xml
│   └── src/main/java/ai/javaclaw/
│       ├── agent/                                 (Agent, DefaultAgent, memory/)
│       ├── ai/                                    (Spring AI wrappers: advisor/, memory/)
│       ├── channels/                              (Channel, ChannelRegistry, events)
│       ├── configuration/                         (ConfigurationManager)
│       ├── files/                                 (YamlParser, YamlDocument)
│       ├── mcp/                                   (McpConnectionsProperties, McpHeaderCustomizer)
│       ├── providers/                             (AgentProvider)
│       ├── tasks/                                 (Task, RecurringTask, TaskManager, repositories)
│       ├── tools/                                 (McpTool, TaskTool, CheckListTool, AutoDiscoveredTool)
│       └── JavaClawConfiguration
├── javaclaw-api/                                  (aggregator POM)
│   ├── pom.xml
│   ├── javaclaw-api-chat/                         (javaclaw-api-chat)
│   │   ├── pom.xml
│   │   └── src/main/java/ai/javaclaw/api/chat/
│   │       ├── ChatChannel
│   │       ├── ChatController
│   │       ├── ChatHtml                           (@Deprecated, moved from app)
│   │       ├── Htmx                               (@Deprecated, moved from app)
│   │       └── ws/
│   │           ├── ChatWebSocketHandler
│   │           └── WebSocketConfig
│   └── javaclaw-api-admin/                        (javaclaw-api-admin, scaffold)
│       └── pom.xml
├── javaclaw-channel/                              (aggregator POM)
│   ├── pom.xml
│   ├── javaclaw-channel-discord/                  (javaclaw-channel-discord)
│   │   ├── pom.xml
│   │   └── src/main/java/ai/javaclaw/channels/discord/
│   │       ├── DiscordChannel
│   │       └── DiscordChannelAutoConfiguration
│   └── javaclaw-channel-telegram/                 (javaclaw-channel-telegram)
│       ├── pom.xml
│       └── src/main/java/ai/javaclaw/channels/telegram/
│           ├── TelegramChannel
│           └── TelegramChannelAutoConfiguration
├── javaclaw-provider/                             (aggregator POM)
│   ├── pom.xml
│   ├── javaclaw-provider-anthropic/               (javaclaw-provider-anthropic)
│   ├── javaclaw-provider-openai/                  (javaclaw-provider-openai)
│   ├── javaclaw-provider-ollama/                  (javaclaw-provider-ollama)
│   └── javaclaw-provider-google/                  (javaclaw-provider-google)
└── javaclaw-app/                                  (javaclaw-app, assembler)
    ├── pom.xml
    └── src/main/
        ├── java/ai/javaclaw/
        │   └── JavaClawApplication
        └── resources/
            ├── application.yaml
            ├── templates/
            │   ├── chat.html.peb                  (@Deprecated)
            │   └── base.html.peb                  (@Deprecated)
            └── db/migration/
                ├── V1__init_tasks.sql
                ├── V2__init_chat_memory.sql
                └── V3__allow_null_content_in_chat_memory.sql
```

Root `pom.xml` modules declaration uses the 3-layer hierarchy with aggregator modules:

```xml
<modules>
    <module>javaclaw-core</module>
    <module>javaclaw-api</module>
    <module>javaclaw-channel</module>
    <module>javaclaw-provider</module>
    <module>javaclaw-app</module>
</modules>
```

Each aggregator POM (`javaclaw-api`, `javaclaw-channel`, `javaclaw-provider`) declares its own child modules, e.g.:

```xml
<!-- javaclaw-api/pom.xml -->
<modules>
    <module>javaclaw-api-chat</module>
    <module>javaclaw-api-admin</module>
</modules>
```

### 2.5. Current-to-Target Structure Mapping

|                   Current Path                    |                                          Target Path                                           |                              Action                              |
|---------------------------------------------------|------------------------------------------------------------------------------------------------|------------------------------------------------------------------|
| `base/`                                           | `javaclaw-core/`                                                                               | Rename directory                                                 |
| `base/pom.xml` (artifactId=javaclaw-base)         | `javaclaw-core/pom.xml` (artifactId=javaclaw-core)                                            | Update artifactId                                                |
| `base/src/.../org/springframework/ai/chat/*`      | —                                                                                              | Remove (Spring AI hacks, replaced by wrappers in ai.javaclaw.ai) |
| `base/src/.../ai/javaclaw/onboarding/*`           | —                                                                                              | Remove (legacy onboarding interfaces)                            |
| —                                                 | `javaclaw-api/pom.xml`                                                                         | Create aggregator POM                                            |
| `app/src/.../ai/javaclaw/chat/ChatChannel`        | `javaclaw-api/javaclaw-api-chat/src/.../ai/javaclaw/api/chat/ChatChannel`                      | Move, update package                                             |
| `app/src/.../ai/javaclaw/chat/api/ChatController` | `javaclaw-api/javaclaw-api-chat/src/.../ai/javaclaw/api/chat/ChatController`                   | Move, update package                                             |
| `app/src/.../ai/javaclaw/chat/ws/*`               | `javaclaw-api/javaclaw-api-chat/src/.../ai/javaclaw/api/chat/ws/*`                             | Move, update packages                                            |
| `app/src/.../ai/javaclaw/chat/ChatHtml`           | `javaclaw-api/javaclaw-api-chat/src/.../ai/javaclaw/api/chat/ChatHtml`                         | Move, add @Deprecated(forRemoval = true)                         |
| `app/src/.../ai/javaclaw/chat/Htmx`               | `javaclaw-api/javaclaw-api-chat/src/.../ai/javaclaw/api/chat/Htmx`                             | Move, add @Deprecated(forRemoval = true)                         |
| —                                                 | `javaclaw-api/javaclaw-api-admin/pom.xml`                                                      | Create scaffold module                                           |
| `app/src/.../ai/javaclaw/onboarding/**`           | —                                                                                              | Remove (onboarding wizard)                                       |
| `app/src/main/resources/templates/onboarding/**`  | —                                                                                              | Remove (onboarding Pebble templates)                             |
| `app/src/main/resources/templates/chat.html.peb`  | `javaclaw-app/src/main/resources/templates/chat.html.peb`                                      | Keep with deprecation comment                                    |
| `app/src/main/resources/templates/base.html.peb`  | `javaclaw-app/src/main/resources/templates/base.html.peb`                                      | Keep with deprecation comment                                    |
| —                                                 | `javaclaw-channel/pom.xml`                                                                     | Create aggregator POM                                            |
| `plugins/discord/`                                | `javaclaw-channel/javaclaw-channel-discord/`                                                   | Move                                                             |
| `plugins/telegram/`                               | `javaclaw-channel/javaclaw-channel-telegram/`                                                  | Move                                                             |
| `plugins/brave/`                                  | —                                                                                              | Remove entirely                                                  |
| `plugins/playwright/`                             | —                                                                                              | Remove entirely                                                  |
| —                                                 | `javaclaw-provider/pom.xml`                                                                    | Create aggregator POM                                            |
| `providers/anthropic/`                            | `javaclaw-provider/javaclaw-provider-anthropic/`                                               | Move, update deps, remove OnboardingProvider                     |
| `providers/openai/`                               | `javaclaw-provider/javaclaw-provider-openai/`                                                  | Move, update deps, remove OnboardingProvider                     |
| `providers/ollama/`                               | `javaclaw-provider/javaclaw-provider-ollama/`                                                  | Move, update deps, remove OnboardingProvider                     |
| `providers/google/`                               | `javaclaw-provider/javaclaw-provider-google/`                                                  | Move, update deps, remove OnboardingProvider                     |

### 2.6. Data Models (Modules)

#### javaclaw-core

|  Parameter   |                                                                                                                                                                                 Value                                                                                                                                                                                  |
|--------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| artifactId   | `javaclaw-core`                                                                                                                                                                                                                                                                                                                                                        |
| packaging    | jar                                                                                                                                                                                                                                                                                                                                                                    |
| Dependencies | spring-boot-starter, spring-modulith-starter-core, spring-ai-client-chat, spring-ai-starter-model-chat-memory-repository-jdbc, spring-ai-starter-mcp-client, spring-ai-agent-utils, jobrunr-spring-boot-4-starter, spring-boot-starter-data-jdbc, commons-lang3, netty-resolver-dns-native-macos (runtime, macOS)                                                      |
| Packages     | ai.javaclaw.agent, ai.javaclaw.channels, ai.javaclaw.configuration, ai.javaclaw.files, ai.javaclaw.mcp, ai.javaclaw.providers, ai.javaclaw.tasks, ai.javaclaw.tools                                                                                                                                                                                                    |
| Exports      | Agent, DefaultAgent, Channel, ChannelRegistry, ChannelMessageReceivedEvent, ConfigurationManager, ConfigurationChangedEvent, Task, TaskManager, TaskRepository, RecurringTask, RecurringTaskRepository, McpTool, TaskTool, CheckListTool, AutoDiscoveredTool, AgentProvider, AgentEnvironment, YamlParser, YamlDocument, McpConnectionsProperties, McpHeaderCustomizer |

#### javaclaw-api (aggregator)

|  Parameter   |                           Value                           |
|--------------|-----------------------------------------------------------|
| artifactId   | `javaclaw-api`                                            |
| packaging    | pom                                                       |
| Modules      | javaclaw-api-chat, javaclaw-api-admin                     |
| Dependencies | None (aggregator only)                                    |

#### javaclaw-api-chat

|  Parameter   |                                       Value                                       |
|--------------|-----------------------------------------------------------------------------------|
| artifactId   | `javaclaw-api-chat`                                                               |
| packaging    | jar                                                                               |
| Dependencies | javaclaw-core, spring-boot-starter-webmvc, spring-boot-starter-websocket          |
| Packages     | ai.javaclaw.api.chat, ai.javaclaw.api.chat.ws                                     |
| Exports      | ChatChannel (Channel impl), ChatController, ChatWebSocketHandler, WebSocketConfig, ChatHtml (@Deprecated), Htmx (@Deprecated) |

#### javaclaw-api-admin

|  Parameter   |                                         Value                                         |
|--------------|---------------------------------------------------------------------------------------|
| artifactId   | `javaclaw-api-admin`                                                                  |
| packaging    | jar                                                                                   |
| Dependencies | javaclaw-core, spring-boot-starter-webmvc                                             |
| Packages     | ai.javaclaw.api.admin (scaffold, empty)                                               |
| Exports      | Not defined at current stage. Will be populated during Phase 3 roadmap implementation |

#### javaclaw-channel (aggregator)

|  Parameter   |                                 Value                                  |
|--------------|------------------------------------------------------------------------|
| artifactId   | `javaclaw-channel`                                                     |
| packaging    | pom                                                                    |
| Modules      | javaclaw-channel-discord, javaclaw-channel-telegram                    |
| Dependencies | None (aggregator only)                                                 |

#### javaclaw-channel-discord

|  Parameter   |                             Value                              |
|--------------|----------------------------------------------------------------|
| artifactId   | `javaclaw-channel-discord`                                     |
| packaging    | jar                                                            |
| Dependencies | javaclaw-core, spring-boot-starter, net.dv8tion:JDA:6.1.1      |
| Packages     | ai.javaclaw.channels.discord                                   |
| Exports      | DiscordChannel (Channel impl), DiscordChannelAutoConfiguration |

#### javaclaw-channel-telegram

|  Parameter   |                                                      Value                                                       |
|--------------|------------------------------------------------------------------------------------------------------------------|
| artifactId   | `javaclaw-channel-telegram`                                                                                      |
| packaging    | jar                                                                                                              |
| Dependencies | javaclaw-core, spring-boot-starter, telegrambots-springboot-longpolling-starter:9.4.0, telegrambots-client:9.4.0 |
| Packages     | ai.javaclaw.channels.telegram                                                                                    |
| Exports      | TelegramChannel (Channel impl), TelegramChannelAutoConfiguration                                                 |

#### javaclaw-provider (aggregator)

|  Parameter   |                                                         Value                                                          |
|--------------|------------------------------------------------------------------------------------------------------------------------|
| artifactId   | `javaclaw-provider`                                                                                                    |
| packaging    | pom                                                                                                                    |
| Modules      | javaclaw-provider-anthropic, javaclaw-provider-google, javaclaw-provider-ollama, javaclaw-provider-openai              |
| Dependencies | None (aggregator only)                                                                                                 |

#### javaclaw-app

|  Parameter   |                                                                                                                                                                                                   Value                                                                                                                                                                                                   |
|--------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| artifactId   | `javaclaw-app`                                                                                                                                                                                                                                                                                                                                                                                            |
| packaging    | jar (Spring Boot executable)                                                                                                                                                                                                                                                                                                                                                                              |
| Dependencies | javaclaw-core, javaclaw-api-chat, javaclaw-api-admin, javaclaw-channel-discord, javaclaw-channel-telegram, javaclaw-provider-anthropic, javaclaw-provider-openai, javaclaw-provider-ollama, javaclaw-provider-google, spring-boot-starter-webmvc, spring-boot-starter-actuator, spring-boot-starter-data-jdbc, spring-boot-starter-restclient, pebble-spring-boot-starter (@Deprecated, kept for chat templates), postgresql (runtime), flyway-database-postgresql (runtime) |
| Contents     | JavaClawApplication (main), application.yaml, Flyway migrations, Pebble templates: chat.html.peb, base.html.peb (both @Deprecated)                                                                                                                                                                                                                                                                       |

### 2.7. Configuration Requirements

Configuration files (`application.yaml`, profile yamls) MUST reside only in the `javaclaw-app/` module.

Modules `javaclaw-core`, `javaclaw-api/*`, `javaclaw-channel/*`, `javaclaw-provider/*` MUST NOT contain their own `application.yaml`. When defaults are needed, they are provided via `@ConfigurationProperties` with default values in Java code.

|         Parameter          | Required |  Type  |   Default   |         Description         |
|----------------------------|----------|--------|-------------|-----------------------------|
| spring.datasource.url      | yes      | string | —           | PostgreSQL connection URL   |
| spring.datasource.username | yes      | string | —           | Database username           |
| spring.datasource.password | yes      | string | —           | Database password           |
| spring.flyway.enabled      | no       | bool   | true        | Enable automatic migrations |
| spring.ai.mcp.client.*     | no       | object | —           | MCP server configuration    |
| javaclaw.workspace.path    | no       | string | ./workspace | Workspace directory path    |

### 2.8. Logging

| Level |            Event             |                      Message Format                       |
|-------|------------------------------|-----------------------------------------------------------|
| INFO  | Application started          | "JavaClaw started with modules: {module_list}"            |
| INFO  | Channel registered           | "Channel registered: {channel_name}"                      |
| INFO  | Channel disconnected         | "Channel unregistered: {channel_name}"                    |
| WARN  | Module not found at startup  | "Expected module {module_name} not found on classpath"    |
| ERROR | Circular dependency detected | "Circular dependency detected: {module_a} <-> {module_b}" |

### 2.9. Validation

|           Check            |         Timing         |                                     Rule                                     | Action on Violation |
|----------------------------|------------------------|------------------------------------------------------------------------------|---------------------|
| Module dependency graph    | mvn compile            | Dependencies are unidirectional, core does not depend on other modules       | Compilation error   |
| Spring Modulith boundaries | @ApplicationModuleTest | Core packages have no cyclic dependencies                                    | Test failure        |
| Autoconfiguration          | Application startup    | All channels and providers are discovered automatically                      | Startup error       |
| No legacy imports          | mvn compile            | No imports of removed classes anywhere (onboarding, htmx, brave, playwright) | Compilation error   |

### 2.10. Error Handling

This refactoring does not change the application's runtime behavior and does not introduce new API endpoints. Error handling remains unchanged.

**IF** during the refactoring a removed component is found to be used at runtime (not only in tests), **THEN** the refactoring MUST be halted until the dependency is clarified. Removal of a component that breaks runtime is PROHIBITED.

---

## 3. Acceptance Criteria Recommendations

| #  |                                                      WHEN                                                      |                                  THEN                                  |
|----|----------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------|
| 1  | `mvn clean compile` is executed from the project root                                                          | All modules compile without errors                                     |
| 2  | `mvn test` is executed from the project root                                                                   | All tests pass (except tests of removed components)                    |
| 3  | Application is started via `mvn spring-boot:run -pl javaclaw-app`                                              | Successful startup, all channels and providers registered in logs      |
| 4  | The `plugins/` directory is checked                                                                            | Directory is absent or empty                                           |
| 5  | The `base/` directory is checked                                                                               | Directory is absent (renamed to `javaclaw-core/`)                      |
| 6  | Search for imports of `org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor` across the project | Zero results                                                           |
| 7  | Search for imports of `ai.javaclaw.onboarding` across the project                                              | Zero results                                                           |
| 8  | Search for classes `ChatHtml`, `Htmx`, `OnboardingController`                                                  | `ChatHtml` and `Htmx` exist in `javaclaw-api-chat` with `@Deprecated(forRemoval = true)`. `OnboardingController` not found. |
| 9  | Search for dependencies on `javaclaw-plugin-brave` and `javaclaw-plugin-playwright`                            | Zero results in pom.xml files                                          |
| 10 | `javaclaw-app/src/main/java/` is checked                                                                       | Contains only `JavaClawApplication` (and optionally `IndexController`) |
| 11 | `javaclaw-app/src/main/resources/templates/` is checked                                                        | Contains only `chat.html.peb` and `base.html.peb` (both @Deprecated). No `onboarding/` subdirectory. |
| 12 | `javaclaw-core/pom.xml` is checked                                                                             | artifactId = `javaclaw-core`                                           |
| 13 | Dependency graph checked: `mvn dependency:tree -pl javaclaw-core`                                              | core does NOT contain dependencies on other project modules            |
| 14 | Dependency graph checked: `mvn dependency:tree -pl javaclaw-api/javaclaw-api-chat`                             | javaclaw-api-chat depends on javaclaw-core, not on other api modules   |
| 15 | A WebSocket message is sent to the agent via chat                                                              | Agent responds (functionality preserved)                               |

---

## 4. Non-Functional Requirements

### 4.1. Security

1. The refactoring MUST NOT change the application's security behavior.
2. Removal of plugins (Brave, Playwright) MUST NOT leave open endpoints or unused dependencies with vulnerabilities.
3. When removing Pebble templates, it is NECESSARY to ensure that static resources (CSS/JS), if any, do not remain accessible without authentication.

### 4.2. Performance

|        Parameter         |             Value              |                         Description                         |
|--------------------------|--------------------------------|-------------------------------------------------------------|
| Compilation time         | No more than +20% over current | Adding modules increases Maven overhead, but not critically |
| Application startup time | No change                      | Number of Spring beans does not change                      |
| Artifact size            | Decreases                      | Removal of Brave, Playwright, Pebble reduces the classpath  |

### 4.3. Reliability

1. The refactoring MUST be performed atomically — intermediate states where the application does not compile are acceptable only within a feature branch.
2. It is recommended to perform steps sequentially with intermediate commits:
   - Commit 1: rename base → javaclaw-core
   - Commit 2: extract javaclaw-api-chat from javaclaw-app
   - Commit 3: move channels from plugins to javaclaw-channel
   - Commit 4: remove legacy (brave, playwright, onboarding, htmx)
   - Commit 5: clean up javaclaw-app, update dependencies
3. Each intermediate commit MUST pass `mvn clean compile`.

### 4.4. Monitoring

Not defined at the current stage. The refactoring introduces no new metrics. The existing Spring Boot Actuator continues to work without changes.
