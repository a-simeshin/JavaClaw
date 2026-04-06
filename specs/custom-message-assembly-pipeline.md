# Plan: Custom Message Assembly Pipeline

## Task Description

Заменить текущий advisor chain (`ChatClient` + `JavaClawMessageChatMemoryAdvisor` + `ToolCallAdvisor`) на собственный pipeline `SseStreamingService → ChatService → MessageAssembler → ChatModel.stream(Prompt)`. Pipeline должен обеспечить полный контроль над порядком секций system prompt, санитизацией истории, turn-boundary windowing и token budget — при сохранении универсального tool calling через `ToolCallingManager` (Path A: `ChatModel.stream()` с `ToolCallingChatOptions`).

Реализация идёт в 5 фазах: от простого extract до token budget. Phase 6 (LLM-суммаризация) — out of scope.

## Objective

После выполнения плана:
1. `ChatService` управляет полным жизненным циклом запроса к LLM: сборка сообщений → streaming → persist
2. `MessageAssembler` собирает секционный system prompt (identity + skills + context + environment) + sanitized + windowed history
3. `MessageSanitizer` удаляет битые последовательности (orphan tool_result, broken tool_call, duplicates)
4. `TurnBoundaryWindower` обрезает историю по turn-границам, не ломая tool chains
5. `TokenBudget` оценивает и проактивно сжимает историю до вызова LLM
6. Tool calling работает универсально через `ToolCallingManager` (Anthropic/OpenAI/Ollama/custom)
7. Все существующие E2E тесты проходят без изменений

## Problem Statement

Текущий pipeline на ChatClient + advisor chain — чёрный ящик:
- Нет контроля над порядком секций в system prompt
- Наивный windowing (`lastN` сообщений) ломает tool_call/tool_result пары
- Нет санитизации битой истории (orphan tool_results, duplicate messages)
- Нет оценки токенов — history может превысить context window
- `SmartWebFetchTool` создаёт circular dependency через ChatClient
- Advisor chain жёстко привязан к ChatClient, нет возможности unit-тестировать сборку сообщений отдельно

Зрелые продукты (PicoClaw, OpenClaw, Claude Code) управляют сборкой вручную — JavaClaw должен следовать этому паттерну.

## Solution Approach

**Path A (ChatModel direct):** `ChatService` → `ChatModel.stream(Prompt(messages, ToolCallingChatOptions))`.

- `ToolCallingManager` — универсальный интерфейс Spring AI (модуль `spring-ai-model`), работает во всех провайдерах
- При `internalToolExecutionEnabled(true)` (default) ChatModel автоматически крутит tool loop
- Никаких импортов конкретных провайдеров — только `org.springframework.ai.chat.model.ChatModel`

**Фазированный подход:**
- Phase 1: Extract — `ChatService` + `ToolCallbackResolver` + прямой `ChatModel.stream()`
- Phase 2: Assemble — `MessageAssembler` + секционный system prompt + `ActiveSkillsProvider`
- Phase 3: Sanitize — `MessageSanitizer` с 4 правилами очистки
- Phase 4: Window — `TurnBoundaryWindower` по turn-границам
- Phase 5: Budget — `TokenEstimator` + `TokenBudget` + проактивное сжатие

## Relevant Files

### Модифицируемые файлы

- `javaclaw-api/javaclaw-api-chat/src/main/java/ai/javaclaw/api/chat/rest/SseStreamingService.java` — основной SSE streaming caller, переключить с ChatClient на ChatService
- `javaclaw-core/src/main/java/ai/javaclaw/JavaClawConfiguration.java` — wiring tools/advisors, @Deprecated ChatClient bean, вынести tools в ChatServiceConfiguration
- `javaclaw-core/src/main/java/ai/javaclaw/agent/DefaultAgent.java` — synchronous agent, переключить на ChatService.call()
- `javaclaw-core/src/main/java/ai/javaclaw/agent/SystemPromptProvider.java` — рефакторить для секционного вывода (identity vs context)
- `javaclaw-core/src/main/java/ai/javaclaw/ai/memory/JavaClawMessageWindowChatMemory.java` — Phase 4: get() больше не используется pipeline'ом

### Удаляемые файлы (Phase 5 cleanup)

