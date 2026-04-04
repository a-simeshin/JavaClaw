# Plan: JavaClaw Module Restructure

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

1. **Rename base → core** with dependency updates + create Spring AI wrapper classes in `ai.javaclaw` namespace + remove onboarding interfaces + move Flyway migrations to app
2. **Extract api/chat module** from app's chat classes
3. **Create api/admin scaffold** (empty module for future Phase 3)
4. **Move channels** from plugins/ to top-level channels/
5. **Update providers** — remove onboarding classes, update deps to javaclaw-core
6. **Remove legacy plugins** (brave, playwright)
7. **Clean app module** — remove onboarding/htmx/pebble, update all POMs, fix IndexController
8. **Adapt/move tests** — relocate chat tests to api/chat, remove onboarding tests
9. **Full validation** — compile, test, startup check

## Relevant Files

### Existing Files (to modify or move)

- **`pom.xml`** (root) — modules list, needs full rewrite of `<modules>` section
- **`base/pom.xml`** — artifactId `javaclaw-base` → rename to `javaclaw-core`
- **`app/pom.xml`** — remove legacy deps, add api/channel modules
- **`base/src/main/java/ai/javaclaw/JavaClawConfiguration.java`** — imports Spring AI hacks (MessageChatMemoryAdvisor, MessageWindowChatMemory); must update to use wrappers
- **`base/src/main/java/ai/javaclaw/providers/AgentProvider.java`** — imports `AgentOnboardingProvider`; must remove onboarding dependency
- **`base/src/main/java/ai/javaclaw/agent/memory/FileSystemChatMemoryRepository.java`** — implements `AppendableChatMemoryRepository` from hack package; must use wrapper
- **`base/src/main/java/ai/javaclaw/agent/memory/JdbcAppendableChatMemoryRepository.java`** — implements `AppendableChatMemoryRepository`; must use wrapper
- **`base/src/main/java/org/springframework/ai/chat/client/advisor/MessageChatMemoryAdvisor.java`** — Spring AI hack to remove
- **`base/src/main/java/org/springframework/ai/chat/memory/AppendableChatMemoryRepository.java`** — Spring AI hack interface to remove
- **`base/src/main/java/org/springframework/ai/chat/memory/MessageWindowChatMemory.java`** — Spring AI hack to remove
- **`base/src/main/java/ai/javaclaw/onboarding/`** — 3 files to remove (OnboardingProvider, AgentOnboardingProvider, AgentOnboardingProviders)
- **`base/src/main/resources/db/migration/`** — 3 SQL files to move to app
- **`app/src/main/java/ai/javaclaw/chat/ChatChannel.java`** — move to api/chat
- **`app/src/main/java/ai/javaclaw/chat/api/ChatController.java`** — move to api/chat
- **`app/src/main/java/ai/javaclaw/chat/ws/ChatWebSocketHandler.java`** — move to api/chat
- **`app/src/main/java/ai/javaclaw/chat/ws/WebSocketConfig.java`** — move to api/chat
- **`app/src/main/java/ai/javaclaw/chat/ChatHtml.java`** — move to api/chat, add `@Deprecated` (будет переписан при миграции с Pebble)
- **`app/src/main/java/ai/javaclaw/chat/Htmx.java`** — move to api/chat, add `@Deprecated` (будет переписан при миграции с Pebble)
- **`app/src/main/java/ai/javaclaw/onboarding/`** — 8 files to remove
- **`app/src/main/java/ai/javaclaw/api/IndexController.java`** — rework to remove onboarding redirect
- **`app/src/main/resources/templates/`** — remove only `onboarding/` subdirectory; keep `chat.html.peb` and `base.html.peb` (add `{# @Deprecated #}` comment — будут переписаны при миграции с Pebble)
- **`plugins/discord/`** — move to `channels/discord/`, remove DiscordOnboardingProvider
- **`plugins/telegram/`** — move to `channels/telegram/`, remove TelegramOnboardingProvider
- **`plugins/brave/`** — delete entirely
- **`plugins/playwright/`** — delete entirely
- **`providers/anthropic/pom.xml`** — update javaclaw-base → javaclaw-core
- **`providers/google/pom.xml`** — update javaclaw-base → javaclaw-core
- **`providers/ollama/pom.xml`** — update javaclaw-base → javaclaw-core
- **`providers/openai/pom.xml`** — update javaclaw-base → javaclaw-core
- **`providers/anthropic/.../AnthropicAgentOnboardingProvider.java`** — remove
- **`providers/google/.../GoogleGenAIAgentOnboardingProvider.java`** — remove
- **`providers/ollama/.../OllamaAgentOnboardingProvider.java`** — remove
- **`providers/openai/.../OpenAIAgentOnboardingProvider.java`** — remove

