# Plan: JavaClaw Module Restructure

> **Status: COMPLETED** (2026-04-04). All modules restructured, build passes, directories renamed to final 3-layer structure.

## Task Description

Infrastructure refactoring of the JavaClaw platform to establish atomic separation-of-concerns at the Maven module level. The current structure violates SRP: `base/` mixes platform core with Spring AI hacks, `app/` combines assembler with business logic, and `plugins/` conflates communication channels with agent tools. The refactoring establishes clean architectural layers: **core**, **api**, **channels**, **providers**, **app** (assembler). Additionally, legacy components (onboarding wizard, htmx/Pebble, Brave, Playwright) are removed per roadmap Phase 0.2.

Source specification: `openspec/specs/javaclaw-module-restructure/javaclaw-module-restructure.md`

## Objective

When complete, JavaClaw will have a clean module hierarchy where each Maven module has a single responsibility, dependencies are strictly unidirectional, legacy code is removed, and the application compiles, tests pass, and starts successfully.

## Problem Statement

The current module structure creates the following problems:
1. **SRP violation in `base/`**: Contains both platform core AND Spring AI class overrides in `org.springframework.ai.chat.*` namespace, plus onboarding interfaces
2. **SRP violation in `app/`**: Combines assembler role (main, yaml, migrations) with chat UI, onboarding wizard, htmx/Pebble rendering
3. **Conceptual mixing in `plugins/`**: Communication channels (Discord, Telegram) co-located with agent tools (Brave, Playwright)
4. **Legacy debt**: Onboarding wizard, htmx integration, Pebble templates, Brave and Playwright plugins are scheduled for removal but still present

## Solution Approach

Sequential refactoring in atomic commits, each passing `mvn clean compile`:

1. **Rename base → javaclaw-core** with dependency updates + create Spring AI wrapper classes in `ai.javaclaw` namespace + remove onboarding interfaces + move Flyway migrations to javaclaw-app
2. **Extract javaclaw-api-chat module** from javaclaw-app's chat classes (under javaclaw-api aggregator)
3. **Create javaclaw-api-admin scaffold** (empty module for future Phase 3, under javaclaw-api aggregator)
4. **Move channels** from plugins/ to javaclaw-channel/ aggregator (javaclaw-channel-discord, javaclaw-channel-telegram)
5. **Move and update providers** under javaclaw-provider/ aggregator — remove onboarding classes, update deps to javaclaw-core
6. **Remove legacy plugins** (brave, playwright)
7. **Clean javaclaw-app module** — remove onboarding, update all POMs, fix IndexController (ChatHtml/Htmx moved to javaclaw-api-chat with @Deprecated, Pebble kept)
8. **Adapt/move tests** — relocate chat tests to javaclaw-api-chat, remove onboarding tests
9. **Full validation** — compile, test, startup check

## Relevant Files

### Existing Files (modified or moved — final paths after restructure)

- **`pom.xml`** (root) — modules list rewritten to 5 top-level modules: `javaclaw-core`, `javaclaw-api`, `javaclaw-channel`, `javaclaw-provider`, `javaclaw-app`
- **`javaclaw-core/pom.xml`** — artifactId renamed from `javaclaw-base` to `javaclaw-core`
- **`javaclaw-app/pom.xml`** — legacy deps removed, api/channel modules added, pebble-spring-boot-starter kept
- **`javaclaw-core/src/main/java/ai/javaclaw/JavaClawConfiguration.java`** — imports updated to use Spring AI wrappers
- **`javaclaw-core/src/main/java/ai/javaclaw/providers/AgentProvider.java`** — onboarding dependency removed
- **`javaclaw-core/src/main/java/ai/javaclaw/agent/memory/FileSystemChatMemoryRepository.java`** — updated to use wrapper `ai.javaclaw.ai.memory.AppendableChatMemoryRepository`
- **`javaclaw-core/src/main/java/ai/javaclaw/agent/memory/JdbcAppendableChatMemoryRepository.java`** — updated to use wrapper
- **`javaclaw-core/src/main/java/org/springframework/ai/chat/client/advisor/MessageChatMemoryAdvisor.java`** — removed (Spring AI hack)
- **`javaclaw-core/src/main/java/org/springframework/ai/chat/memory/AppendableChatMemoryRepository.java`** — removed (Spring AI hack)
- **`javaclaw-core/src/main/java/org/springframework/ai/chat/memory/MessageWindowChatMemory.java`** — removed (Spring AI hack)
- **`javaclaw-core/src/main/java/ai/javaclaw/onboarding/`** — 3 files removed (OnboardingProvider, AgentOnboardingProvider, AgentOnboardingProviders)
- **`javaclaw-core/src/main/resources/db/migration/`** — 3 SQL files moved to javaclaw-app
- **`javaclaw-app/src/main/java/ai/javaclaw/chat/ChatChannel.java`** — moved to javaclaw-api/javaclaw-api-chat
- **`javaclaw-app/src/main/java/ai/javaclaw/chat/api/ChatController.java`** — moved to javaclaw-api/javaclaw-api-chat
- **`javaclaw-app/src/main/java/ai/javaclaw/chat/ws/ChatWebSocketHandler.java`** — moved to javaclaw-api/javaclaw-api-chat
- **`javaclaw-app/src/main/java/ai/javaclaw/chat/ws/WebSocketConfig.java`** — moved to javaclaw-api/javaclaw-api-chat
- **`javaclaw-app/src/main/java/ai/javaclaw/chat/ChatHtml.java`** — moved to javaclaw-api/javaclaw-api-chat, marked `@Deprecated(forRemoval = true)`
- **`javaclaw-app/src/main/java/ai/javaclaw/chat/Htmx.java`** — moved to javaclaw-api/javaclaw-api-chat, marked `@Deprecated(forRemoval = true)`
- **`javaclaw-app/src/main/java/ai/javaclaw/onboarding/`** — 8 files removed
- **`javaclaw-app/src/main/java/ai/javaclaw/api/IndexController.java`** — reworked to remove onboarding redirect
- **`javaclaw-app/src/main/resources/templates/`** — `onboarding/` subdirectory removed; `chat.html.peb` and `base.html.peb` kept with `{# @Deprecated #}` comment
- **`plugins/discord/`** — moved to `javaclaw-channel/javaclaw-channel-discord/`, DiscordOnboardingProvider removed
- **`plugins/telegram/`** — moved to `javaclaw-channel/javaclaw-channel-telegram/`, TelegramOnboardingProvider removed
- **`plugins/brave/`** — deleted entirely
- **`plugins/playwright/`** — deleted entirely
- **`javaclaw-provider/javaclaw-provider-anthropic/pom.xml`** — updated javaclaw-base → javaclaw-core
- **`javaclaw-provider/javaclaw-provider-google/pom.xml`** — updated javaclaw-base → javaclaw-core
- **`javaclaw-provider/javaclaw-provider-ollama/pom.xml`** — updated javaclaw-base → javaclaw-core
- **`javaclaw-provider/javaclaw-provider-openai/pom.xml`** — updated javaclaw-base → javaclaw-core
- **`javaclaw-provider/javaclaw-provider-anthropic/.../AnthropicAgentOnboardingProvider.java`** — removed
- **`javaclaw-provider/javaclaw-provider-google/.../GoogleGenAIAgentOnboardingProvider.java`** — removed
- **`javaclaw-provider/javaclaw-provider-ollama/.../OllamaAgentOnboardingProvider.java`** — removed
- **`javaclaw-provider/javaclaw-provider-openai/.../OpenAIAgentOnboardingProvider.java`** — removed

