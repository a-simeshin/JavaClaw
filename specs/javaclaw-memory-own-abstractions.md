# javaclaw-memory: Replace Spring AI Memory with Own Abstractions

**Goal**: Remove dependency on `org.springframework.ai.chat.memory.*` interfaces.
Full control over chat memory contract — own interfaces, own message representation,
zero Spring AI coupling in javaclaw-memory module.

**Non-goal**: Replace Spring AI `ChatModel`, `Prompt`, `ChatResponse` etc. Those stay.

---

## Current State

### Dependencies (javaclaw-memory/pom.xml)

- `spring-ai-client-chat` — provides `ChatMemory`, `ChatMemoryRepository`, `Message` hierarchy
- `spring-boot-starter-data-jdbc` — stays as-is

### Files in javaclaw-memory/src/main/java

|                           File                            |                       Role                        |                        Spring AI coupling                        |
|-----------------------------------------------------------|---------------------------------------------------|------------------------------------------------------------------|
| `ai.javaclaw.ai.memory.JavaClawMessageWindowChatMemory`   | implements `o.s.ai.chat.memory.ChatMemory`        | **HIGH** — implements interface, uses `Message`, `SystemMessage` |
| `ai.javaclaw.ai.memory.AppendableChatMemoryRepository`    | extends `o.s.ai.chat.memory.ChatMemoryRepository` | **HIGH** — extends interface, uses `Message`                     |
| `ai.javaclaw.agent.memory.SpringDataChatMemoryRepository` | implements `AppendableChatMemoryRepository`       | **HIGH** — uses `Message`, `MessageType`, all subtypes           |
| `ai.javaclaw.agent.memory.ChatMemoryEntry`                | Spring Data JDBC entity                           | **LOW** — `type` field stores `MessageType.name()` as string     |
| `ai.javaclaw.agent.memory.ChatMemoryEntryRepository`      | Spring Data JDBC repo                             | **NONE**                                                         |

### Consumers outside javaclaw-memory (prod code)

|             File              |        Uses `ChatMemory`        | Uses `ChatMemoryRepository` / `AppendableChatMemoryRepository` |                                Uses `Message` types                                |
|-------------------------------|---------------------------------|----------------------------------------------------------------|------------------------------------------------------------------------------------|
| `ChatService`                 | `add()` for user+assistant msgs | —                                                              | `UserMessage`, `AssistantMessage`                                                  |
| `SseStreamingService`         | `add()` for assistant persist   | —                                                              | `AssistantMessage`                                                                 |
| `MessageAssembler`            | field (unused for reads)        | `findByConversationId()` via `ChatMemoryRepository`            | `Message`, `SystemMessage`, `UserMessage`                                          |
| `ConversationController`      | —                               | `deleteByConversationId()` via `ChatMemoryRepository`          | —                                                                                  |
| `JavaClawConfiguration`       | `@Bean ChatMemory`              | `@Bean` param `ChatMemoryRepository`                           | —                                                                                  |
| `ChatServiceConfiguration`    | constructor param               | —                                                              | —                                                                                  |
| **`DeliveryService`**         | —                               | **`AppendableChatMemoryRepository.appendAll()`**               | `AssistantMessage`                                                                 |
| **`ChatChannel`**             | —                               | **`AppendableChatMemoryRepository.appendAll()`**               | `AssistantMessage`                                                                 |
| **`MessageTool`**             | —                               | **`AppendableChatMemoryRepository.appendAll()`**               | `AssistantMessage`                                                                 |
| **`AgentToolsConfiguration`** | —                               | **passes `AppendableChatMemoryRepository` to `MessageTool`**   | —                                                                                  |
| `ChatYamlSerializer`          | —                               | —                                                              | `Message`, `MessageType`, `UserMessage`, `AssistantMessage`, `SystemMessage`       |
| `ChatAuditService`            | —                               | —                                                              | `AssistantMessage`, `Message`, `SystemMessage`                                     |
| `MessageSanitizer`            | —                               | —                                                              | `Message`, `MessageType`, `AssistantMessage`, `ToolResponseMessage`, `UserMessage` |
| `AssembledPrompt`             | —                               | —                                                              | `Message`, `SystemMessage`, `UserMessage`                                          |
| `TokenEstimator`              | —                               | —                                                              | `Message`                                                                          |
| `TurnBoundaryWindower`        | —                               | —                                                              | `Message`, `UserMessage`                                                           |
| `WindowingResult`             | —                               | —                                                              | `Message`                                                                          |
| `ConversationSummaryService`  | —                               | —                                                              | `Message`, `AssistantMessage`, `SystemMessage`, `UserMessage`                      |