### Existing Test Files (to move, adapt, or remove)

- **`base/src/test/.../MessageWindowChatMemoryTest.java`** — remove (tests removed hack class)
- **`app/src/test/.../chat/ChatChannelTest.java`** — move to api/chat/src/test/
- **`app/src/test/.../chat/ws/ChatWebSocketHandlerTest.java`** — move to api/chat/src/test/
- **`app/src/test/.../api/OnboardingControllerTest.java`** — remove
- **`app/src/test/.../e2e/OnboardingE2ETest.java`** — remove
- **`plugins/discord/src/test/.../DiscordOnboardingProviderTest.java`** — remove (moves with dir, then delete)
- **`plugins/brave/src/test/`** — deleted with module
- **`plugins/playwright/src/test/`** — deleted with module

### New Files

- **`core/pom.xml`** — renamed from base/pom.xml with updated artifactId
- **`core/src/main/java/ai/javaclaw/ai/advisor/JavaClawMessageChatMemoryAdvisor.java`** — wrapper for Spring AI hack
- **`core/src/main/java/ai/javaclaw/ai/memory/JavaClawMessageWindowChatMemory.java`** — wrapper for Spring AI hack
- **`core/src/main/java/ai/javaclaw/ai/memory/AppendableChatMemoryRepository.java`** — wrapper interface for Spring AI hack
- **`api/chat/pom.xml`** — new module javaclaw-api-chat
- **`api/chat/src/main/java/ai/javaclaw/api/chat/ChatChannel.java`** — moved from app
- **`api/chat/src/main/java/ai/javaclaw/api/chat/ChatController.java`** — moved from app
- **`api/chat/src/main/java/ai/javaclaw/api/chat/ChatHtml.java`** — moved from app, marked `@Deprecated`
- **`api/chat/src/main/java/ai/javaclaw/api/chat/Htmx.java`** — moved from app, marked `@Deprecated`
- **`api/chat/src/main/java/ai/javaclaw/api/chat/ws/ChatWebSocketHandler.java`** — moved from app
- **`api/chat/src/main/java/ai/javaclaw/api/chat/ws/WebSocketConfig.java`** — moved from app
- **`api/admin/pom.xml`** — scaffold module javaclaw-api-admin
- **`channels/discord/pom.xml`** — moved from plugins/discord, updated artifactId
- **`channels/telegram/pom.xml`** — moved from plugins/telegram, updated artifactId
- **`app/src/main/resources/db/migration/V1__init_tasks.sql`** — moved from core
- **`app/src/main/resources/db/migration/V2__init_chat_memory.sql`** — moved from core
- **`app/src/main/resources/db/migration/V3__allow_null_content_in_chat_memory.sql`** — moved from core

## Implementation Phases

### Phase 1: Foundation (Tasks 1-2)

Rename `base/` to `core/`, update all POM references, create Spring AI wrapper classes in the project's own namespace (`ai.javaclaw.ai.*`), update all internal imports, remove original hack classes, remove onboarding interfaces from core, adapt `AgentProvider` to work without onboarding, move Flyway migrations to `app/`. Each step MUST compile.

**Critical detail — Spring AI wrappers**: The 3 hack classes in `org.springframework.ai.chat.*` are used within core itself:
- `MessageChatMemoryAdvisor` → used in `JavaClawConfiguration.java`
- `MessageWindowChatMemory` → used in `JavaClawConfiguration.java`
- `AppendableChatMemoryRepository` → implemented by `FileSystemChatMemoryRepository` and `JdbcAppendableChatMemoryRepository`

Per rule 2.3 #5: create replacements in `ai.javaclaw.ai.*` namespace FIRST, update imports, THEN remove originals.

**Critical detail — AgentProvider**: `AgentProvider.java` has a `SequencedCollection<AgentOnboardingProvider>` field. After removing onboarding, this field and its constructor parameter must be removed.

### Phase 2: Module Extraction (Tasks 3-7)