### Existing Test Files (moved, adapted, or removed)

- **`javaclaw-core/src/test/.../MessageWindowChatMemoryTest.java`** — removed (tested removed hack class)
- **`javaclaw-app/src/test/.../chat/ChatChannelTest.java`** — moved to javaclaw-api/javaclaw-api-chat/src/test/
- **`javaclaw-app/src/test/.../chat/ws/ChatWebSocketHandlerTest.java`** — moved to javaclaw-api/javaclaw-api-chat/src/test/
- **`javaclaw-app/src/test/.../api/OnboardingControllerTest.java`** — removed
- **`javaclaw-app/src/test/.../e2e/OnboardingE2ETest.java`** — removed
- **`plugins/discord/src/test/.../DiscordOnboardingProviderTest.java`** — removed (moved with dir, then deleted)
- **`plugins/brave/src/test/`** — deleted with module
- **`plugins/playwright/src/test/`** — deleted with module

### New Files (final paths after restructure)

- **`javaclaw-core/pom.xml`** — renamed from base/pom.xml with updated artifactId
- **`javaclaw-core/src/main/java/ai/javaclaw/ai/advisor/JavaClawMessageChatMemoryAdvisor.java`** — wrapper for Spring AI hack
- **`javaclaw-core/src/main/java/ai/javaclaw/ai/memory/JavaClawMessageWindowChatMemory.java`** — wrapper for Spring AI hack
- **`javaclaw-core/src/main/java/ai/javaclaw/ai/memory/AppendableChatMemoryRepository.java`** — wrapper interface for Spring AI hack
- **`javaclaw-api/pom.xml`** — aggregator POM for API modules
- **`javaclaw-api/javaclaw-api-chat/pom.xml`** — new module javaclaw-api-chat
- **`javaclaw-api/javaclaw-api-chat/src/main/java/ai/javaclaw/api/chat/ChatChannel.java`** — moved from javaclaw-app
- **`javaclaw-api/javaclaw-api-chat/src/main/java/ai/javaclaw/api/chat/ChatController.java`** — moved from javaclaw-app
- **`javaclaw-api/javaclaw-api-chat/src/main/java/ai/javaclaw/api/chat/ChatHtml.java`** — moved from javaclaw-app, marked `@Deprecated(forRemoval = true)`
- **`javaclaw-api/javaclaw-api-chat/src/main/java/ai/javaclaw/api/chat/Htmx.java`** — moved from javaclaw-app, marked `@Deprecated(forRemoval = true)`
- **`javaclaw-api/javaclaw-api-chat/src/main/java/ai/javaclaw/api/chat/ws/ChatWebSocketHandler.java`** — moved from javaclaw-app
- **`javaclaw-api/javaclaw-api-chat/src/main/java/ai/javaclaw/api/chat/ws/WebSocketConfig.java`** — moved from javaclaw-app
- **`javaclaw-api/javaclaw-api-admin/pom.xml`** — scaffold module javaclaw-api-admin
- **`javaclaw-channel/pom.xml`** — aggregator POM for channel modules
- **`javaclaw-channel/javaclaw-channel-discord/pom.xml`** — moved from plugins/discord, updated artifactId
- **`javaclaw-channel/javaclaw-channel-telegram/pom.xml`** — moved from plugins/telegram, updated artifactId
- **`javaclaw-provider/pom.xml`** — aggregator POM for provider modules
- **`javaclaw-app/src/main/resources/db/migration/V1__init_tasks.sql`** — moved from javaclaw-core
- **`javaclaw-app/src/main/resources/db/migration/V2__init_chat_memory.sql`** — moved from javaclaw-core
- **`javaclaw-app/src/main/resources/db/migration/V3__allow_null_content_in_chat_memory.sql`** — moved from javaclaw-core

## Implementation Phases

### Phase 1: Foundation (Tasks 1-2)