---

## Architecture Decision

### What we replace

1. `o.s.ai.chat.memory.ChatMemory` → own `ai.javaclaw.agent.memory.ChatMemory`
2. `o.s.ai.chat.memory.ChatMemoryRepository` → own `ai.javaclaw.agent.memory.ChatMemoryRepository`
3. `o.s.ai.chat.memory.InMemoryChatMemoryRepository` → own `InMemoryChatMemoryRepository`
4. `AppendableChatMemoryRepository` → own (no longer extends Spring AI)

### What we DON'T replace in this plan

- `Message` / `UserMessage` / `AssistantMessage` / `SystemMessage` / `ToolResponseMessage` / `MessageType` — these are the **chat model API**, used by `ChatModel`, `Prompt`, tool callbacks, etc. across 30+ files. Replacing them is a separate, bigger effort and they're NOT specific to memory.

### Consequence

- `spring-ai-client-chat` stays in javaclaw-memory/pom.xml **only** for `Message` types
- All `import o.s.ai.chat.memory.*` removed from entire codebase
- Own interfaces give full control: can add metadata, pagination, batch operations etc.

---

## Implementation Plan

> **Serena usage**: every step that reads/edits code should use Serena tools:
> - `find_symbol` + `include_body=true` to read exact methods before editing
> - `find_referencing_symbols` to find all callers before changing signatures
> - `get_symbols_overview` to verify file structure after changes
> - `replace_symbol_body` / `insert_before_symbol` / `insert_after_symbol` for precise edits
> - `search_for_pattern` for cross-codebase import/usage sweeps
> - `rename_symbol` when renaming interfaces/classes

### Phase 1: Define Own Interfaces (javaclaw-memory module)

**Step 1.1** — Create `ai.javaclaw.agent.memory.ChatMemoryRepository` (own interface)

```java
package ai.javaclaw.agent.memory;

import java.util.List;
import org.springframework.ai.chat.messages.Message;

/**
 * Repository for chat memory storage. Replaces Spring AI's ChatMemoryRepository
 * to give JavaClaw full control over the memory contract.
 */
public interface ChatMemoryRepository {
    List<String> findConversationIds();
    List<Message> findByConversationId(String conversationId);
    void saveAll(String conversationId, List<Message> messages);
    void deleteByConversationId(String conversationId);
}
```

- **Serena**: `get_symbols_overview` on existing `AppendableChatMemoryRepository.java` to verify method signatures before writing.

**Step 1.2** — Create `ai.javaclaw.agent.memory.AppendableChatMemoryRepository` (own interface)

```java
package ai.javaclaw.agent.memory;

import java.util.List;
import org.springframework.ai.chat.messages.Message;

public interface AppendableChatMemoryRepository extends ChatMemoryRepository {
    void appendAll(String conversationId, List<Message> messages);
}
```

- Move from `ai.javaclaw.ai.memory` package → `ai.javaclaw.agent.memory` package
- Delete old `ai.javaclaw.ai.memory.AppendableChatMemoryRepository`
- **Serena**: `find_referencing_symbols` on old `AppendableChatMemoryRepository` to find all imports to update

**Step 1.3** — Create `ai.javaclaw.agent.memory.ChatMemory` (own interface)

```java
package ai.javaclaw.agent.memory;

import java.util.List;
import org.springframework.ai.chat.messages.Message;

/**
 * High-level chat memory abstraction. Replaces Spring AI's ChatMemory
 * to give JavaClaw full control over windowing, eviction, and persistence.
 */
public interface ChatMemory {
    void add(String conversationId, List<Message> messages);
    List<Message> get(String conversationId);
    void clear(String conversationId);
}
```

**Step 1.4** — Create `ai.javaclaw.agent.memory.InMemoryChatMemoryRepository`

Simple in-memory impl for tests and builder defaults. Replaces `o.s.ai.chat.memory.InMemoryChatMemoryRepository`.

```java
package ai.javaclaw.agent.memory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.messages.Message;

public class InMemoryChatMemoryRepository implements AppendableChatMemoryRepository {
    private final Map<String, List<Message>> store = new LinkedHashMap<>();
    // ... implement all methods
}
```

### Phase 2: Migrate Implementations (javaclaw-memory module)

**Step 2.1** — Update `SpringDataChatMemoryRepository`

- Change: `implements AppendableChatMemoryRepository` → implements **own** `AppendableChatMemoryRepository`
- Import changes: remove `import ai.javaclaw.ai.memory.AppendableChatMemoryRepository`, already in same package
- **Serena**: `find_symbol("SpringDataChatMemoryRepository", include_body=true)` → verify current impl, then `replace_symbol_body`