Create new modules (`api/chat`, `api/admin`), move channels from `plugins/` to `channels/`, update providers to depend on `javaclaw-core` and remove onboarding classes, delete legacy plugins (Brave, Playwright). Tasks 3-7 can run in parallel as they touch independent directories.

**Note on parallelism**: Tasks 3-7 each create/modify their OWN pom.xml only. They do NOT touch root `pom.xml` or `app/pom.xml` — that's deferred to Task 8 to avoid merge conflicts.

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
  - `ChatChannelTest` → move to `api/chat/src/test/java/ai/javaclaw/api/chat/`
  - `ChatWebSocketHandlerTest` → move to `api/chat/src/test/java/ai/javaclaw/api/chat/ws/`
  - `DiscordChannelTest` → stays in `channels/discord/src/test/` (moved with directory)
  - `TelegramChannelTest` → stays in `channels/telegram/src/test/` (moved with directory)
  - `JdbcAppendableChatMemoryRepositoryTest` → stays in `core/src/test/`, update imports to wrapper
  - `FileSystemChatMemoryRepositoryTest` → stays in `core/src/test/`, verify imports compile with wrapper
  - `AnthropicClaudeCodeBackendTest` → stays in `providers/anthropic/src/test/`
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
- `mvn dependency:tree -pl core` — core has NO dependencies on other project modules
- `mvn dependency:tree -pl api/chat` — api/chat depends only on javaclaw-core

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
- **Startup verification**: `mvn spring-boot:run -pl app` starts successfully

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
- Rename directory `base/` → `core/` using `git mv base core`
- In `core/pom.xml`: change `<artifactId>javaclaw-base</artifactId>` → `<artifactId>javaclaw-core</artifactId>`
- In root `pom.xml`: change `<module>base</module>` → `<module>core</module>`
- In `app/pom.xml`: change `javaclaw-base` → `javaclaw-core` in dependency
- In all `plugins/*/pom.xml`: change `javaclaw-base` → `javaclaw-core`
- In all `providers/*/pom.xml`: change `javaclaw-base` → `javaclaw-core`
- Run `mvn clean compile -pl core` to verify core compiles
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
  - Create `core/src/main/java/ai/javaclaw/ai/memory/AppendableChatMemoryRepository.java` — interface extending `ChatMemoryRepository` with `append()` method (copy contract from hack, use `ai.javaclaw.ai.memory` package)
  - Create `core/src/main/java/ai/javaclaw/ai/memory/JavaClawMessageWindowChatMemory.java` — copy logic from hack `MessageWindowChatMemory` into `ai.javaclaw.ai.memory` package, referencing new `AppendableChatMemoryRepository`
  - Create `core/src/main/java/ai/javaclaw/ai/advisor/JavaClawMessageChatMemoryAdvisor.java` — copy logic from hack `MessageChatMemoryAdvisor` into `ai.javaclaw.ai.advisor` package
- **Step 2b: Update all internal imports** within core:
  - `JavaClawConfiguration.java`: replace `org.springframework.ai.chat.memory.MessageWindowChatMemory` → `ai.javaclaw.ai.memory.JavaClawMessageWindowChatMemory`
  - `JavaClawConfiguration.java`: replace `org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor` → `ai.javaclaw.ai.advisor.JavaClawMessageChatMemoryAdvisor`
  - `FileSystemChatMemoryRepository.java`: replace `org.springframework.ai.chat.memory.AppendableChatMemoryRepository` → `ai.javaclaw.ai.memory.AppendableChatMemoryRepository`
  - `JdbcAppendableChatMemoryRepository.java`: replace same import
  - `JdbcAppendableChatMemoryRepositoryTest.java`: update import if needed
- **Step 2c: Remove original hack classes**:
  - Delete `core/src/main/java/org/springframework/ai/chat/client/advisor/MessageChatMemoryAdvisor.java`
  - Delete `core/src/main/java/org/springframework/ai/chat/memory/AppendableChatMemoryRepository.java`
  - Delete `core/src/main/java/org/springframework/ai/chat/memory/MessageWindowChatMemory.java`
  - Delete `core/src/test/java/org/springframework/ai/chat/memory/MessageWindowChatMemoryTest.java`
  - Delete empty `org/springframework/` directory tree from core