Rename `base/` to `javaclaw-core/`, update all POM references, create Spring AI wrapper classes in the project's own namespace (`ai.javaclaw.ai.*`), update all internal imports, remove original hack classes, remove onboarding interfaces from core, adapt `AgentProvider` to work without onboarding, move Flyway migrations to `javaclaw-app/`. Each step MUST compile.

**Critical detail — Spring AI wrappers**: The 3 hack classes in `org.springframework.ai.chat.*` are used within core itself:
- `MessageChatMemoryAdvisor` → used in `JavaClawConfiguration.java`
- `MessageWindowChatMemory` → used in `JavaClawConfiguration.java`
- `AppendableChatMemoryRepository` → implemented by `FileSystemChatMemoryRepository` and `JdbcAppendableChatMemoryRepository`

Per rule 2.3 #5: create replacements in `ai.javaclaw.ai.*` namespace FIRST, update imports, THEN remove originals.

**Critical detail — AgentProvider**: `AgentProvider.java` has a `SequencedCollection<AgentOnboardingProvider>` field. After removing onboarding, this field and its constructor parameter must be removed.

### Phase 2: Module Extraction (Tasks 3-7)

Create new modules (`javaclaw-api-chat`, `javaclaw-api-admin` under `javaclaw-api/` aggregator), move channels from `plugins/` to `javaclaw-channel/` aggregator, move and update providers under `javaclaw-provider/` aggregator, remove onboarding classes, delete legacy plugins (Brave, Playwright). Tasks 3-7 can run in parallel as they touch independent directories.

**Note on parallelism**: Tasks 3-7 each create/modify their OWN pom.xml only. They do NOT touch root `pom.xml` or `javaclaw-app/pom.xml` — that's deferred to Task 8 to avoid merge conflicts.

### Phase 3: Integration & Polish (Tasks 8-10)

Consolidate all POM changes (root modules list, app dependencies), clean app of legacy code (onboarding), rework IndexController, move/adapt tests, run full validation. Note: `ChatHtml`, `Htmx`, `chat.html.peb`, `base.html.peb`, and `pebble-spring-boot-starter` are PRESERVED (marked `@Deprecated`) — removing them would break the chat UI. Full Pebble removal is deferred to a separate task.

## Team Orchestration

- You operate as the team lead and orchestrate the team to execute the plan.
- You're responsible for deploying the right team members with the right context to execute the plan.
- IMPORTANT: You NEVER operate directly on the codebase. You use `Task` and `Task*` tools to deploy team members to to the building, validating, testing, deploying, and other tasks.
  - This is critical. You're job is to act as a high level director of the team, not a builder.
  - You're role is to validate all work is going well and make sure the team is on track to complete the plan.
  - You'll orchestrate this by using the Task* Tools to manage coordination between the team members.
  - Communication is paramount. You'll use the Task* Tools to communicate with the team members and ensure they're on track to complete the plan.
- Take note of the session id of each team member. This is how you'll reference them.

### Team Members

- Builder
  - Name: builder-foundation
  - Role: Rename base→core, create Spring AI wrappers, remove onboarding from core, move migrations. Foundation work that all other tasks depend on.
  - Agent Type: builder
  - Resume: true
- Builder
  - Name: builder-modules
  - Role: Create api/chat module (extract from app), create api/admin scaffold, move channels from plugins to channels, update providers.
  - Agent Type: builder
  - Resume: true
- Builder
  - Name: builder-cleanup
  - Role: Remove legacy plugins (brave, playwright), clean app module (remove onboarding, htmx, pebble), update all POMs, fix IndexController.
  - Agent Type: builder
  - Resume: true
- Builder
  - Name: builder-tests
  - Role: Move chat tests to api/chat, remove onboarding tests, adapt e2e tests.
  - Agent Type: builder
  - Resume: true
- Builder
  - Name: validator-final
  - Role: Full validation — compile, test, startup check, acceptance criteria verification.
  - Agent Type: validator
  - Resume: false

## Testing Strategy

Test pyramid ratio: **80% unit / 15% integration-API / 5% UI e2e**

This is a structural refactoring — we are primarily PRESERVING existing tests, not writing new ones. The test strategy focuses on: (a) moving tests to correct modules, (b) removing tests of deleted components, (c) verifying everything still passes.

### Unit Tests (80%)

- **Existing tests to preserve (move to correct module)**:
  - `ChatChannelTest` → move to `javaclaw-api/javaclaw-api-chat/src/test/java/ai/javaclaw/api/chat/`
  - `ChatWebSocketHandlerTest` → move to `javaclaw-api/javaclaw-api-chat/src/test/java/ai/javaclaw/api/chat/ws/`
  - `DiscordChannelTest` → stays in `javaclaw-channel/javaclaw-channel-discord/src/test/` (moved with directory)
  - `TelegramChannelTest` → stays in `javaclaw-channel/javaclaw-channel-telegram/src/test/` (moved with directory)
  - `JdbcAppendableChatMemoryRepositoryTest` → stays in `javaclaw-core/src/test/`, update imports to wrapper
  - `FileSystemChatMemoryRepositoryTest` → stays in `javaclaw-core/src/test/`, verify imports compile with wrapper
  - `AnthropicClaudeCodeBackendTest` → stays in `javaclaw-provider/javaclaw-provider-anthropic/src/test/`
- **Existing tests to remove**:
  - `MessageWindowChatMemoryTest` (tests removed hack class)
  - `OnboardingControllerTest` (tests removed component)
  - `DiscordOnboardingProviderTest` (tests removed component)
  - `BraveOnboardingProviderTest` (deleted with module)
  - `BraveWebSearchAutoConfigurationTests` (deleted with module)
  - `PlaywrightOnboardingProviderTest` (deleted with module)
  - `PlaywrightAutoConfigurationTests` (deleted with module)
- **New unit tests**: Minimal — verify wrapper classes delegate correctly (optional, wrappers are thin)