- `javaclaw-core/src/main/java/ai/javaclaw/ai/advisor/JavaClawMessageChatMemoryAdvisor.java` — заменён MessageAssembler + ChatService persist
- SmartWebFetchTool регистрация из JavaClawConfiguration (tool из внешней зависимости, удалить ссылки)

### Файлы без изменений (используются as-is)

- `javaclaw-core/src/main/java/ai/javaclaw/agent/memory/JdbcAppendableChatMemoryRepository.java` — persistence, append semantics сохраняются
- `javaclaw-core/src/main/java/ai/javaclaw/ai/memory/AppendableChatMemoryRepository.java` — интерфейс для append
- `javaclaw-core/src/main/java/ai/javaclaw/skills/SkillRepository.java` — используется ActiveSkillsProvider (добавить метод findAllByEnabledTrue или фильтровать)
- `javaclaw-core/src/main/java/ai/javaclaw/tools/AgentEnvironment.java` — environment info для system prompt

### Существующие тесты

- `javaclaw-api/javaclaw-api-chat/src/test/java/ai/javaclaw/api/chat/rest/SseStreamingServiceTest.java` — обновить для ChatService
- `javaclaw-core/src/test/java/ai/javaclaw/agent/memory/JdbcAppendableChatMemoryRepositoryTest.java` — без изменений

### New Files

- `javaclaw-core/src/main/java/ai/javaclaw/agent/pipeline/ChatService.java` — orchestration: assemble → stream → persist
- `javaclaw-core/src/main/java/ai/javaclaw/agent/pipeline/ChatServiceConfiguration.java` — @Configuration, wiring
- `javaclaw-core/src/main/java/ai/javaclaw/agent/pipeline/ToolCallbackResolver.java` — собирает tool callbacks
- `javaclaw-core/src/main/java/ai/javaclaw/agent/pipeline/MessageAssembler.java` — секционная сборка prompt
- `javaclaw-core/src/main/java/ai/javaclaw/agent/pipeline/AssembledPrompt.java` — record: systemMessage + history + userMessage
- `javaclaw-core/src/main/java/ai/javaclaw/agent/pipeline/ActiveSkillsProvider.java` — enabled skills из БД → system prompt секция
- `javaclaw-core/src/main/java/ai/javaclaw/agent/pipeline/MessageSanitizer.java` — 4 правила очистки
- `javaclaw-core/src/main/java/ai/javaclaw/agent/pipeline/TurnBoundaryWindower.java` — turn-aware windowing
- `javaclaw-core/src/main/java/ai/javaclaw/agent/pipeline/TokenEstimator.java` — эвристика chars/3.5
- `javaclaw-core/src/main/java/ai/javaclaw/agent/pipeline/TokenBudget.java` — record: budget расчёт
- `javaclaw-core/src/main/java/ai/javaclaw/agent/pipeline/TokenBudgetProperties.java` — @ConfigurationProperties
- `javaclaw-core/src/test/java/ai/javaclaw/agent/pipeline/ChatServiceTest.java`
- `javaclaw-core/src/test/java/ai/javaclaw/agent/pipeline/ToolCallbackResolverTest.java`
- `javaclaw-core/src/test/java/ai/javaclaw/agent/pipeline/MessageAssemblerTest.java`
- `javaclaw-core/src/test/java/ai/javaclaw/agent/pipeline/ActiveSkillsProviderTest.java`
- `javaclaw-core/src/test/java/ai/javaclaw/agent/pipeline/MessageSanitizerTest.java`
- `javaclaw-core/src/test/java/ai/javaclaw/agent/pipeline/TurnBoundaryWindowerTest.java`
- `javaclaw-core/src/test/java/ai/javaclaw/agent/pipeline/TokenEstimatorTest.java`
- `javaclaw-core/src/test/java/ai/javaclaw/agent/pipeline/TokenBudgetTest.java`

## Implementation Phases

### Phase 1: Extract Pipeline — ChatService + Direct ChatModel

**Цель:** Ввести `ChatService`, вызывающий `ChatModel.stream(Prompt)` напрямую. Поведение идентично текущему.