**Step 2.2** — Move & Rewrite `JavaClawMessageWindowChatMemory`

- Move from `ai.javaclaw.ai.memory` → `ai.javaclaw.agent.memory` (same package as other memory classes)
- Change: `implements ChatMemory` → implements **own** `ChatMemory`
- Change: `AppendableChatMemoryRepository` refs → own interface
- **Builder type resolution**: Builder stores `AppendableChatMemoryRepository` (own type, not base `ChatMemoryRepository`).
  Constructor parameter: `AppendableChatMemoryRepository`.
  All real callers (`JavaClawConfiguration`) pass `SpringDataChatMemoryRepository` which implements own `AppendableChatMemoryRepository` — compiles fine.
- Remove inner `DelegatingAppendableChatMemoryRepository`. The fallback logic (simulate append via find+saveAll for non-appendable repos) is no longer needed — all real impls are `AppendableChatMemoryRepository`.
- Builder default: own `InMemoryChatMemoryRepository` (which implements `AppendableChatMemoryRepository`)
- **Serena**: `find_referencing_symbols` on `JavaClawMessageWindowChatMemory` to find all callers

### Phase 3: Update Consumers (javaclaw-core, javaclaw-api)

**Step 3.1** — Update imports: `o.s.ai.chat.memory.*` → `ai.javaclaw.agent.memory.*`

Import replacements:
- `import org.springframework.ai.chat.memory.ChatMemory` → `import ai.javaclaw.agent.memory.ChatMemory`
- `import org.springframework.ai.chat.memory.ChatMemoryRepository` → `import ai.javaclaw.agent.memory.ChatMemoryRepository`
- `import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository` → `import ai.javaclaw.agent.memory.InMemoryChatMemoryRepository`
- `import ai.javaclaw.ai.memory.JavaClawMessageWindowChatMemory` → `import ai.javaclaw.agent.memory.JavaClawMessageWindowChatMemory`
- `import ai.javaclaw.ai.memory.AppendableChatMemoryRepository` → `import ai.javaclaw.agent.memory.AppendableChatMemoryRepository`

Files to update (prod — **10 files**):
- `JavaClawConfiguration.java` — imports: `ChatMemory` + `ChatMemoryRepository` + `JavaClawMessageWindowChatMemory`.
**Also change `@Bean` method signature**: `public ChatMemory chatMemory(final AppendableChatMemoryRepository chatMemoryRepository)` — the Builder setter now requires own `AppendableChatMemoryRepository`, not base `ChatMemoryRepository`.
- `ChatServiceConfiguration.java` — `ChatMemory`
- `ChatService.java` — `ChatMemory`
- `MessageAssembler.java` — `ChatMemory` + `ChatMemoryRepository`
- `SseStreamingService.java` — `ChatMemory`
- `ConversationController.java` — `ChatMemoryRepository`
- **`DeliveryService.java`** — `AppendableChatMemoryRepository`
- **`ChatChannel.java`** — `AppendableChatMemoryRepository`
- **`MessageTool.java`** — `AppendableChatMemoryRepository`
- **`AgentToolsConfiguration.java`** — `AppendableChatMemoryRepository`

Files to update (test — **10 files**):
- `ChatServiceTest.java`
- `ChatServiceIntegrationTest.java`
- `MessageAssemblerTest.java`
- `SseStreamingServiceTest.java`
- `SseStreamingServiceReasoningCompatTest.java`
- `ConversationControllerTest.java`
- `ChatMemoryLiveE2ETest.java`
- **`DeliveryServiceTest.java`**
- **`ChatChannelTest.java`**
- **`MessageToolTest.java`**

- **Serena**: `search_for_pattern("org.springframework.ai.chat.memory")` → zero matches after this step
- **Serena**: `search_for_pattern("ai.javaclaw.ai.memory")` → zero matches after this step

**Step 3.2** — Update `ConversationController`

Currently typed as `ChatMemoryRepository` (Spring AI). Change to own `ChatMemoryRepository`.
Only uses `deleteByConversationId()` — signature identical.

**Step 3.3** — Update `MessageAssembler`

Currently has field `chatMemoryRepository` typed as `ChatMemoryRepository` (Spring AI).
Change to own `ChatMemoryRepository`.
Only uses `findByConversationId()` — signature identical.

**Step 3.4** — Update `AppendableChatMemoryRepository` consumers