### Integration / API Tests (15%)

- `mvn clean compile` — all modules compile without errors
- `mvn test` — all remaining tests pass
- `mvn dependency:tree -pl javaclaw-core` — core has NO dependencies on other project modules
- `mvn dependency:tree -pl javaclaw-api/javaclaw-api-chat` — javaclaw-api-chat depends only on javaclaw-core

### UI E2E Tests (5%)

- **Existing e2e tests to preserve**:
  - `ChatE2ETest` — chat functionality preserved
  - `ConversationSwitchingE2ETest` — conversation switching preserved
  - `TaskCreationE2ETest` — task creation preserved
  - `MultiInstanceE2ETest` — multi-instance preserved
- **Existing e2e tests to remove**:
  - `OnboardingE2ETest` — onboarding removed
- **Existing e2e tests to adapt**:
  - `E2ETestBase` / `ChatReadyE2ETestBase` — remove any onboarding references if present
- **Startup verification**: `mvn spring-boot:run -pl javaclaw-app` starts successfully

## Step by Step Tasks

- IMPORTANT: Execute every step in order, top to bottom. Each task maps directly to a `TaskCreate` call.
- Before you start, run `TaskCreate` to create the initial task list that all team members can see and execute.

### 1. Rename base to core and update POMs

- **Task ID**: rename-base-to-core
- **Depends On**: none
- **Assigned To**: builder-foundation
- **Agent Type**: builder
- **Stack**: Java Spring Boot maven lombok pom.xml
- **Parallel**: false
- **Tests**: Integration: `mvn clean compile` passes after rename
- Rename directory `base/` → `javaclaw-core/` using `git mv base javaclaw-core`
- In `javaclaw-core/pom.xml`: change `<artifactId>javaclaw-base</artifactId>` → `<artifactId>javaclaw-core</artifactId>`
- In root `pom.xml`: change `<module>base</module>` → `<module>javaclaw-core</module>`
- In `javaclaw-app/pom.xml`: change `javaclaw-base` → `javaclaw-core` in dependency
- In all `plugins/*/pom.xml`: change `javaclaw-base` → `javaclaw-core`
- In all `providers/*/pom.xml`: change `javaclaw-base` → `javaclaw-core`
- Run `mvn clean compile -pl javaclaw-core` to verify core compiles
- Run `mvn clean compile` to verify ALL modules compile

### 2. Prepare core — Spring AI wrappers + remove onboarding + move migrations

- **Task ID**: prepare-core
- **Depends On**: rename-base-to-core
- **Assigned To**: builder-foundation
- **Agent Type**: builder
- **Stack**: Java Spring Boot entity record pattern matching sealed interface exception error handling
- **Parallel**: false
- **Tests**: Unit: `JdbcAppendableChatMemoryRepositoryTest` adapted to wrapper. Integration: `mvn clean compile` passes.
- **Step 2a: Create Spring AI wrapper classes** in `ai.javaclaw.ai` namespace:
  - Create `javaclaw-core/src/main/java/ai/javaclaw/ai/memory/AppendableChatMemoryRepository.java` — interface extending `ChatMemoryRepository` with `append()` method (copy contract from hack, use `ai.javaclaw.ai.memory` package)
  - Create `javaclaw-core/src/main/java/ai/javaclaw/ai/memory/JavaClawMessageWindowChatMemory.java` — copy logic from hack `MessageWindowChatMemory` into `ai.javaclaw.ai.memory` package, referencing new `AppendableChatMemoryRepository`
  - Create `javaclaw-core/src/main/java/ai/javaclaw/ai/advisor/JavaClawMessageChatMemoryAdvisor.java` — copy logic from hack `MessageChatMemoryAdvisor` into `ai.javaclaw.ai.advisor` package
- **Step 2b: Update all internal imports** within javaclaw-core:
  - `JavaClawConfiguration.java`: replace `org.springframework.ai.chat.memory.MessageWindowChatMemory` → `ai.javaclaw.ai.memory.JavaClawMessageWindowChatMemory`
  - `JavaClawConfiguration.java`: replace `org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor` → `ai.javaclaw.ai.advisor.JavaClawMessageChatMemoryAdvisor`
  - `FileSystemChatMemoryRepository.java`: replace `org.springframework.ai.chat.memory.AppendableChatMemoryRepository` → `ai.javaclaw.ai.memory.AppendableChatMemoryRepository`
  - `JdbcAppendableChatMemoryRepository.java`: replace same import
  - `JdbcAppendableChatMemoryRepositoryTest.java`: update import if needed
- **Step 2c: Remove original hack classes**:
  - Delete `javaclaw-core/src/main/java/org/springframework/ai/chat/client/advisor/MessageChatMemoryAdvisor.java`
  - Delete `javaclaw-core/src/main/java/org/springframework/ai/chat/memory/AppendableChatMemoryRepository.java`
  - Delete `javaclaw-core/src/main/java/org/springframework/ai/chat/memory/MessageWindowChatMemory.java`
  - Delete `javaclaw-core/src/test/java/org/springframework/ai/chat/memory/MessageWindowChatMemoryTest.java`
  - Delete empty `org/springframework/` directory tree from javaclaw-core
- **Step 2d: Remove onboarding from javaclaw-core**:
  - Delete `javaclaw-core/src/main/java/ai/javaclaw/onboarding/OnboardingProvider.java`
  - Delete `javaclaw-core/src/main/java/ai/javaclaw/onboarding/AgentOnboardingProvider.java`
  - Delete `javaclaw-core/src/main/java/ai/javaclaw/onboarding/AgentOnboardingProviders.java`
  - Delete `javaclaw-core/src/main/java/ai/javaclaw/onboarding/` directory
  - Update `javaclaw-core/src/main/java/ai/javaclaw/providers/AgentProvider.java`: remove `AgentOnboardingProvider` import, remove `SequencedCollection<AgentOnboardingProvider> agentOnboardingProviders` field and constructor parameter. Verify no other references.