Новые классы в `ai.javaclaw.agent.pipeline`:
- `ChatService` — provider-agnostic, зависит от `ChatModel` (не от Anthropic/OpenAI)
- `ToolCallbackResolver` — собирает все tool callbacks (статические + MCP + auto-discovered)
- `ChatServiceConfiguration` — @Configuration bean wiring

Потребители (`SseStreamingService`, `DefaultAgent`) переключаются на `ChatService`. ChatClient bean получает `@Deprecated`. SmartWebFetchTool временно убирается.

### Phase 2: MessageAssembler — Ordered Sections

**Цель:** Извлечь логику сборки из `ChatService` в отдельный `MessageAssembler`.

Секционный system prompt (аналог Claude Code):

```
[1] Identity:    AGENT.md + SOUL.md
[2] Skills:      enabled skills из таблицы skills
[3] Context:     INFO.md
[4] Environment: AgentEnvironment.info()
```

Новые классы: `MessageAssembler`, `AssembledPrompt` (record), `ActiveSkillsProvider`.

### Phase 3: MessageSanitizer — Очистка истории

**Цель:** Удалить битые последовательности перед отправкой в LLM.

4 правила (в порядке применения):
1. **Dedup**: adjacent дубликаты (same type + content)
2. **Orphan tool_result**: ToolResponseMessage без matching tool_call
3. **Broken tool_call**: AssistantMessage с tool_call без tool_result → strip tool calls
4. **Alternation**: два UserMessage подряд → оставить последний

### Phase 4: TurnBoundaryWindower — Умная обрезка

**Цель:** Заменить наивное window() на turn-boundary aware windowing.

Алгоритм:
1. Группировать в "turns": UserMessage + все последующие Assistant/ToolResponse до следующего User
2. Дропать целые turns с начала, не ломая tool_call/tool_result пары
3. Считать сообщения против бюджета

`MessageAssembler` читает из `ChatMemoryRepository` напрямую (все сообщения), применяет sanitizer → windower.

### Phase 5: Token Budget — Оценка и проактивное сжатие

**Цель:** Оценивать токены и сжимать историю до вызова LLM.

- `TokenEstimator`: эвристика `chars / 3.5`
- `TokenBudget`: record с maxContextTokens (200k), reservedForResponse (8k), reservedForTools, reservedForSystem
- `TokenBudgetProperties`: `@ConfigurationProperties("javaclaw.chat.token-budget")`
- `TurnBoundaryWindower` получает перегрузку `window(messages, maxTokens, estimator)`

## Team Orchestration

- Я выступаю как team lead и оркестрирую команду через Task* Tools.
- Я НИКОГДА не работаю с кодом напрямую — только деплою team members для реализации.
- Фазы выполняются последовательно (каждая зависит от предыдущей).
- После каждой фазы — промежуточная валидация (compile + тесты).

### Team Members

- Builder
  - Name: builder-pipeline
  - Role: Основной разработчик pipeline — реализация Phase 1-5, создание классов, рефакторинг consumers
  - Agent Type: builder
  - Resume: true
- Builder
  - Name: builder-tests
  - Role: Написание comprehensive unit + integration тестов для всех фаз pipeline
  - Agent Type: builder
  - Resume: true
- Builder
  - Name: builder-cleanup
  - Role: Удаление deprecated кода, финальная чистка
  - Agent Type: builder
  - Resume: false
- Validator
  - Name: validator-final
  - Role: Read-only валидация: compile, spotless, тесты, acceptance criteria
  - Agent Type: validator
  - Resume: false

## Testing Strategy

Test pyramid ratio: **80% unit / 15% integration-API / 5% UI e2e**

### Unit Tests (80%)