- **Step 2d: Remove onboarding from core**:
  - Delete `core/src/main/java/ai/javaclaw/onboarding/OnboardingProvider.java`
  - Delete `core/src/main/java/ai/javaclaw/onboarding/AgentOnboardingProvider.java`
  - Delete `core/src/main/java/ai/javaclaw/onboarding/AgentOnboardingProviders.java`
  - Delete `core/src/main/java/ai/javaclaw/onboarding/` directory
  - Update `core/src/main/java/ai/javaclaw/providers/AgentProvider.java`: remove `AgentOnboardingProvider` import, remove `SequencedCollection<AgentOnboardingProvider> agentOnboardingProviders` field and constructor parameter. Verify no other references.
- **Step 2e: Move Flyway migrations**:
  - Create `app/src/main/resources/db/migration/` directory
  - Move `core/src/main/resources/db/migration/V1__init_tasks.sql` → `app/src/main/resources/db/migration/`
  - Move `core/src/main/resources/db/migration/V2__init_chat_memory.sql` → `app/src/main/resources/db/migration/`
  - Move `core/src/main/resources/db/migration/V3__allow_null_content_in_chat_memory.sql` → `app/src/main/resources/db/migration/`
  - Delete empty `core/src/main/resources/db/` directory
- Run `mvn clean compile` to verify everything compiles

### 3. Create api/chat module

- **Task ID**: create-api-chat
- **Depends On**: prepare-core
- **Assigned To**: builder-modules
- **Agent Type**: builder
- **Stack**: Java Spring Boot controller Spring MVC WebSocket maven
- **Parallel**: true (with tasks 4, 5, 6)
- **Tests**: Unit: ChatChannelTest, ChatWebSocketHandlerTest moved and compiling. Integration: module compiles standalone.
- Create directory structure: `api/chat/src/main/java/ai/javaclaw/api/chat/` and `api/chat/src/main/java/ai/javaclaw/api/chat/ws/` and `api/chat/src/test/java/`
- Create `api/chat/pom.xml` with:
  - Parent: javaclaw-parent
  - ArtifactId: `javaclaw-api-chat`
  - Dependencies: javaclaw-core, spring-boot-starter-webmvc, spring-boot-starter-websocket
- Move classes from `app/src/main/java/` to `api/chat/src/main/java/` with package updates:
  - `ai.javaclaw.chat.ChatChannel` → `ai.javaclaw.api.chat.ChatChannel` (update package declaration)
  - `ai.javaclaw.chat.api.ChatController` → `ai.javaclaw.api.chat.ChatController` (update package + imports)
  - `ai.javaclaw.chat.ChatHtml` → `ai.javaclaw.api.chat.ChatHtml` (update package, add `@Deprecated(forRemoval = true)` annotation with Javadoc: "Scheduled for removal — will be replaced when migrating from Pebble to REST/SPA")
  - `ai.javaclaw.chat.Htmx` → `ai.javaclaw.api.chat.Htmx` (update package, add `@Deprecated(forRemoval = true)` with same Javadoc)
  - `ai.javaclaw.chat.ws.ChatWebSocketHandler` → `ai.javaclaw.api.chat.ws.ChatWebSocketHandler`
  - `ai.javaclaw.chat.ws.WebSocketConfig` → `ai.javaclaw.api.chat.ws.WebSocketConfig`
- Update imports of `ChatHtml` and `Htmx` in `ChatChannel` and `ChatWebSocketHandler` to new package `ai.javaclaw.api.chat`
- Move tests to `api/chat/src/test/java/` with package updates:
  - `app/.../chat/ChatChannelTest.java` → `api/chat/src/test/java/ai/javaclaw/api/chat/ChatChannelTest.java`
  - `app/.../chat/ws/ChatWebSocketHandlerTest.java` → `api/chat/src/test/java/ai/javaclaw/api/chat/ws/ChatWebSocketHandlerTest.java`
- Add test dependencies to `api/chat/pom.xml` if needed (spring-boot-starter-test)
- Do NOT update root pom.xml or app/pom.xml yet (deferred to task 8)
- Verify: `mvn clean compile -f api/chat/pom.xml` (root pom not yet updated, compile from module directly)

### 4. Create api/admin scaffold

- **Task ID**: create-api-admin
- **Depends On**: prepare-core
- **Assigned To**: builder-modules
- **Agent Type**: builder
- **Stack**: Java Spring Boot maven
- **Parallel**: true (with tasks 3, 5, 6)
- **Tests**: None (scaffold only)
- Create directory: `api/admin/`
- Create `api/admin/pom.xml` with:
  - Parent: javaclaw-parent
  - ArtifactId: `javaclaw-api-admin`
  - Dependencies: javaclaw-core, spring-boot-starter-webmvc