- **Step 2e: Move Flyway migrations**:
  - Create `javaclaw-app/src/main/resources/db/migration/` directory
  - Move `javaclaw-core/src/main/resources/db/migration/V1__init_tasks.sql` → `javaclaw-app/src/main/resources/db/migration/`
  - Move `javaclaw-core/src/main/resources/db/migration/V2__init_chat_memory.sql` → `javaclaw-app/src/main/resources/db/migration/`
  - Move `javaclaw-core/src/main/resources/db/migration/V3__allow_null_content_in_chat_memory.sql` → `javaclaw-app/src/main/resources/db/migration/`
  - Delete empty `javaclaw-core/src/main/resources/db/` directory
- Run `mvn clean compile` to verify everything compiles

### 3. Create api/chat module

- **Task ID**: create-api-chat
- **Depends On**: prepare-core
- **Assigned To**: builder-modules
- **Agent Type**: builder
- **Stack**: Java Spring Boot controller Spring MVC WebSocket maven
- **Parallel**: true (with tasks 4, 5, 6)
- **Tests**: Unit: ChatChannelTest, ChatWebSocketHandlerTest moved and compiling. Integration: module compiles standalone.
- Create directory structure: `javaclaw-api/javaclaw-api-chat/src/main/java/ai/javaclaw/api/chat/` and `javaclaw-api/javaclaw-api-chat/src/main/java/ai/javaclaw/api/chat/ws/` and `javaclaw-api/javaclaw-api-chat/src/test/java/`
- Create `javaclaw-api/pom.xml` (aggregator) with modules: `javaclaw-api-chat`, `javaclaw-api-admin`
- Create `javaclaw-api/javaclaw-api-chat/pom.xml` with:
  - Parent: javaclaw-parent
  - ArtifactId: `javaclaw-api-chat`
  - Dependencies: javaclaw-core, spring-boot-starter-webmvc, spring-boot-starter-websocket
- Move classes from `javaclaw-app/src/main/java/` to `javaclaw-api/javaclaw-api-chat/src/main/java/` with package updates:
  - `ai.javaclaw.chat.ChatChannel` → `ai.javaclaw.api.chat.channel.ChatChannel` (update package declaration)
  - `ai.javaclaw.chat.api.ChatController` → `ai.javaclaw.api.chat.ChatController` (update package + imports)
  - `ai.javaclaw.chat.ChatHtml` → `ai.javaclaw.api.chat.ChatHtml` (update package, add `@Deprecated(forRemoval = true)` annotation with Javadoc: "Scheduled for removal — will be replaced when migrating from Pebble to REST/SPA")
  - `ai.javaclaw.chat.Htmx` → `ai.javaclaw.api.chat.Htmx` (update package, add `@Deprecated(forRemoval = true)` with same Javadoc)
  - `ai.javaclaw.chat.ws.ChatWebSocketHandler` → `ai.javaclaw.api.chat.ws.ChatWebSocketHandler`
  - `ai.javaclaw.chat.ws.WebSocketConfig` → `ai.javaclaw.api.chat.ws.WebSocketConfig`
- Update imports of `ChatHtml` and `Htmx` in `ChatChannel` and `ChatWebSocketHandler` to new package `ai.javaclaw.api.chat`
- Move tests to `javaclaw-api/javaclaw-api-chat/src/test/java/` with package updates:
  - `javaclaw-app/.../chat/ChatChannelTest.java` → `javaclaw-api/javaclaw-api-chat/src/test/java/ai/javaclaw/api/chat/ChatChannelTest.java`
  - `javaclaw-app/.../chat/ws/ChatWebSocketHandlerTest.java` → `javaclaw-api/javaclaw-api-chat/src/test/java/ai/javaclaw/api/chat/ws/ChatWebSocketHandlerTest.java`
- Add test dependencies to `javaclaw-api/javaclaw-api-chat/pom.xml` if needed (spring-boot-starter-test)
- Do NOT update root pom.xml or javaclaw-app/pom.xml yet (deferred to task 8)
- Verify: `mvn clean compile -f javaclaw-api/javaclaw-api-chat/pom.xml` (root pom not yet updated, compile from module directly)

### 4. Create api/admin scaffold

- **Task ID**: create-api-admin
- **Depends On**: prepare-core
- **Assigned To**: builder-modules
- **Agent Type**: builder
- **Stack**: Java Spring Boot maven
- **Parallel**: true (with tasks 3, 5, 6)
- **Tests**: None (scaffold only)
- Create directory: `javaclaw-api/javaclaw-api-admin/`
- Create `javaclaw-api/javaclaw-api-admin/pom.xml` with:
  - Parent: javaclaw-parent
  - ArtifactId: `javaclaw-api-admin`
  - Dependencies: javaclaw-core, spring-boot-starter-webmvc
- No source files — scaffold only
- Module declared in `javaclaw-api/pom.xml` aggregator (created in task 3)
- Do NOT update root pom.xml yet

### 5. Move channels from plugins to channels

- **Task ID**: move-channels
- **Depends On**: prepare-core
- **Assigned To**: builder-modules
- **Agent Type**: builder
- **Stack**: Java Spring Boot maven autoconfiguration
- **Parallel**: true (with tasks 3, 4, 6)
- **Tests**: Unit: DiscordChannelTest, TelegramChannelTest preserved. Integration: modules compile standalone.
- Create `javaclaw-channel/pom.xml` (aggregator) with modules: `javaclaw-channel-discord`, `javaclaw-channel-telegram`
- Move directories using `git mv`:
  - `git mv plugins/discord javaclaw-channel/javaclaw-channel-discord`
  - `git mv plugins/telegram javaclaw-channel/javaclaw-channel-telegram`