|         Test Class         |   Тестируемый класс    |                                                     Ключевые тест-кейсы                                                     |
|----------------------------|------------------------|-----------------------------------------------------------------------------------------------------------------------------|
| `ChatServiceTest`          | `ChatService`          | Порядок сообщений (system → history → user), persist user before / assistant after, streaming delegation, call() delegation |
| `ToolCallbackResolverTest` | `ToolCallbackResolver` | Сборка callbacks из всех sources, кеширование, пустые sources                                                               |
| `MessageAssemblerTest`     | `MessageAssembler`     | Порядок секций, фильтрация SystemMessage из history, пустая история, все секции present/absent                              |
| `ActiveSkillsProviderTest` | `ActiveSkillsProvider` | Загрузка из БД, пустой список, disabled skills не попадают, формат вывода                                                   |
| `MessageSanitizerTest`     | `MessageSanitizer`     | По тест-кейсу на каждое из 4 правил + mixed scenario + clean data passthrough + empty input                                 |
| `TurnBoundaryWindowerTest` | `TurnBoundaryWindower` | Под бюджетом (passthrough), над бюджетом (drop oldest turns), tool chain не разрывается, single turn, empty history         |
| `TokenEstimatorTest`       | `TokenEstimator`       | Оценка для разных типов сообщений, пустой список, consistency                                                               |
| `TokenBudgetTest`          | `TokenBudget`          | availableForHistory() расчёт, edge cases (budget exhausted)                                                                 |

### Integration / API Tests (15%)

|             Test Class             |             Scope              |                                     Описание                                     |
|------------------------------------|--------------------------------|----------------------------------------------------------------------------------|
| `ChatServiceIntegrationTest`       | ChatService + ChatModel mock   | End-to-end flow: assemble → stream → persist, с реальным ChatMemory (in-memory)  |
| `SseStreamingServiceTest` (update) | SSE + ChatService              | Обновить существующий тест для работы с ChatService вместо ChatClient            |
| `MessageAssemblerIntegrationTest`  | Assembler + реальные providers | Проверить с реальными SystemPromptProvider + ActiveSkillsProvider (mocked repos) |

### UI E2E Tests (5%)

- Существующие E2E тесты (`ChatMemoryLiveE2ETest`) должны проходить без изменений — это основной критерий backward compatibility
- Новых E2E тестов не добавляется — pipeline прозрачен для UI

## Step by Step Tasks

- IMPORTANT: Execute every step in order, top to bottom. Each task maps directly to a `TaskCreate` call.
- Before you start, run `TaskCreate` to create the initial task list that all team members can see and execute.

### 1. Create Pipeline Foundation (Phase 1 Core)

- **Task ID**: create-pipeline-foundation
- **Depends On**: none
- **Assigned To**: builder-pipeline
- **Agent Type**: builder
- **Stack**: Java Spring Boot controller service bean configuration ToolCallback
- **Parallel**: false
- **Tests**: Unit: `ChatServiceTest` — порядок сообщений, persist flow. `ToolCallbackResolverTest` — сборка callbacks.
- Создать пакет `ai.javaclaw.agent.pipeline` в `javaclaw-core`
- Создать `ToolCallbackResolver`:
  - Конструктор: `SyncMcpToolCallbackProvider`, `Set<AutoDiscoveredTool<?>>`, список built-in tool beans (TaskTool, CheckListTool, McpTool, FileSystemTools)
  - Метод `List<ToolCallback> resolve()` — на Phase 1 просто собирает и кеширует все callbacks
  - НЕ включать SkillsTool из springaicommunity (filesystem skills) — он войдёт в tool callbacks напрямую
- Создать `ChatService`:
  - Конструктор: `ChatModel`, `ChatMemory`, `SystemPromptProvider`, `ToolCallbackResolver`
  - `Flux<ChatResponse> stream(String conversationId, String userContent)`:
    - SystemMessage из `systemPromptProvider.load()`
    - History из `chatMemory.get(conversationId)`
    - UserMessage(userContent)
    - `ToolCallingChatOptions` с callbacks из resolver + `internalToolExecutionEnabled(true)`
    - `chatModel.stream(new Prompt(messages, options))`
    - Persist: user message перед stream, assistant message из aggregated response
  - `String call(String conversationId, String userContent)` — synchronous версия для DefaultAgent
  - `<T> T call(String conversationId, String userContent, Class<T> resultType)` — structured output через `BeanOutputConverter` для `DefaultAgent.prompt()` / `TaskHandler`
  - **Fallback chatModel() streaming**: если `ChatModel` не поддерживает `stream()` (выбрасывает `UnsupportedOperationException`) — fallback на `Mono.fromCallable(() -> chatModel.call(prompt)).flux()`. Обернуть в try-catch в `stream()`.