- No source files — scaffold only
- Do NOT update root pom.xml yet

### 5. Move channels from plugins to channels

- **Task ID**: move-channels
- **Depends On**: prepare-core
- **Assigned To**: builder-modules
- **Agent Type**: builder
- **Stack**: Java Spring Boot maven autoconfiguration
- **Parallel**: true (with tasks 3, 4, 6)
- **Tests**: Unit: DiscordChannelTest, TelegramChannelTest preserved. Integration: modules compile standalone.
- Move directories using `git mv`:
  - `git mv plugins/discord channels/discord`
  - `git mv plugins/telegram channels/telegram`
- **Discord module updates** (`channels/discord/`):
  - `pom.xml`: change artifactId `javaclaw-plugin-discord` → `javaclaw-channel-discord`, verify dependency is `javaclaw-core` (updated in task 1)
  - Delete `DiscordOnboardingProvider.java` and its test `DiscordOnboardingProviderTest.java`
  - Delete onboarding template: `src/main/resources/templates/onboarding/steps/discord.html.peb` and empty templates dirs
  - Update `DiscordChannelAutoConfiguration.java`: remove any `@Bean` method for `DiscordOnboardingProvider`
  - Verify `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` only lists `DiscordChannelAutoConfiguration`
- **Telegram module updates** (`channels/telegram/`):
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
- **Anthropic provider** (`providers/anthropic/`):
  - Verify pom.xml has `javaclaw-core` (updated in task 1)
  - Delete `AnthropicAgentOnboardingProvider.java`
  - Verify remaining files: `AnthropicClaudeCodeBackend.java`, `AnthropicClaudeCodeOAuthTokenExtractor.java`, `AnthropticClaudeCodeConfiguration.java` — module is NOT empty
- **Google provider** (`providers/google/`):
  - Delete `GoogleGenAIAgentOnboardingProvider.java`
  - Module becomes thin wrapper (pom.xml with `spring-ai-starter-model-google-genai` only) — this is expected per spec 2.1.5.4
- **Ollama provider** (`providers/ollama/`):
  - Delete `OllamaAgentOnboardingProvider.java`
  - Module becomes thin wrapper (pom.xml with `spring-ai-starter-model-ollama`)
- **OpenAI provider** (`providers/openai/`):
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
- **Step 8a: Remove legacy code from app**:
  - Delete entire `app/src/main/java/ai/javaclaw/onboarding/` directory (OnboardingController, S1-S6 steps)
  - Delete remaining empty `app/src/main/java/ai/javaclaw/chat/` directory (all classes including ChatHtml and Htmx already moved to api/chat in task 3)
  - Delete `app/src/main/resources/templates/onboarding/` directory (onboarding templates only)
  - Keep `app/src/main/resources/templates/chat.html.peb` and `app/src/main/resources/templates/base.html.peb` — add `{# @Deprecated: scheduled for removal when migrating from Pebble to REST/SPA #}` comment at top of each file
- **Step 8b: Rework IndexController**:
  - Remove onboarding redirect logic
  - Remove `agent.onboarding.completed` property reference
  - Simplify: `GET /` and `GET /index` redirect to `/chat`
  - Remove unused imports
- **Step 8c: Update root pom.xml `<modules>` section**:
  - Remove: `base`, `plugins/discord`, `plugins/telegram`, `plugins/playwright`, `plugins/brave`
  - Add: `core`, `api/chat`, `api/admin`, `channels/discord`, `channels/telegram`
  - Keep: `providers/anthropic`, `providers/google`, `providers/ollama`, `providers/openai`, `app`
  - Final modules list (order matters — dependencies first):

    ```
    core, api/chat, api/admin, channels/discord, channels/telegram,
    providers/anthropic, providers/google, providers/ollama, providers/openai, app
    ```