- **Discord module updates** (`javaclaw-channel/javaclaw-channel-discord/`):
  - `pom.xml`: change artifactId `javaclaw-plugin-discord` → `javaclaw-channel-discord`, verify dependency is `javaclaw-core` (updated in task 1)
  - Delete `DiscordOnboardingProvider.java` and its test `DiscordOnboardingProviderTest.java`
  - Delete onboarding template: `src/main/resources/templates/onboarding/steps/discord.html.peb` and empty templates dirs
  - Update `DiscordChannelAutoConfiguration.java`: remove any `@Bean` method for `DiscordOnboardingProvider`
  - Verify `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` only lists `DiscordChannelAutoConfiguration`
- **Telegram module updates** (`javaclaw-channel/javaclaw-channel-telegram/`):
  - `pom.xml`: change artifactId `javaclaw-plugin-telegram` → `javaclaw-channel-telegram`
  - Delete `TelegramOnboardingProvider.java`
  - Delete onboarding template: `src/main/resources/templates/onboarding/steps/telegram.html.peb` and empty templates dirs
  - Update `TelegramChannelAutoConfiguration.java`: remove any `@Bean` for `TelegramOnboardingProvider`
  - Verify `.imports` file
- Do NOT update root pom.xml yet

### 6. Update providers — remove onboarding, update dependencies

- **Task ID**: update-providers
- **Depends On**: prepare-core
- **Assigned To**: builder-modules
- **Agent Type**: builder
- **Stack**: Java Spring Boot maven autoconfiguration
- **Parallel**: true (with tasks 3, 4, 5)
- **Tests**: Unit: AnthropicClaudeCodeBackendTest preserved. Integration: each provider compiles standalone.
- Create `javaclaw-provider/pom.xml` (aggregator) with modules: `javaclaw-provider-anthropic`, `javaclaw-provider-google`, `javaclaw-provider-ollama`, `javaclaw-provider-openai`
- Move provider directories under `javaclaw-provider/` aggregator (e.g. `git mv providers/anthropic javaclaw-provider/javaclaw-provider-anthropic`)
- **Anthropic provider** (`javaclaw-provider/javaclaw-provider-anthropic/`):
  - Verify pom.xml has `javaclaw-core` (updated in task 1)
  - Delete `AnthropicAgentOnboardingProvider.java`
  - Verify remaining files: `AnthropicClaudeCodeBackend.java`, `AnthropicClaudeCodeOAuthTokenExtractor.java`, `AnthropticClaudeCodeConfiguration.java` — module is NOT empty
- **Google provider** (`javaclaw-provider/javaclaw-provider-google/`):
  - Delete `GoogleGenAIAgentOnboardingProvider.java`
  - Module becomes thin wrapper (pom.xml with `spring-ai-starter-model-google-genai` only) — this is expected per spec 2.1.5.5
- **Ollama provider** (`javaclaw-provider/javaclaw-provider-ollama/`):
  - Delete `OllamaAgentOnboardingProvider.java`
  - Module becomes thin wrapper (pom.xml with `spring-ai-starter-model-ollama`)
- **OpenAI provider** (`javaclaw-provider/javaclaw-provider-openai/`):
  - Delete `OpenAIAgentOnboardingProvider.java`
  - Module becomes thin wrapper (pom.xml with `spring-ai-starter-model-openai`)
- Verify no provider has AutoConfiguration.imports referencing onboarding (none do currently)

### 7. Remove legacy plugins (Brave, Playwright)

- **Task ID**: remove-legacy-plugins
- **Depends On**: move-channels
- **Assigned To**: builder-cleanup
- **Agent Type**: builder
- **Stack**: Java maven
- **Parallel**: false
- **Tests**: Integration: no compilation errors from removed modules.
- Delete entire `plugins/brave/` directory: `rm -rf plugins/brave`
- Delete entire `plugins/playwright/` directory: `rm -rf plugins/playwright`
- If `plugins/` directory is now empty, delete it: `rmdir plugins` (it should be empty after channels moved out)
- Do NOT update root pom.xml or app/pom.xml yet (deferred to task 8)

### 8. Clean app module — remove legacy, update all POMs

- **Task ID**: clean-app-module
- **Depends On**: create-api-chat, create-api-admin, move-channels, update-providers, remove-legacy-plugins
- **Assigned To**: builder-cleanup
- **Agent Type**: builder
- **Stack**: Java Spring Boot controller maven exception error handling
- **Parallel**: false
- **Tests**: Integration: `mvn clean compile` passes for ALL modules. All new modules visible.
- **Step 8a: Remove legacy code from javaclaw-app**:
  - Delete entire `javaclaw-app/src/main/java/ai/javaclaw/onboarding/` directory (OnboardingController, S1-S6 steps)
  - Delete remaining empty `javaclaw-app/src/main/java/ai/javaclaw/chat/` directory (all classes including ChatHtml and Htmx already moved to javaclaw-api-chat in task 3)
  - Delete `javaclaw-app/src/main/resources/templates/onboarding/` directory (onboarding templates only)
  - Keep `javaclaw-app/src/main/resources/templates/chat.html.peb` and `javaclaw-app/src/main/resources/templates/base.html.peb` — add `{# @Deprecated: scheduled for removal when migrating from Pebble to REST/SPA #}` comment at top of each file
- **Step 8b: Rework IndexController**:
  - Remove onboarding redirect logic
  - Remove `agent.onboarding.completed` property reference
  - Simplify: `GET /` and `GET /index` redirect to `/chat`
  - Remove unused imports