- Создать `ChatServiceConfiguration` (@Configuration):
  - Bean `ToolCallbackResolver` — собирает все tool sources
  - Bean `ChatService` — wires ChatModel, ChatMemory, SystemPromptProvider, ToolCallbackResolver
  - Регистрировать SkillsTool callbacks (springaicommunity) через ToolCallbackResolver
- Написать `ChatServiceTest` и `ToolCallbackResolverTest` (unit, mock ChatModel/ChatMemory)

### 2. Migrate Consumers to ChatService (Phase 1 Migration)

- **Task ID**: migrate-consumers
- **Depends On**: create-pipeline-foundation
- **Assigned To**: builder-pipeline
- **Agent Type**: builder
- **Stack**: Java Spring Boot controller SSE streaming ChatModel
- **Parallel**: false
- **Tests**: Integration: обновить `SseStreamingServiceTest` для ChatService.
- Рефакторить `SseStreamingService`:
  - Заменить `ChatClient chatClient` → `ChatService chatService` в конструкторе
  - `chatClient.prompt().advisors().stream().content()` → `chatService.stream(conversationId, userContent)` + маппинг `Flux<ChatResponse>` в delta strings
  - Сохранить всю SSE/Vercel protocol логику as-is
- Рефакторить `DefaultAgent`:
  - Заменить `ChatClient chatClient` → `ChatService chatService`
  - `respondTo()` → `chatService.call(conversationId, question)`
  - `prompt(conversationId, input, Class<T>)` → `chatService.call(conversationId, input, resultType)` — structured output через BeanOutputConverter (TaskHandler зависит от этого метода для TaskResult.class)
- В `JavaClawConfiguration`:
  - Пометить ChatClient bean `@Deprecated`
  - Убрать регистрацию `SmartWebFetchTool` из tool list
  - Оставить advisors пока для backward compatibility (ChatClient deprecated, но не удалён)
- Обновить `SseStreamingServiceTest` для работы с ChatService mock

### 3. Create MessageAssembler (Phase 2)

- **Task ID**: create-message-assembler
- **Depends On**: migrate-consumers
- **Assigned To**: builder-pipeline
- **Agent Type**: builder
- **Stack**: Java Spring Boot service component entity JPA repository
- **Parallel**: false
- **Tests**: Unit: `MessageAssemblerTest` — порядок секций, history filtering. `ActiveSkillsProviderTest` — DB loading.
- Создать `AssembledPrompt` (record):
  - `SystemMessage systemMessage`, `List<Message> history`, `UserMessage userMessage`
  - `List<Message> toMessageList()` — [system, ...history, user]
- Создать `ActiveSkillsProvider` (@Component):
  - Зависимость: `SkillRepository`
  - `String loadActiveSkills()` — `skillRepository.findAllByOwnerIdIsNullAndEnabledTrue()` → формат `## Skill: {name}\n{content}\n\n`
  - Skill entity уже имеет поле `enabled` — добавить derived query method `findAllByOwnerIdIsNullAndEnabledTrue()` в `SkillRepository` (Flyway миграция не нужна)
  - Кешировать на 30 секунд (или @Cacheable)
- Рефакторить `SystemPromptProvider`:
  - Добавить `String loadIdentity()` — AGENT.md + SOUL.md
  - Добавить `String loadContext()` — INFO.md
  - Оставить `load()` как есть (backward compatibility)
- Создать `MessageAssembler` (@Component):
  - Зависимости: `SystemPromptProvider`, `ActiveSkillsProvider`, `ChatMemory`
  - `AssembledPrompt assemble(String conversationId, String userContent)`:
    - Секционный SystemMessage:

      ```
      [1] systemPromptProvider.loadIdentity()
      [2] activeSkillsProvider.loadActiveSkills()
      [3] systemPromptProvider.loadContext()
      [4] AgentEnvironment.info().toString()
      ```
    - History: `chatMemory.get(conversationId)` → убрать SystemMessage из history
    - UserMessage(userContent)
- Интегрировать в `ChatService`: делегировать сборку в `MessageAssembler`
- Написать `MessageAssemblerTest` и `ActiveSkillsProviderTest`

### 4. Create MessageSanitizer (Phase 3)