- `DeliveryService` — field typed `AppendableChatMemoryRepository`, calls `appendAll()`. Change import only.
- `ChatChannel` — field typed `AppendableChatMemoryRepository`, calls `appendAll()`. Change import only.
- `MessageTool` + `MessageTool.Builder` — field typed `AppendableChatMemoryRepository`. Change import only.
- `AgentToolsConfiguration` — passes `AppendableChatMemoryRepository` bean. Change import only.

### Phase 3.5: Delete Old Package (AFTER all consumers updated)

- Delete `ai.javaclaw.ai.memory.AppendableChatMemoryRepository` (replaced in Step 1.2)
- Delete `ai.javaclaw.ai.memory.JavaClawMessageWindowChatMemory` (replaced in Step 2.2)
- Delete entire `ai.javaclaw.ai.memory` package directory
- **Serena**: `search_for_pattern("ai.javaclaw.ai.memory")` across codebase → must be zero
- **IMPORTANT**: this step MUST run after Phase 3 (all consumer imports updated), not during Phase 2

### Phase 4: Update pom.xml

**Step 4.1** — javaclaw-memory/pom.xml

Change comment on `spring-ai-client-chat`:

```xml
<!-- Spring AI — Message types only (ChatMemory/ChatMemoryRepository replaced by own interfaces) -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-client-chat</artifactId>
</dependency>
```

- Dependency stays because `Message`/`UserMessage`/etc. are still used
- All `o.s.ai.chat.memory.*` imports gone from module

### Phase 5: Update Tests

**Step 5.1** — Update `SpringDataChatMemoryRepositoryTest`

- Import changes only (Message types stay, memory interfaces change to own)
- **No test logic changes** — all assertions remain valid

**Step 5.2** — Update `ChatMemoryEntryRepositoryPostgresIT` and `ChatMemoryEntryRepositorySqliteIT`

- These use `SpringDataChatMemoryRepository` which now implements own interface
- Import changes for `Message` subtypes stay; memory interface imports change
- **Must run both dialects** per feedback_test_all_variability_axes

**Step 5.3** — Update test files for `AppendableChatMemoryRepository` consumers

- `DeliveryServiceTest.java` — import change `ai.javaclaw.ai.memory` → `ai.javaclaw.agent.memory`
- `ChatChannelTest.java` — same import change
- `MessageToolTest.java` — same import change

**Step 5.4** — Update integration tests in javaclaw-app

- `AuthIntegrationTest`, `ConversationIsolationIntegrationTest`, etc.
- These likely get `ChatMemory`/`ChatMemoryRepository` beans — type changes propagate via Spring context
- **Serena**: `search_for_pattern("ChatMemory", relative_path="javaclaw-app/src/test")` to find all affected tests

### Phase 6: Build & Verify

**Step 6.1** — `mvn compile -pl javaclaw-core/javaclaw-memory -am`
**Step 6.2** — `mvn test -pl javaclaw-core/javaclaw-memory`
**Step 6.3** — `mvn compile` (full project)
**Step 6.4** — `mvn test` (full project — all dialects)
**Step 6.5** — Verify: `grep -r "org.springframework.ai.chat.memory" --include="*.java"` → **zero results**
**Step 6.6** — Verify: `grep -r "ai.javaclaw.ai.memory" --include="*.java"` → **zero results**

---

## Risk Analysis

|                                    Risk                                    |                                              Mitigation                                              |
|----------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------|
| Spring AI autoconfiguration creates `ChatMemory` bean from their interface | We override with `@Bean` in `JavaClawConfiguration` → our type wins via `@Primary`                   |
| Spring AI advisor/client expects their `ChatMemory` type                   | Check: `search_for_pattern("ChatMemory", relative_path="javaclaw-provider")` — likely not used there |
| Test classpath: Spring AI test slices auto-wire their memory beans         | Our `@Bean` declarations + `@Primary` on `SpringDataChatMemoryRepository` handle this                |
| Package move breaks reflection/component-scan                              | `@ComponentScan` on main app covers `ai.javaclaw.agent.memory` already                               |

## Success Criteria

1. `grep -r "org.springframework.ai.chat.memory" --include="*.java"` returns **0 matches**
2. `grep -r "ai.javaclaw.ai.memory" --include="*.java"` returns **0 matches**
3. `spring-ai-client-chat` remains in javaclaw-memory pom.xml (for Message types) with updated comment
4. All existing tests green (unit + IT + both dialects)
5. `mvn compile` clean, no deprecation warnings related to memory interfaces

## Future Work (out of scope)

- Replace `Message` hierarchy with own types (bigger effort, touches ChatModel API)
- Add pagination to `ChatMemoryRepository.findByConversationId()`
- Add metadata/media support to memory entries
- Token counting at memory storage level