- **Step 8c: Update root pom.xml `<modules>` section**:
  - Remove: `base`, `plugins/discord`, `plugins/telegram`, `plugins/playwright`, `plugins/brave`, `providers/anthropic`, `providers/google`, `providers/ollama`, `providers/openai`
  - Add: `javaclaw-core`, `javaclaw-api`, `javaclaw-channel`, `javaclaw-provider`, `javaclaw-app`
  - Final modules list (3-layer hierarchy with aggregators):

    ```xml
    <modules>
        <module>javaclaw-core</module>
        <module>javaclaw-api</module>
        <module>javaclaw-channel</module>
        <module>javaclaw-provider</module>
        <module>javaclaw-app</module>
    </modules>
    ```
- **Step 8d: Update javaclaw-app/pom.xml dependencies**:
  - Remove: `javaclaw-plugin-discord`, `javaclaw-plugin-telegram`, `javaclaw-plugin-playwright`, `javaclaw-plugin-brave`
  - Add: `javaclaw-api-chat`, `javaclaw-api-admin`, `javaclaw-channel-discord`, `javaclaw-channel-telegram`
  - Keep: all provider dependencies (`javaclaw-provider-*`), `javaclaw-core` (already updated in task 1)
  - Keep: `pebble-spring-boot-starter` (still needed for chat.html.peb and base.html.peb; marked @Deprecated, will be removed when migrating to REST/SPA)
  - Remove: `spring-boot-starter-websocket` (now in javaclaw-api-chat module)
  - Keep: `spring-boot-starter-webmvc`, `spring-boot-starter-actuator`, `spring-boot-starter-data-jdbc`, `spring-boot-starter-restclient`, `postgresql`, `flyway-database-postgresql`, `spring-boot-devtools`, test dependencies
- Run `mvn clean compile` — ALL modules MUST compile

### 9. Move and adapt tests

- **Task ID**: move-adapt-tests
- **Depends On**: clean-app-module
- **Assigned To**: builder-tests
- **Agent Type**: builder
- **Stack**: Java MockMvc assertj test structure test naming mockito integration test
- **Parallel**: false
- **Tests**: All moved tests compile. `mvn test` passes.
- **Remove onboarding tests from javaclaw-app**:
  - Delete `javaclaw-app/src/test/java/ai/javaclaw/api/OnboardingControllerTest.java`
  - Delete `javaclaw-app/src/test/java/ai/javaclaw/e2e/OnboardingE2ETest.java`
- **Adapt e2e test bases** (if they reference onboarding):
  - Check `javaclaw-app/src/test/java/ai/javaclaw/e2e/E2ETestBase.java` for onboarding imports/references — remove if found
  - Check `javaclaw-app/src/test/java/ai/javaclaw/e2e/ChatReadyE2ETestBase.java` — remove onboarding refs if found (e.g., Javadoc mentioning "onboarding to already be completed", `agent.onboarding.completed` property)
  - Check `javaclaw-app/src/test/resources/application-e2e.yaml` (or similar profile YAML) for `agent.onboarding.completed` property — remove if found
- **Verify core tests compile after wrapper migration**:
  - Check `FileSystemChatMemoryRepositoryTest` in `javaclaw-core/src/test/` — verify imports use wrapper `ai.javaclaw.ai.memory.AppendableChatMemoryRepository` if needed
- **Update package references in moved tests** (already partially done in task 3, verify):
  - Verify `ChatChannelTest` imports match new package `ai.javaclaw.api.chat`
  - Verify `ChatWebSocketHandlerTest` imports match new package
- **Add test dependencies to javaclaw-api/javaclaw-api-chat/pom.xml** if not already present:
  - `spring-boot-starter-test` (test scope)
  - Any mocking/assertion libraries used by the moved tests
- **Verify all provider tests compile** after onboarding removal (AnthropicClaudeCodeBackendTest)
- Run `mvn test` — all tests MUST pass (some may be skipped if they require running DB/containers)
- Run `mvn test -pl javaclaw-core` — core unit tests pass
- Run `mvn test -pl javaclaw-api/javaclaw-api-chat` — chat tests pass

### 10. Final validation

- **Task ID**: validate-all
- **Depends On**: move-adapt-tests
- **Assigned To**: validator-final
- **Agent Type**: validator
- **Stack**: Java Spring Boot maven test structure integration test surefire failsafe
- **Parallel**: false
- **Acceptance criteria verification** (all 15 criteria from spec section 3):
  1. `mvn clean compile` — all modules compile without errors
  2. `mvn test` — all tests pass
  3. `mvn spring-boot:run -pl javaclaw-app` — successful startup (verify in logs: channels and providers registered)
  4. Verify `plugins/` directory absent or empty
  5. Verify `base/` directory absent (renamed to `javaclaw-core/`)
  6. `grep -r "org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor" --include="*.java"` — zero results
  7. `grep -r "ai.javaclaw.onboarding" --include="*.java"` — zero results
  8. Verify `ChatHtml` and `Htmx` exist in `javaclaw-api/javaclaw-api-chat` with `@Deprecated(forRemoval = true)`. Verify `OnboardingController` not found anywhere.
  9. `grep -r "javaclaw-plugin-brave\|javaclaw-plugin-playwright" --include="pom.xml"` — zero results
  10. Verify `javaclaw-app/src/main/java/` contains only `JavaClawApplication` and `IndexController` (in `api/` subpackage)
  11. Verify `javaclaw-app/src/main/resources/templates/` contains ONLY `chat.html.peb` and `base.html.peb` (both marked @Deprecated). No `onboarding/` subdirectory.
  12. Verify `javaclaw-core/pom.xml` has artifactId = `javaclaw-core`
  13. `mvn dependency:tree -pl javaclaw-core` — no dependencies on other project modules
  14. `mvn dependency:tree -pl javaclaw-api/javaclaw-api-chat` — depends on javaclaw-core only
  15. WebSocket chat test (if feasible in CI) — agent responds