- **Task ID**: create-sanitizer
- **Depends On**: create-message-assembler
- **Assigned To**: builder-pipeline
- **Agent Type**: builder
- **Stack**: Java Spring Boot component record pattern matching
- **Parallel**: false
- **Tests**: Unit: `MessageSanitizerTest` — по тест-кейсу на каждое правило + mixed + passthrough.
- Создать `MessageSanitizer` (@Component, stateless):
  - `List<Message> sanitize(List<Message> messages)`
  - Правило 1 — **Dedup**: удалить adjacent дубликаты (same MessageType + content text)
  - Правило 2 — **Orphan tool_result**: удалить `ToolResponseMessage` без matching tool_call ID в предыдущем `AssistantMessage`
  - Правило 3 — **Broken tool_call**: если `AssistantMessage` содержит tool_call без последующего `ToolResponseMessage` — strip tool calls, оставить текст
  - Правило 4 — **Alternation**: два `UserMessage` подряд → оставить последний
  - Применять правила в указанном порядке (pipeline pattern)
- Интегрировать в `MessageAssembler`: после `chatMemory.get()` → `sanitizer.sanitize(history)`
- Написать `MessageSanitizerTest`:
  - Тест на каждое правило отдельно
  - Mixed scenario (все правила применяются)
  - Clean data passthrough (ничего не меняется)
  - Empty input → empty output

### 5. Create TurnBoundaryWindower (Phase 4)

- **Task ID**: create-windower
- **Depends On**: create-sanitizer
- **Assigned To**: builder-pipeline
- **Agent Type**: builder
- **Stack**: Java Spring Boot component record pattern matching
- **Parallel**: false
- **Tests**: Unit: `TurnBoundaryWindowerTest` — under/over budget, tool chain preserved, edge cases.
- Создать `TurnBoundaryWindower` (@Component, stateless):
  - `List<Message> window(List<Message> messages, int maxMessages)`:
    1. Группировать в "turns": `UserMessage` + все последующие `AssistantMessage`/`ToolResponseMessage` до следующего `UserMessage`
    2. Если total messages <= maxMessages → return as-is
    3. Дропать целые turns с начала (oldest first)
    4. Считать сообщения (не turns) против бюджета
    5. Гарантия: tool_call/tool_result пары не разрываются (входят в один turn)
- Рефакторить `MessageAssembler`:
  - Заменить `chatMemory.get(conversationId)` на `chatMemoryRepository.get(conversationId)` (все сообщения, без windowing)
  - Pipeline: `repository.get()` → `sanitizer.sanitize()` → `windower.window(sanitized, maxMessages)`
  - Добавить зависимость `ChatMemoryRepository` (вместо или в дополнение к ChatMemory)
- `JavaClawMessageWindowChatMemory.get()` больше не используется pipeline'ом (остаётся для `add()`)
- Написать `TurnBoundaryWindowerTest`:
  - Under budget (passthrough)
  - Over budget (drops oldest turns)
  - Tool chain не разрывается
  - Single turn
  - Empty history

### 6. Create Token Budget (Phase 5)

- **Task ID**: create-token-budget
- **Depends On**: create-windower
- **Assigned To**: builder-pipeline
- **Agent Type**: builder
- **Stack**: Java Spring Boot component record configuration
- **Parallel**: false
- **Tests**: Unit: `TokenEstimatorTest`, `TokenBudgetTest` — расчёты, edge cases.
- Создать `TokenEstimator`:
  - `int estimate(List<Message> messages)` — sum of individual
  - `int estimate(Message message)` — `message.getText().length() / 3.5` (rounded up)
- Создать `TokenBudget` (record):
  - `int maxContextTokens` (default 200_000)
  - `int reservedForResponse` (default 8_000)
  - `int reservedForTools` (default 4_000)
  - `int reservedForSystem` (default 0, вычисляется динамически)
  - `int availableForHistory()` — `maxContextTokens - reservedForResponse - reservedForTools - reservedForSystem`
- Создать `TokenBudgetProperties` (`@ConfigurationProperties("javaclaw.chat.token-budget")`):
  - `maxContextTokens`, `reservedForResponse`, `reservedForTools`
  - Зарегистрировать в `ChatServiceConfiguration`