- **Step 8d: Update app/pom.xml dependencies**:
  - Remove: `javaclaw-plugin-discord`, `javaclaw-plugin-telegram`, `javaclaw-plugin-playwright`, `javaclaw-plugin-brave`
  - Add: `javaclaw-api-chat`, `javaclaw-api-admin`, `javaclaw-channel-discord`, `javaclaw-channel-telegram`
  - Keep: all provider dependencies (`javaclaw-provider-*`), `javaclaw-core` (already updated in task 1)
  - Keep: `pebble-spring-boot-starter` (still needed for chat.html.peb and base.html.peb; marked @Deprecated, will be removed when migrating to REST/SPA)
  - Remove: `spring-boot-starter-websocket` (now in api/chat module)
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
- **Remove onboarding tests from app**:
  - Delete `app/src/test/java/ai/javaclaw/api/OnboardingControllerTest.java`
  - Delete `app/src/test/java/ai/javaclaw/e2e/OnboardingE2ETest.java`
- **Adapt e2e test bases** (if they reference onboarding):
  - Check `app/src/test/java/ai/javaclaw/e2e/E2ETestBase.java` for onboarding imports/references — remove if found
  - Check `app/src/test/java/ai/javaclaw/e2e/ChatReadyE2ETestBase.java` — remove onboarding refs if found (e.g., Javadoc mentioning "onboarding to already be completed", `agent.onboarding.completed` property)
  - Check `app/src/test/resources/application-e2e.yaml` (or similar profile YAML) for `agent.onboarding.completed` property — remove if found
- **Verify core tests compile after wrapper migration**:
  - Check `FileSystemChatMemoryRepositoryTest` in `core/src/test/` — verify imports use wrapper `ai.javaclaw.ai.memory.AppendableChatMemoryRepository` if needed
- **Update package references in moved tests** (already partially done in task 3, verify):
  - Verify `ChatChannelTest` imports match new package `ai.javaclaw.api.chat`
  - Verify `ChatWebSocketHandlerTest` imports match new package
- **Add test dependencies to api/chat/pom.xml** if not already present:
  - `spring-boot-starter-test` (test scope)
  - Any mocking/assertion libraries used by the moved tests
- **Verify all provider tests compile** after onboarding removal (AnthropicClaudeCodeBackendTest)
- Run `mvn test` — all tests MUST pass (some may be skipped if they require running DB/containers)
- Run `mvn test -pl core` — core unit tests pass
- Run `mvn test -pl api/chat` — chat tests pass

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
  3. `mvn spring-boot:run -pl app` — successful startup (verify in logs: channels and providers registered)
  4. Verify `plugins/` directory absent or empty
  5. Verify `base/` directory absent
  6. `grep -r "org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor" --include="*.java"` — zero results
  7. `grep -r "ai.javaclaw.onboarding" --include="*.java"` — zero results
  8. Verify `ChatHtml` and `Htmx` exist in `api/chat` with `@Deprecated(forRemoval = true)`. Verify `OnboardingController` not found anywhere.
  9. `grep -r "javaclaw-plugin-brave\|javaclaw-plugin-playwright" --include="pom.xml"` — zero results
  10. Verify `app/src/main/java/` contains only `JavaClawApplication` and `IndexController` (in `api/` subpackage)
  11. Verify `app/src/main/resources/templates/` contains ONLY `chat.html.peb` and `base.html.peb` (both marked @Deprecated). No `onboarding/` subdirectory.
  12. Verify `core/pom.xml` has artifactId = `javaclaw-core`
  13. `mvn dependency:tree -pl core` — no dependencies on other project modules
  14. `mvn dependency:tree -pl api/chat` — depends on javaclaw-core only
  15. WebSocket chat test (if feasible in CI) — agent responds
- **Structural checks**:
  - Verify module dependency graph is unidirectional (core has no project deps, api/channels/providers depend only on core)
  - Verify no cross-layer dependencies (api/chat does NOT depend on api/admin, channels/discord does NOT depend on channels/telegram)
  - Verify no imports of removed packages anywhere
- **Report**: pass/fail with detailed findings

## Acceptance Criteria