- **Structural checks**:
  - Verify module dependency graph is unidirectional (core has no project deps, api/channels/providers depend only on core)
  - Verify no cross-layer dependencies (api/chat does NOT depend on api/admin, channels/discord does NOT depend on channels/telegram)
  - Verify no imports of removed packages anywhere
- **Report**: pass/fail with detailed findings

## Acceptance Criteria

1. `mvn clean compile` executes successfully from project root — all modules compile
2. `mvn test` executes successfully — all tests pass (except tests of removed components, which are deleted)
3. Application starts via `mvn spring-boot:run -pl javaclaw-app` — channels and providers registered in logs
4. `plugins/` directory is absent or empty
5. `base/` directory is absent (renamed to `javaclaw-core/`)
6. Zero imports of `org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor` in project
7. Zero imports of `ai.javaclaw.onboarding` in project
8. `ChatHtml` and `Htmx` exist in `javaclaw-api-chat` module with `@Deprecated(forRemoval = true)`. `OnboardingController` not found anywhere in project.
9. Zero references to `javaclaw-plugin-brave` or `javaclaw-plugin-playwright` in pom.xml files
10. `javaclaw-app/src/main/java/` contains only `JavaClawApplication` and optionally `IndexController`
11. `javaclaw-app/src/main/resources/templates/` contains ONLY `chat.html.peb` and `base.html.peb` (both marked @Deprecated). No `onboarding/` subdirectory.
12. `javaclaw-core/pom.xml` artifactId = `javaclaw-core`
13. `mvn dependency:tree -pl javaclaw-core` shows no dependencies on other project modules
14. `mvn dependency:tree -pl javaclaw-api/javaclaw-api-chat` shows dependency on javaclaw-core only (no other project modules)
15. WebSocket chat with agent works (functionality preserved)

## Validation Commands

Execute these commands to validate the task is complete:

- `mvn clean compile` — all modules compile
- `mvn test` — all tests pass
- `mvn spring-boot:run -pl javaclaw-app` — app starts (check logs for channel/provider registration)
- `grep -r "org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor" --include="*.java" .` — expect 0 results
- `grep -r "ai.javaclaw.onboarding" --include="*.java" .` — expect 0 results
- `grep -r "OnboardingController" --include="*.java" .` — expect 0 results
- `grep -r "@Deprecated" javaclaw-api/javaclaw-api-chat/src/main/java/ai/javaclaw/api/chat/ChatHtml.java` — expect match (marked deprecated)
- `grep -r "@Deprecated" javaclaw-api/javaclaw-api-chat/src/main/java/ai/javaclaw/api/chat/Htmx.java` — expect match (marked deprecated)
- `grep -r "javaclaw-plugin-brave\|javaclaw-plugin-playwright" --include="pom.xml" .` — expect 0 results
- `grep -r "javaclaw-base" --include="pom.xml" .` — expect 0 results (all replaced with javaclaw-core)
- `grep -r "pebble" --include="pom.xml" javaclaw-app/pom.xml` — expect 1 result (pebble-spring-boot-starter kept, marked @Deprecated in plan)
- `mvn dependency:tree -pl javaclaw-core | grep "ai.javaclaw"` — only javaclaw-core itself
- `mvn dependency:tree -pl javaclaw-api/javaclaw-api-chat | grep "ai.javaclaw"` — only javaclaw-core and javaclaw-api-chat
- `ls -d base/ 2>/dev/null` — expect "No such file or directory"
- `ls -d plugins/ 2>/dev/null` — expect "No such file or directory" or empty
- `ls javaclaw-app/src/main/resources/templates/ 2>/dev/null` — expect only `chat.html.peb` and `base.html.peb`
- `ls javaclaw-app/src/main/resources/templates/onboarding/ 2>/dev/null` — expect "No such file or directory"
- `find javaclaw-core/src -path "*/org/springframework/*" 2>/dev/null` — expect 0 results

## Notes

- **Commit strategy**: Follow spec section 4.3.2 — sequential commits with intermediate `mvn clean compile` checks. Recommended commits:
  1. Commit after task 1: "refactor: rename base module to javaclaw-core"
  2. Commit after task 2: "refactor: create Spring AI wrappers, remove onboarding from javaclaw-core, move migrations"
  3. Commit after tasks 3-7: "refactor: extract javaclaw-api-chat, create javaclaw-api-admin, move channels to javaclaw-channel, move providers to javaclaw-provider, remove legacy plugins"
  4. Commit after task 8: "refactor: clean javaclaw-app module, update all POMs with 3-layer aggregator structure"
  5. Commit after task 9: "refactor: move and adapt tests"
- **Spring AI hack wrappers**: The wrapper approach preserves the customized behavior (dedup messages, appendable repository pattern) that upstream Spring AI doesn't support. When Spring AI 2.0 GA addresses these, wrappers can be removed.
- **Empty provider modules**: google, ollama, openai become thin wrappers (pom.xml only). This is intentional — they provide uniform structure for future customization (fallback chains, token extractors, custom model options).
- **No new libraries needed**: All dependencies already exist in the project.
- **Risk**: If `AgentProvider` is used broadly and the constructor signature change breaks other code, check all `AgentProvider` instantiation/injection sites.
- **Pebble/htmx deprecation decision**: The spec (2.1.6.1) lists `ChatHtml` and `Htmx` for removal, but plan review revealed they are actively used by `ChatChannel` and `ChatWebSocketHandler` (~20+ references). Removing them breaks runtime, violating spec rule 2.10. Decision: move to `api/chat` with `@Deprecated(forRemoval = true)`, keep `chat.html.peb`/`base.html.peb` in app templates with deprecation comment, retain `pebble-spring-boot-starter`. Full Pebble removal deferred to separate future task (rewrite chat UI to REST/SPA).