- Добавить перегрузку в `TurnBoundaryWindower`:
  - `List<Message> window(List<Message> messages, int maxTokens, TokenEstimator estimator)`
  - Дропает turns пока `estimator.estimate(remaining) > maxTokens`
- Интегрировать в `MessageAssembler`:
  - После сборки system message → `estimator.estimate(systemMessage)` → `budget.reservedForSystem = estimate`
  - Windowing: `windower.window(sanitized, budget.availableForHistory(), estimator)`
- Написать `TokenEstimatorTest` и `TokenBudgetTest`
- Добавить в `application.yaml` секцию `javaclaw.chat.token-budget` с defaults

### 7. Cleanup Deprecated Code

- **Task ID**: cleanup-deprecated
- **Depends On**: create-token-budget
- **Assigned To**: builder-cleanup
- **Agent Type**: builder
- **Stack**: Java Spring Boot configuration bean
- **Parallel**: false
- **Tests**: Compile + existing tests pass after cleanup.
- Удалить `JavaClawMessageChatMemoryAdvisor.java` полностью
- Удалить ChatClient bean из `JavaClawConfiguration`:
  - Удалить метод `chatClient()`
  - Удалить imports: `ChatClient`, `ToolCallAdvisor`, `SimpleLoggerAdvisor`, `SmartWebFetchTool`, `SkillsTool` (springaicommunity)
  - Оставить bean `chatMemory()` (используется ChatService)
- Убрать `@Deprecated` аннотации (код уже удалён)
- Проверить что нет dangling imports/references на удалённые классы
- Запустить `mvn compile -q` — должен проходить

### 8. Write Comprehensive Tests

- **Task ID**: write-tests
- **Depends On**: cleanup-deprecated
- **Assigned To**: builder-tests
- **Agent Type**: builder
- **Stack**: Java MockMvc Mockito assertj allure test structure test naming integration test
- **Parallel**: false
- **Tests**: N/A — this IS the test task.
- Ревью и дополнение unit тестов для всех pipeline классов:
  - `ChatServiceTest`: mock ChatModel returns Flux, verify persist calls, verify message order
  - `ToolCallbackResolverTest`: multiple sources, empty sources, dedup
  - `MessageAssemblerTest`: полный flow с mocked providers, порядок секций
  - `ActiveSkillsProviderTest`: with skills, empty skills, null content handling
  - `MessageSanitizerTest`: comprehensive — все 4 правила + комбинации
  - `TurnBoundaryWindowerTest`: count-based + token-based windowing
  - `TokenEstimatorTest`: различные типы messages
  - `TokenBudgetTest`: boundary calculations
- Integration тест:
  - `ChatServiceIntegrationTest`: собрать реальный pipeline с mock ChatModel, проверить end-to-end flow
- Убедиться что `SseStreamingServiceTest` обновлён и проходит
- Запустить `mvn test` — все тесты зелёные
- Follow existing test patterns: AssertJ assertions, descriptive method names, @DisplayName

### 9. Final Validation

- **Task ID**: validate-all
- **Depends On**: write-tests
- **Assigned To**: validator-final
- **Agent Type**: validator
- **Stack**: Java Spring Boot Maven test compile spotless integration test assertj allure
- **Parallel**: false
- Run all validation commands (see Validation Commands section)
- Verify all unit tests pass
- Verify all integration tests pass
- Verify no compilation errors
- Verify code style (spotless)
- Verify acceptance criteria met:
  - [ ] ChatService существует и используется SseStreamingService + DefaultAgent
  - [ ] ChatClient bean удалён
  - [ ] MessageAssembler собирает секционный system prompt
  - [ ] MessageSanitizer применяет 4 правила
  - [ ] TurnBoundaryWindower не ломает tool chains
  - [ ] TokenBudget оценивает и сжимает историю
  - [ ] SmartWebFetchTool убран
  - [ ] Все тесты зелёные

## Acceptance Criteria