1. `mvn clean compile` executes successfully from project root — all modules compile
2. `mvn test` executes successfully — all tests pass (except tests of removed components, which are deleted)
3. Application starts via `mvn spring-boot:run -pl app` — channels and providers registered in logs
4. `plugins/` directory is absent or empty
5. `base/` directory is absent (renamed to `core/`)
6. Zero imports of `org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor` in project
7. Zero imports of `ai.javaclaw.onboarding` in project
8. `ChatHtml` and `Htmx` exist in `api/chat` module with `@Deprecated(forRemoval = true)`. `OnboardingController` not found anywhere in project.
9. Zero references to `javaclaw-plugin-brave` or `javaclaw-plugin-playwright` in pom.xml files
10. `app/src/main/java/` contains only `JavaClawApplication` and optionally `IndexController`
11. `app/src/main/resources/templates/` contains ONLY `chat.html.peb` and `base.html.peb` (both marked @Deprecated). No `onboarding/` subdirectory.
12. `core/pom.xml` artifactId = `javaclaw-core`
13. `mvn dependency:tree -pl core` shows no dependencies on other project modules
14. `mvn dependency:tree -pl api/chat` shows dependency on javaclaw-core only (no other project modules)
15. WebSocket chat with agent works (functionality preserved)

## Validation Commands

Execute these commands to validate the task is complete:

- `mvn clean compile` — all modules compile
- `mvn test` — all tests pass
- `mvn spring-boot:run -pl app` — app starts (check logs for channel/provider registration)
- `grep -r "org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor" --include="*.java" .` — expect 0 results
- `grep -r "ai.javaclaw.onboarding" --include="*.java" .` — expect 0 results
- `grep -r "OnboardingController" --include="*.java" .` — expect 0 results
- `grep -r "@Deprecated" api/chat/src/main/java/ai/javaclaw/api/chat/ChatHtml.java` — expect match (marked deprecated)
- `grep -r "@Deprecated" api/chat/src/main/java/ai/javaclaw/api/chat/Htmx.java` — expect match (marked deprecated)
- `grep -r "javaclaw-plugin-brave\|javaclaw-plugin-playwright" --include="pom.xml" .` — expect 0 results
- `grep -r "javaclaw-base" --include="pom.xml" .` — expect 0 results (all replaced with javaclaw-core)
- `grep -r "pebble" --include="pom.xml" app/pom.xml` — expect 1 result (pebble-spring-boot-starter kept, marked @Deprecated in plan)
- `mvn dependency:tree -pl core | grep "ai.javaclaw"` — only javaclaw-core itself
- `mvn dependency:tree -pl api/chat | grep "ai.javaclaw"` — only javaclaw-core and javaclaw-api-chat
- `ls -d base/ 2>/dev/null` — expect "No such file or directory"
- `ls -d plugins/ 2>/dev/null` — expect "No such file or directory" or empty
- `ls app/src/main/resources/templates/ 2>/dev/null` — expect only `chat.html.peb` and `base.html.peb`
- `ls app/src/main/resources/templates/onboarding/ 2>/dev/null` — expect "No such file or directory"
- `find core/src -path "*/org/springframework/*" 2>/dev/null` — expect 0 results

## Notes

- **Commit strategy**: Follow spec section 4.3.2 — sequential commits with intermediate `mvn clean compile` checks. Recommended commits:
  1. Commit after task 1: "refactor: rename base module to core"
  2. Commit after task 2: "refactor: create Spring AI wrappers, remove onboarding from core, move migrations"
  3. Commit after tasks 3-7: "refactor: extract api/chat, create api/admin, move channels, update providers, remove legacy plugins"
  4. Commit after task 8: "refactor: clean app module, update all POMs"
  5. Commit after task 9: "refactor: move and adapt tests"
- **Spring AI hack wrappers**: The wrapper approach preserves the customized behavior (dedup messages, appendable repository pattern) that upstream Spring AI doesn't support. When Spring AI 2.0 GA addresses these, wrappers can be removed.
- **Empty provider modules**: google, ollama, openai become thin wrappers (pom.xml only). This is intentional — they provide uniform structure for future customization (fallback chains, token extractors, custom model options).
- **No new libraries needed**: All dependencies already exist in the project.
- **Risk**: If `AgentProvider` is used broadly and the constructor signature change breaks other code, check all `AgentProvider` instantiation/injection sites.
- **Pebble/htmx deprecation decision**: The spec (2.1.6.1) lists `ChatHtml` and `Htmx` for removal, but plan review revealed they are actively used by `ChatChannel` and `ChatWebSocketHandler` (~20+ references). Removing them breaks runtime, violating spec rule 2.10. Decision: move to `api/chat` with `@Deprecated(forRemoval = true)`, keep `chat.html.peb`/`base.html.peb` in app templates with deprecation comment, retain `pebble-spring-boot-starter`. Full Pebble removal deferred to separate future task (rewrite chat UI to REST/SPA).