1. `ChatService` принимает `(conversationId, userContent)` и возвращает `Flux<ChatResponse>` / `String`
2. `SseStreamingService` и `DefaultAgent` используют `ChatService` (не ChatClient)
3. ChatClient bean и advisor chain удалены из `JavaClawConfiguration`
4. `MessageAssembler` собирает: `[Identity + Skills + Context + Environment]` + `[sanitized windowed history]` + `[UserMessage]`
5. `MessageSanitizer` удаляет: duplicates, orphan tool_results, broken tool_calls, double UserMessages
6. `TurnBoundaryWindower` обрезает по turn-границам, tool_call/tool_result пары не разрываются
7. `TokenBudget` рассчитывает доступный бюджет, windowing использует token-based обрезку
8. SmartWebFetchTool временно убран из tool list
9. `mvn compile -q` проходит без ошибок
10. `mvn test` — все unit + integration тесты зелёные
11. `mvn spotless:check` — код отформатирован
12. Никаких импортов конкретных провайдеров (Anthropic/OpenAI) в pipeline классах

## Validation Commands

Execute these commands to validate the task is complete:

- `mvn spotless:check` — проверка форматирования кода
- `mvn compile -q` — компиляция без ошибок
- `mvn test` — все unit + integration тесты зелёные
- `mvn test -pl javaclaw-core -Dtest="ai.javaclaw.agent.pipeline.*Test"` — pipeline тесты отдельно
- `grep -r "ChatClient" javaclaw-core/src/main/java/ai/javaclaw/JavaClawConfiguration.java` — должен вернуть пусто (ChatClient bean удалён)
- `grep -r "import org.springframework.ai.anthropic" javaclaw-core/src/main/java/ai/javaclaw/agent/pipeline/` — должен вернуть пусто (no provider imports)
- `grep -r "SmartWebFetchTool" javaclaw-core/src/main/java/` — должен вернуть пусто

## Notes

- **Spring AI version**: 2.0.0-SNAPSHOT. `ToolCallingChatOptions` подтверждён в `org.springframework.ai.model.tool`. `ChatModel.stream(Prompt)` возвращает `Flux<ChatResponse>`.
- **ToolCallback**: `org.springframework.ai.tool.ToolCallback` extends `FunctionCallback`.
- **SmartWebFetchTool**: из `spring-ai-agent-utils-0.6.0-SNAPSHOT`, зависит от `ChatClient`. Временно убирается. В будущей фазе можно: (a) создать lightweight ChatClient из ChatModel, (b) дождаться upstream fix.
- **SkillRepository**: имеет `findAllByOwnerIdIsNull()`. `Skill` entity уже имеет поле `enabled`. Добавить derived query `findAllByOwnerIdIsNullAndEnabledTrue()` в `SkillRepository` — Flyway миграция не нужна.
- **ConfigurationManager**: публикует `ConfigurationChangedEvent` — можно использовать для invalidation cache в `ToolCallbackResolver` (MCP tools).
- **DefaultAgent.prompt()**: метод с entity extraction (`Class<T> result`), используется `TaskHandler` для `TaskResult.class`. Решение: `ChatService` получает метод `<T> T call(conversationId, input, Class<T>)` с `BeanOutputConverter` — structured output через прямой `ChatModel.call()` без ChatClient.
- **Fallback chatModel()**: fallback bean в `JavaClawConfiguration` реализует только synchronous `call(Prompt)`. `ChatService.stream()` должен обрабатывать `UnsupportedOperationException` от `chatModel.stream()` и fallback на `Mono.fromCallable(() -> chatModel.call(prompt)).flux()`.
- **Existing E2E tests**: `ChatMemoryLiveE2ETest` — не запускать в рамках этого плана (требует живой LLM). Запуск E2E — отдельный цикл после стабилизации.
- **Target Architecture** (после Phase 5):

  ```
  ChatRestController
    → SseStreamingService (SSE transport)
      → ChatService (orchestration)
        → MessageAssembler
          ├── SystemPromptProvider.loadIdentity()/loadContext()
          ├── ActiveSkillsProvider.loadActiveSkills()
          ├── ChatMemoryRepository.get() → raw history
          ├── MessageSanitizer.sanitize() → clean history
          ├── TurnBoundaryWindower.window() → windowed history (token-based)
          └── [SystemMessage, history, UserMessage]
        → ChatModel.stream(Prompt(messages, ToolCallingChatOptions))
          └── ToolCallingManager (universal: Anthropic/OpenAI/Ollama/custom)
        → persist assistant message
  ```

