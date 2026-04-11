# Plan: OpenAI-compat Reasoning Events (bug #2_thinking)

## Task Description

Реализовать сквозную поддержку reasoning-событий (SSE `g:` кадров — ReasoningDelta) для OpenAI-совместимых провайдеров (OpenRouter, MiniMax) в JavaClaw. Сейчас `SseStreamingService` эмитит reasoning-блок только для Anthropic (через `AssistantMessage.properties["thinking"]==true`), который проставляет Spring AI Anthropic ChatModel. Для OpenAI-совместимых провайдеров Spring AI `OpenAiChatModel` не читает поля `delta.reasoning` / `delta.reasoning_content` / `delta.reasoning_details[]` — они теряются при парсинге, reasoning-блок на фронте пуст.

Решение делается **целиком через Spring AI** — новая имплементация `org.springframework.ai.chat.model.ChatModel` + `StreamingChatModel`, которая оборачивает дефолтный автоконфигурированный `OpenAiChatModel`, добавляет reasoning-параметры в запрос и параллельно с дефолтным парсингом перехватывает raw SSE chunks (через кастомный `WebClient`), извлекает reasoning-токены и эмитит их как отдельные `ChatResponse` с `metadata.thinking=true`. Интеграция в существующий pipeline происходит без изменения `SseStreamingService` — мост осуществляется через уже поддерживаемый `properties["thinking"]` контракт.

## Objective

После завершения работ:
1. В запросах к OpenRouter body содержит `"reasoning": {"effort": <configured>}` (Spring AI `OpenAiChatOptions` расширяется кастомной опцией).
2. Reasoning-токены, приходящие в `delta.reasoning_details[]` / `delta.reasoning` / `delta.reasoning_content`, конвертируются в `ChatResponse` с `AssistantMessage.properties["thinking"]=true` и стримятся в `SseStreamingService` **до** content-токенов.
3. По завершении reasoning-блока (до первого content-токена) эмитится `AssistantMessage` с `properties["signature"]=<id|format>` — закрытие reasoning-блока в существующей логике `SseStreamingService:296-307`.
4. Frontend (`reasoning-block.tsx`) отображает размышления minimax/openrouter reasoning-моделей так же, как сейчас работает Anthropic.
5. Все тесты (unit/integration/E2E) зелёные.

## Problem Statement

- `SseStreamingService.java:276-296` — ветка `metadata.containsKey("thinking")` никогда не срабатывает для OpenAI-compat, потому что Spring AI `OpenAiChatModel` парсит только `delta.content`, отбрасывая reasoning-поля.
- В `application.yaml:25-30` дефолтный провайдер — OpenRouter (`base-url: https://openrouter.ai/api`, модель `minimax/minimax-m2.7`) — это reasoning-модель, потеря reasoning-токенов критична для UX.
- Спека `specs/chat-runtime-bug-fixes-e2e-coverage.md:11,632` явно выносит #2_thinking в отдельный план — это тот план.

## Solution Approach

### Архитектурное решение

Создать декоратор `ReasoningAwareChatModel implements ChatModel` поверх автоконфигурированного `OpenAiChatModel`. Внутри:

**Non-streaming путь (`call(Prompt)`):** делегируется в обёрнутый `OpenAiChatModel` без изменений (reasoning для non-stream режима пока out-of-scope — агентский pipeline везде использует stream).

**Streaming путь (`stream(Prompt)`):**
1. Перед вызовом delegate — инжектит `reasoning: {effort}` в request options. Spring AI 2.0.0-SNAPSHOT `OpenAiChatOptions` поддерживает произвольные `additionalProperties` (`OpenAiChatOptions.Builder#metadata()` / `.httpHeaders()` / raw JSON map). Если прямой API нет — расширяем через кастомный `OpenAiApi` подкласс, который добавляет поле в сериализованный JSON через `ObjectMapper` mixin.
2. Параллельно с `delegate.stream(prompt)` запускается **собственный WebClient-канал** к тому же endpoint с тем же request body. Цель — получить raw SSE chunks и распарсить reasoning-поля, которых нет в `ChatCompletionChunk` Spring AI. **Это не дубль запроса** — delegate's flux игнорируется, используется только raw-канал + ручная сборка `ChatResponse` (reasoning + content в едином потоке). Инструменты (tool calls) проксируются из raw delta.
3. Парсер `ReasoningChunkParser` извлекает из каждого chunk:
- Reasoning-токены (`delta.reasoning_details[].text`, `delta.reasoning`, `delta.reasoning_content`) → `ChatResponse` с `AssistantMessage(text, Map.of("thinking", true))`.
- Завершение reasoning (первый ненулевой `delta.content` после reasoning-токенов) → эмитится синтетический signature chunk: `AssistantMessage("", Map.of("signature", reasoningId))` для закрытия блока в `SseStreamingService`.
- Content-токены (`delta.content`) → `AssistantMessage(text)` без thinking metadata.
- Tool calls (`delta.tool_calls[]`) → стандартный путь Spring AI — восстанавливаем через `ToolCall` из `org.springframework.ai.chat.messages`.
- Usage (`finish.usage`) → `ChatResponseMetadata`.

### Почему именно такой подход

- **Единственный способ в рамках Spring AI 2.0**: `OpenAiChatModel` не final, но его `ChatCompletionChunk` DTO фиксированный — не содержит reasoning-полей, extend через subclass не поможет (`ObjectMapper` внутри Spring AI десериализует в закрытый record). Форкать DTO = копировать весь класс. Кастомная имплементация `ChatModel` — официальный расширение-паттерн Spring AI (так делают интеграции Ollama/Bedrock/DeepSeek).
- **Переиспользование OpenAiApi DTOs для запроса**: билдер запроса (messages, tools, params) из Spring AI используется as-is — мы только добавляем reasoning-поле и перехватываем response.
- **Без изменений в `SseStreamingService`**: существующий контракт `metadata["thinking"]` + `metadata["signature"]` уже рабочий (проверено Anthropic-путём и unit-тестами `SseStreamingServiceTest:284-396`). Мост через этот же контракт — минимальная поверхность изменений.
- **Без двойного запроса**: хотя концептуально мы делаем raw HTTP вызов, delegate используется только для NON-stream пути. Streaming путь полностью заменён.

### Альтернативы (отвергнуты)

- **Форк `OpenAiChatModel`** — нельзя поддерживать при upgrade Spring AI (сейчас 2.0.0-SNAPSHOT — нестабильный API).
- **`ExchangeFilterFunction` на WebClient Spring AI** — reasoning-поля теряются при десериализации в `ChatCompletionChunk`, интерцептор ничего не изменит.
- **Pre-parse SSE + re-serialize** — избыточная сложность, два прохода парсинга.

## Relevant Files

- `javaclaw-api/javaclaw-api-chat/src/main/java/ai/javaclaw/api/chat/service/SseStreamingService.java` — существующий обработчик reasoning через `metadata["thinking"]` (строки 276-320). **Не меняется**, только становится источником `g:` кадров для OpenAI-compat.
- `javaclaw-api/javaclaw-api-chat/src/test/java/ai/javaclaw/api/chat/service/SseStreamingServiceTest.java` — эталонный паттерн тестов reasoning lifecycle (строки 284-396). Расширяется сценарием OpenAI-compat.
- `javaclaw-core/src/main/java/ai/javaclaw/agent/pipeline/ChatServiceConfiguration.java` — место регистрации `@Primary` ChatModel bean (сейчас `FallbackChatModel` обёртка). Декоратор вклинивается ВНУТРЬ FallbackChatModel chain: `FallbackChatModel → ReasoningAwareChatModel → OpenAiChatModel`.
- `javaclaw-core/src/main/java/ai/javaclaw/agent/pipeline/FallbackChatModel.java` — без изменений логики, только delegate становится `ReasoningAwareChatModel`.
- `javaclaw-core/src/main/java/ai/javaclaw/agent/config/DefaultChatModelProperties.java` — существующий `@ConfigurationProperties("spring.ai.openai.chat.options")`. Расширяется/дополняется новым `ReasoningProperties` (или добавляется поле).
- `javaclaw-app/src/main/resources/application.yaml` — добавить `javaclaw.reasoning.*` секцию.
- `javaclaw-app/src/test/resources/application-integration.yaml`, `application-contracttest.yaml` — прописать тестовые дефолты reasoning.
- `javaclaw-frontend/src/components/chat/reasoning-block.tsx` — не меняется, используется as-is.
- `specs/chat-runtime-bug-fixes-e2e-coverage.md` — обновить статус #2_thinking → DONE по завершении.

### New Files

**Модуль `javaclaw-provider/javaclaw-provider-openai`** — имеет зависимость на `spring-ai-starter-model-openai`, где живёт `OpenAiChatModel.class`. `javaclaw-core` к этому классу доступа НЕ имеет (depends only на `spring-ai-client-chat` — интерфейсы), поэтому все классы с прямыми ссылками на `OpenAiChatModel` размещаем здесь:
- `javaclaw-provider/javaclaw-provider-openai/src/main/java/ai/javaclaw/provider/openai/reasoning/ReasoningAwareChatModel.java` — главная имплементация `ChatModel` + `StreamingChatModel`. Конструктор принимает `ChatModel delegate` (интерфейс, не конкретный класс — чтобы упростить мокание в тестах). Конкретный `OpenAiChatModel` инжектится в `@Bean`-методе через `ObjectProvider<OpenAiChatModel>` — обходит конфликт с `@Primary FallbackChatModel`.
- `javaclaw-provider/javaclaw-provider-openai/src/main/java/ai/javaclaw/provider/openai/reasoning/ReasoningChunkParser.java` — парсер SSE chunks (Jackson): извлекает reasoning/content/tool_calls из raw JSON.
- `javaclaw-provider/javaclaw-provider-openai/src/main/java/ai/javaclaw/provider/openai/reasoning/ReasoningRequestBuilder.java` — строит body с `reasoning: {effort}` поверх Spring AI `OpenAiChatOptions`.
- `javaclaw-provider/javaclaw-provider-openai/src/main/java/ai/javaclaw/provider/openai/reasoning/ReasoningProperties.java` — `@ConfigurationProperties("javaclaw.reasoning")`: `enabled: bool`, `effort: high|medium|low|minimal`, `exclude: bool`, `maxTokens: Integer`, `modelPattern: String` (regex, matched **только против `Prompt.getOptions().getModel()`** — не против base-url; default `.*(minimax|openrouter|deepseek|claude|o1|o3|gpt-5).*`).
- `javaclaw-provider/javaclaw-provider-openai/src/main/java/ai/javaclaw/provider/openai/reasoning/ReasoningAutoConfiguration.java` — `@AutoConfiguration @EnableConfigurationProperties(ReasoningProperties.class)` регистрирует `ReasoningAwareChatModel` как `@Bean` с `@ConditionalOnProperty("javaclaw.reasoning.enabled", havingValue="true", matchIfMissing=true)`. Инжектит `OpenAiChatModel` через `ObjectProvider<OpenAiChatModel>` чтобы получить именно raw autoconfigured bean, минуя `FallbackChatModel`.
- `javaclaw-provider/javaclaw-provider-openai/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` — регистрация `ReasoningAutoConfiguration`.
- `javaclaw-provider/javaclaw-provider-openai/src/test/java/ai/javaclaw/provider/openai/reasoning/ReasoningChunkParserTest.java` — unit: все 3 варианта полей (reasoning_details / reasoning / reasoning_content), смешанный поток, edge cases.
- `javaclaw-provider/javaclaw-provider-openai/src/test/java/ai/javaclaw/provider/openai/reasoning/ReasoningRequestBuilderTest.java` — unit: JSON-билдер.
- `javaclaw-provider/javaclaw-provider-openai/src/test/java/ai/javaclaw/provider/openai/reasoning/ReasoningAwareChatModelTest.java` — unit с MockWebServer: полный streaming цикл (reasoning → content → tool_call → finish + disabled-by-pattern bypass).

**`javaclaw-provider/javaclaw-provider-openai/pom.xml`** — добавить test-scope зависимость `com.squareup.okhttp3:mockwebserver` (не объявлена сейчас нигде в проекте).

**Модуль `javaclaw-app`** — `javaclaw-provider-openai` должен быть уже подключён как runtime dependency (проверить; если нет — добавить), чтобы auto-configuration подхватилась.

**Модуль `javaclaw-api/javaclaw-api-chat`**:
- `javaclaw-api/javaclaw-api-chat/src/test/java/ai/javaclaw/api/chat/service/SseStreamingServiceReasoningCompatTest.java` — integration: `SseStreamingService` + mock `ChatModel` эмитирующий reasoning через `properties["thinking"]` — проверка что контракт работает для OpenAI-compat пути.

**Модуль `javaclaw-e2e`**:
- `javaclaw-e2e/src/test/java/ai/javaclaw/e2e/playwright/OpenAiCompatReasoningE2ETest.java` — E2E Playwright-тест с локальным WireMock-сервером (добавить `org.wiremock:wiremock-standalone` в `javaclaw-e2e/pom.xml` test-scope, если отсутствует). WireMock стартуется через `@RegisterExtension WireMockExtension` ДО открытия браузера; в `application-it.yaml` (test profile) `spring.ai.openai.base-url` указывает на `http://localhost:${wiremock.port}`. Stub возвращает SSE с `choices[].delta.reasoning_details[]` → проверка что frontend `reasoning-block` заполнен ДО основного ответа.

## Implementation Phases

### Phase 1: Foundation

1. Добавить модульные зависимости в `javaclaw-provider/javaclaw-provider-openai/pom.xml`: `com.squareup.okhttp3:mockwebserver` (test), `org.springframework:spring-webflux` (compile, если не транзитивно через spring-ai-openai).
2. Создать `ReasoningProperties` (`@ConfigurationProperties("javaclaw.reasoning")`) в `javaclaw-provider-openai`: `enabled`, `effort`, `exclude`, `maxTokens`, `modelPattern` (regex против `Prompt.getOptions().getModel()`).
3. Создать `ReasoningAutoConfiguration` с `@EnableConfigurationProperties` + `@ConditionalOnProperty`.
4. Создать файл `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` с именем класса.
5. Прописать дефолты в `javaclaw-app/src/main/resources/application.yaml` (`javaclaw.reasoning.enabled: true`, `effort: medium`, `modelPattern: .*(minimax|openrouter|deepseek|o1|o3|gpt-5).*`).
6. Прописать reasoning-disabled в `javaclaw-app/src/test/resources/application-integration.yaml` и `application-contracttest.yaml` (чтобы не ломать существующие тесты: `javaclaw.reasoning.enabled: false`).
7. Создать скелеты `ReasoningChunkParser`, `ReasoningRequestBuilder`, `ReasoningAwareChatModel` с заглушками (TDD).

### Phase 2: Core Implementation

1. Реализовать `ReasoningChunkParser`:
   - Вход: `JsonNode` от одного SSE chunk (`data: {...}`).
   - Выход: `ParsedChunk { Optional<String> reasoningText, Optional<String> reasoningId, Optional<String> contentText, List<ToolCallDelta> tools, Optional<String> finishReason, Optional<Usage> usage }`.
   - Поддержать три формата reasoning-полей (с приоритетом `reasoning_details[]` > `reasoning` > `reasoning_content`).
2. Реализовать `ReasoningRequestBuilder`:
   - Собирает OpenAI-compat body из Spring AI `Prompt` + `OpenAiChatOptions` + `ReasoningProperties`.
   - Переиспользовать Spring AI `OpenAiApi.ChatCompletionRequest` через Jackson → `ObjectNode`, добавить `reasoning: {effort|max_tokens|exclude}`.
3. Реализовать `ReasoningAwareChatModel.stream(Prompt)`:
   - **Bypass-условие**: если `props.enabled()==false` ИЛИ `Prompt.getOptions().getModel()` не матчит `props.modelPattern()` → делегирует `delegate.stream(prompt)` без модификаций (ноль регрессии для не-reasoning моделей).
   - Иначе строит request body через `ReasoningRequestBuilder`, открывает свой WebClient → POST `/v1/chat/completions` с `Accept: text/event-stream`.
   - `.bodyToFlux(String.class)` → парсит SSE lines → `Flux<ParsedChunk>` через `ReasoningChunkParser`.
   - **State machine** `INIT → REASONING → POST_REASONING → FINISH`:
     - Reasoning chunk → state=`REASONING`, эмит `ChatResponse` с `AssistantMessage(text, Map.of("thinking", true))`.
     - Первый **non-reasoning** chunk (content ИЛИ tool_calls ИЛИ finish) после REASONING → перед эмитом переходного чанка эмитим синтетический signature chunk `AssistantMessage("", Map.of("signature", reasoningId))`, state=`POST_REASONING`. Это закрывает reasoning-блок в `SseStreamingService:296-307`. **Signature должен срабатывать и для tool-call-only ответов**, не только для content-only.
     - Content chunk → `AssistantMessage(text)` без thinking.
     - Tool call chunk → `AssistantMessage` с `List<ToolCall>` (копируем логику агрегирования tool_call deltas из Spring AI `OpenAiChatModel` — merge по `index`).
     - Finish chunk → `ChatResponse` с usage metadata, state=`FINISH`.
   - Обработка ошибок: на ошибке парсинга reasoning-поля для конкретного chunk — логируем WARN и пропускаем его (не ломаем поток). Network error → `Flux.error()`, далее подхватывается `FallbackChatModel`.
4. Реализовать `call(Prompt)` как делегацию в обёрнутый `ChatModel delegate`.
5. **Вклиниться в DI безопасно**:
   - `ReasoningAutoConfiguration` регистрирует `@Bean ReasoningAwareChatModel` с параметром `ObjectProvider<OpenAiChatModel>` (конкретный тип). Внутри: `provider.getIfAvailable()` возвращает именно raw autoconfigured `OpenAiChatModel` — минуя `@Primary FallbackChatModel` и избегая циклической зависимости.
   - Обновить `ChatServiceConfiguration.fallbackChatModel()`: изменить filter lambda на `filter(m -> m instanceof ReasoningAwareChatModel || (m instanceof OpenAiChatModel && !(m instanceof FallbackChatModel)))`. Приоритет отдаём `ReasoningAwareChatModel`, если он есть в контексте (`.findFirst()` после сортировки `Comparator.comparingInt(m -> m instanceof ReasoningAwareChatModel ? 0 : 1)`). Это уберёт неопределённость порядка из `ObjectProvider.stream()`.
   - **Альтернативный минимальный вариант** (если Spring AI автоконфигурация легко переопределяется через `@Bean` в app-модуле): в `javaclaw-app` импортировать `ReasoningAutoConfiguration` явно и переключить quualifier через `@Bean(name = "primaryReasoningChatModel")`. Выбирает билдер на этапе реализации.
6. Валидация: `mvn -pl javaclaw-provider/javaclaw-provider-openai -am compile` → зелёное.

### Phase 3: Integration & Polish

1. Написать unit-тесты (Phase 3.1 в Step by Step).
2. Написать integration-тесты с MockWebServer (Phase 3.2).
3. Добавить E2E Playwright-сценарий (Phase 3.3).
4. Запустить полный `mvn test` — все 1187+N тестов зелёные.
5. Обновить `specs/chat-runtime-bug-fixes-e2e-coverage.md`: #2_thinking → DONE.
6. Проверить вручную через `curl` → `/api/chat/stream` с minimax моделью: убедиться, что `g:` кадры приходят.

## Team Orchestration

- Team lead (я) оркестрирует через `TaskCreate` / `Task` / `TaskUpdate` / `TaskOutput`.
- Билдер работает итеративно через `resume: true` для сохранения контекста между фазами.
- Валидатор проверяет итоговый результат против acceptance criteria.
- Pre-build: `plan-reviewer` валидирует сам план.

### Team Members

- Builder
  - Name: `builder-reasoning`
  - Role: Реализовать `ReasoningAwareChatModel` + парсер + request-builder + интеграцию в DI.
  - Agent Type: `builder`
  - Resume: `true`
- Builder
  - Name: `builder-tests`
  - Role: Написать unit/integration/E2E тесты. Использовать MockWebServer для сетевого слоя, existing Playwright base для E2E.
  - Agent Type: `builder`
  - Resume: `true`
- Reviewer
  - Name: `plan-reviewer`
  - Role: Content-review плана перед началом сборки.
  - Agent Type: `plan-reviewer`
  - Resume: `false`
- Validator
  - Name: `validator-reasoning`
  - Role: Проверка против acceptance criteria, запуск `mvn test`, валидация SSE-потока через curl.
  - Agent Type: `validator`
  - Resume: `false`

## Testing Strategy

Test pyramid ratio: **80% unit / 15% integration-API / 5% UI e2e**

### Unit Tests (80%)

- `ReasoningChunkParserTest` (в `javaclaw-core`):
  - `parse_reasoningDetailsArray_extractsText` — OpenRouter new format (`delta.reasoning_details[{type:reasoning.text,text:...}]`).
  - `parse_reasoningLegacyField_extractsText` — OpenRouter legacy (`delta.reasoning: "..."`).
  - `parse_reasoningContentField_extractsText` — MiniMax/DeepSeek (`delta.reasoning_content: "..."`).
  - `parse_contentOnlyChunk_noReasoning` — обычный content без reasoning.
  - `parse_mixedChunk_preferDetailsOverLegacy` — приоритет полей.
  - `parse_toolCallDelta_extractsToolCalls` — tool_calls path.
  - `parse_finishChunk_extractsUsage` — usage metadata.
  - `parse_malformedJson_throwsDiagnostic` — error handling.
- `ReasoningRequestBuilderTest`:
  - `build_withEffortHigh_injectsReasoningBlock`.
  - `build_withExcludeTrue_injectsExclude`.
  - `build_withMaxTokens_injectsMaxTokens`.
  - `build_reasoningDisabled_noReasoningField`.
  - `build_preservesExistingOptions` — model/temperature/tools не теряются.
- `ReasoningAwareChatModelTest` (MockWebServer):
  - `stream_reasoningThenContent_emitsSignatureBeforeContent` — фазы reasoning → signature → content.
  - `stream_reasoningThenToolCall_emitsSignatureBeforeToolCall` — signature срабатывает для tool-call-only ответов (критический случай).
  - `stream_pureReasoningNoContent_finalizesCleanly`.
  - `stream_modelNotMatchingPattern_bypassesToDelegate` — bypass по `modelPattern`.
  - `stream_reasoningDisabled_bypassesToDelegate` — bypass по `enabled=false`.
  - `stream_providerError_propagatesGracefully`.
  - `call_delegatesToWrappedModel` — non-stream путь.

### Integration / API Tests (15%)

- `SseStreamingServiceReasoningCompatTest` (`javaclaw-api/javaclaw-api-chat`):
  - `reasoningFromOpenAiCompatProvider_emitsGKadres` — mock `ChatModel` (симулирующий `ReasoningAwareChatModel`) эмитит `AssistantMessage(thinking=true)` → `SseStreamingService` выдаёт `g:` кадры (используется existing паттерн `SseStreamingServiceTest:284-396`).
  - `reasoningFollowedBySignature_closesBlock` — проверка закрытия reasoning-блока через signature.
- `ReasoningAwareChatModelIntegrationTest` (`javaclaw-provider-openai`, Testcontainers не нужны — только WireMock/MockWebServer):
  - `fullRequestLifecycle_withOpenRouterReasoningFormat` — end-to-end unit поверх mock HTTP.

### UI E2E Tests (5%)

- `OpenAiCompatReasoningE2ETest` (Playwright, `javaclaw-e2e`):
  - `reasoningModelResponse_showsReasoningBlock` — запустить чат с reasoning-моделью через mock/stub OpenAI-compat (или через `application-it.yaml` с mock endpoint), проверить что на странице появляется `reasoning-block` с текстом до ответа ассистента. Переиспользовать базу из `ChatScenarioE2ETest` (где уже есть thinking/reasoning паттерн для Anthropic).

## Step by Step Tasks

### 1. Конфигурация и скелеты

- **Task ID**: `scaffold-config`
- **Depends On**: none
- **Assigned To**: `builder-reasoning`
- **Agent Type**: `builder`
- **Stack**: `Java Spring Boot configuration properties maven lombok`
- **Parallel**: false
- **Tests**: Unit: `ReasoningPropertiesTest` (базовый binding properties, default значения, валидация).
- В `javaclaw-provider/javaclaw-provider-openai/pom.xml` добавить test-dep `com.squareup.okhttp3:mockwebserver` (3.14.x или последний совместимый). Проверить наличие `spring-webflux` (через spring-ai-starter-model-openai должно прийти; иначе добавить явно).
- Создать `ReasoningProperties` (`@ConfigurationProperties("javaclaw.reasoning")`) в `javaclaw-provider-openai`: поля `enabled: boolean`, `effort: String (high|medium|low|minimal)`, `exclude: boolean`, `maxTokens: Integer`, `modelPattern: String` (default: `.*(minimax|openrouter|deepseek|o1|o3|gpt-5|claude).*`). JavaDoc явно указывает: `modelPattern` матчится против `Prompt.getOptions().getModel()`.
- Создать `ReasoningAutoConfiguration` с `@AutoConfiguration @EnableConfigurationProperties(ReasoningProperties.class) @ConditionalOnProperty("javaclaw.reasoning.enabled", havingValue="true", matchIfMissing=true)`.
- Создать `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` со строкой `ai.javaclaw.provider.openai.reasoning.ReasoningAutoConfiguration`.
- Прописать дефолты в `javaclaw-app/src/main/resources/application.yaml`: `javaclaw.reasoning.enabled: true`, `effort: medium`.
- Прописать `javaclaw.reasoning.enabled: false` в тестовых профилях `application-integration.yaml` и `application-contracttest.yaml` — чтобы не ломать существующие integration-тесты.
- Создать скелеты классов: `ReasoningAwareChatModel`, `ReasoningChunkParser`, `ReasoningRequestBuilder` в пакете `ai.javaclaw.provider.openai.reasoning`.
- Проверка: `mvn -pl javaclaw-provider/javaclaw-provider-openai -am compile`.

### 2. Парсер SSE chunks

- **Task ID**: `impl-chunk-parser`
- **Depends On**: `scaffold-config`
- **Assigned To**: `builder-reasoning`
- **Agent Type**: `builder`
- **Stack**: `Java Spring Boot jackson pattern matching record`
- **Parallel**: false
- **Tests**: Unit: `ReasoningChunkParserTest` — 8 сценариев (см. Testing Strategy).
- Реализовать `ReasoningChunkParser` в `javaclaw-provider-openai`:
  - Метод `Optional<ParsedChunk> parse(String sseLine)` — удаляет `data: ` префикс, парсит JSON через общий `ObjectMapper`, обрабатывает `[DONE]` (возвращает `Optional.empty()`).
  - Record `ParsedChunk(Optional<String> reasoningText, Optional<String> reasoningId, Optional<String> contentText, List<ToolCallDelta> tools, Optional<String> finishReason, Optional<Usage> usage)`.
  - Приоритет полей: `choices[0].delta.reasoning_details[]` → `choices[0].delta.reasoning` → `choices[0].delta.reasoning_content`.
  - Extract tool_calls в `List<ToolCallDelta>` (совместимо с `AssistantMessage.ToolCall`).
- Написать `ReasoningChunkParserTest`.
- Проверка: `mvn -pl javaclaw-provider/javaclaw-provider-openai test -Dtest=ReasoningChunkParserTest`.

### 3. Билдер запроса

- **Task ID**: `impl-request-builder`
- **Depends On**: `scaffold-config`
- **Assigned To**: `builder-reasoning`
- **Agent Type**: `builder`
- **Stack**: `Java Spring Boot jackson lombok record`
- **Parallel**: true (параллельно с `impl-chunk-parser`)
- **Tests**: Unit: `ReasoningRequestBuilderTest` — 5 сценариев.
- Реализовать `ReasoningRequestBuilder`:
  - Принимает Spring AI `Prompt`, resolved `OpenAiChatOptions`, `ReasoningProperties`.
  - Возвращает `ObjectNode` (Jackson) с полным request body, совместимым с OpenAI chat completions API.
  - Переиспользует Spring AI сериализатор `OpenAiApi.ChatCompletionRequest` для базы, затем добавляет `reasoning: {effort, max_tokens, exclude}` через `ObjectNode.set()`.
- Написать `ReasoningRequestBuilderTest`.

### 4. Кастомный ChatModel

- **Task ID**: `impl-chat-model`
- **Depends On**: `impl-chunk-parser`, `impl-request-builder`
- **Assigned To**: `builder-reasoning`
- **Agent Type**: `builder`
- **Stack**: `Java Spring Boot controller reactor WebFlux virtual thread`
- **Parallel**: false
- **Tests**: Unit: `ReasoningAwareChatModelTest` (MockWebServer) — 7 сценариев (см. Testing Strategy).
- Реализовать `ReasoningAwareChatModel` в `javaclaw-provider-openai`:
  - Конструктор: `ChatModel delegate` (интерфейс — позволяет мокать), `WebClient webClient` (preconfigured), `ReasoningProperties props`, `ReasoningRequestBuilder requestBuilder`, `ReasoningChunkParser parser`, `ObjectMapper mapper`.
  - `call(Prompt) → ChatResponse` — делегирует `delegate.call(prompt)`.
  - `stream(Prompt) → Flux<ChatResponse>`:
    - Bypass: `!props.enabled()` ИЛИ `Prompt.getOptions().getModel()` не матчит `props.modelPattern()` → `delegate.stream(prompt)`.
    - Иначе: строит body через `requestBuilder`, открывает WebClient SSE stream, парсит чанки через `parser`, мапит в `ChatResponse` с метадатой.
    - State machine: `INIT → REASONING → POST_REASONING → FINISH`. Переход REASONING→POST_REASONING срабатывает на первом non-reasoning чанке (content, tool_call, или finish) и эмитит signature chunk ПЕРЕД эмитом самого переходного чанка.
  - Tool calls: агрегирование delta по `index` (как в Spring AI `OpenAiChatModel`), создание `AssistantMessage.ToolCall` объектов.
- Написать `ReasoningAwareChatModelTest` с MockWebServer (фиксированные SSE sequences для каждого из 7 сценариев).

### 5. DI интеграция

- **Task ID**: `wire-di`
- **Depends On**: `impl-chat-model`
- **Assigned To**: `builder-reasoning`
- **Agent Type**: `builder`
- **Stack**: `Java Spring Boot controller entity`
- **Parallel**: false
- **Tests**: Integration: проверка через existing тесты `ChatServiceTest`, `FallbackChatModelTest` (остаются зелёными, нет регрессии).
- В `ReasoningAutoConfiguration` создать `@Bean ReasoningAwareChatModel reasoningAwareChatModel(ObjectProvider<OpenAiChatModel> openAiChatModelProvider, WebClient.Builder webClientBuilder, ReasoningProperties props, ...)`:
  - Внутри: `OpenAiChatModel delegate = openAiChatModelProvider.getIfAvailable()` — достаёт **именно** raw autoconfigured bean (по конкретному типу, минуя `@Primary FallbackChatModel`).
  - Если `delegate == null` → конфигурация не применяется (провайдер OpenAI не подключён).
  - WebClient настраивается с `baseUrl` из `spring.ai.openai.base-url` + `Authorization: Bearer ${spring.ai.openai.api-key}`.
- Обновить `ChatServiceConfiguration.fallbackChatModel()`:
  - Изменить filter lambda: `filter(m -> !(m instanceof FallbackChatModel))` → `filter(m -> !(m instanceof FallbackChatModel)).sorted((a,b) -> Boolean.compare(!(a instanceof ReasoningAwareChatModel), !(b instanceof ReasoningAwareChatModel)))` — приоритет `ReasoningAwareChatModel`, fallback на raw `OpenAiChatModel`.
  - Если `ReasoningAwareChatModel` не в classpath (зависит от наличия `javaclaw-provider-openai`) — делать через `Class.forName` проверку или просто `instanceof ChatModel`-sort без жёсткой ссылки (чтобы `javaclaw-core` не тянуло `javaclaw-provider-openai` как compile-dep; компилятор не увидит класс, но runtime check работает через reflection или marker-interface).
  - **Альтернатива** (упростить): добавить marker-интерфейс `ReasoningCapableChatModel` в `javaclaw-core` (пустой), который реализует `ReasoningAwareChatModel`. Тогда фильтр в `javaclaw-core` может проверять `instanceof ReasoningCapableChatModel` без compile-dep на `javaclaw-provider-openai`.
- Проверка: `mvn -pl javaclaw-core,javaclaw-provider/javaclaw-provider-openai test -Dtest='ChatServiceTest,FallbackChatModelTest,ReasoningAutoConfigurationTest'`.

### 6. Integration-тесты SseStreamingService

- **Task ID**: `integration-tests-sse`
- **Depends On**: `wire-di`
- **Assigned To**: `builder-tests`
- **Agent Type**: `builder`
- **Stack**: `Java Spring Boot mockmvc http test mockito assertj allure test structure`
- **Parallel**: false
- **Tests**: Integration: `SseStreamingServiceReasoningCompatTest`.
- Написать `SseStreamingServiceReasoningCompatTest` (в `javaclaw-api-chat/src/test/java/.../service/`):
  - Mock `ChatService.stream(...)` возвращает `Flux<ChatResponse>` с последовательностью: `AssistantMessage("думаю", {thinking:true})`, `AssistantMessage("", {signature:"sig-1"})`, `AssistantMessage("ответ")`.
  - Ассерты: `SseStreamingService` эмитит `f:` (MessageStart) → `g:` (ReasoningStart + Delta) → `g:` (ReasoningEnd с signature) → `0:` (TextStart + Delta) → `e:` → `d:`.
  - Использовать шаблон `SseStreamingServiceTest:284-396` как образец.
- Проверка: `mvn -pl javaclaw-api/javaclaw-api-chat test -Dtest=SseStreamingServiceReasoningCompatTest`.

### 7. E2E Playwright с WireMock

- **Task ID**: `e2e-playwright`
- **Depends On**: `integration-tests-sse`
- **Assigned To**: `builder-tests`
- **Agent Type**: `builder`
- **Stack**: `Java Spring Boot testcontainers integration test selenide e2e page object`
- **Parallel**: false
- **Tests**: E2E: `OpenAiCompatReasoningE2ETest`.
- Добавить в `javaclaw-e2e/pom.xml` test-scope зависимость `org.wiremock:wiremock-standalone` (последнюю совместимую), если отсутствует.
- Написать `OpenAiCompatReasoningE2ETest` (`javaclaw-e2e/src/test/java/ai/javaclaw/e2e/playwright/`):
  - Использовать `PlaywrightE2ETestBase` как базу.
  - Стартовать WireMock через `@RegisterExtension WireMockExtension wireMock = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build()` **до** старта Spring context (через `@BeforeAll` + `@DynamicPropertySource` для `spring.ai.openai.base-url = http://localhost:${wireMock.port}` и `spring.ai.openai.api-key = test`).
  - Stub WireMock: `stubFor(post("/v1/chat/completions").willReturn(aResponse().withStatus(200).withHeader("Content-Type", "text/event-stream").withBody(<fixture SSE с reasoning_details[] + content>)))`.
  - Fixture: последовательность SSE `data: {...reasoning_details[{type:reasoning.text,text:"Думаю..."}]...}`, `data: {...reasoning_details[{type:reasoning.text,text:" и решаю"}]...}`, `data: {...content:"Ответ: 42"...}`, `data: [DONE]`.
  - Открыть чат UI → отправить сообщение → дождаться появления `[data-testid="reasoning-block"]` с текстом `Думаю и решаю` → дождаться появления `Ответ: 42` в основном сообщении.
  - Проверка: `mvn -pl javaclaw-e2e test -Dtest=OpenAiCompatReasoningE2ETest`.

### 8. Write Tests (сводная фаза)

- **Task ID**: `write-tests`
- **Depends On**: `impl-chunk-parser`, `impl-request-builder`, `impl-chat-model`, `wire-di`, `integration-tests-sse`, `e2e-playwright`
- **Assigned To**: `builder-tests`
- **Agent Type**: `builder`
- **Stack**: `Java MockMvc Mockito assertj allure test structure integration test testcontainers mockwebserver`
- **Parallel**: false
- Гарантировать что все unit-тесты из Testing Strategy написаны и зелёные (финальный проход по списку).
- Гарантировать что integration-тесты следуют existing паттернам (Allure annotations, AssertJ, no field injection).
- Гарантировать что E2E использует data-testid локаторы (существующая конвенция).
- Финальный прогон: `mvn test` на всём проекте, счёт `+N` новых тестов — зафиксировать в коммит-сообщении.

### 9. Финальная валидация

- **Task ID**: `validate-all`
- **Depends On**: `write-tests`
- **Assigned To**: `validator-reasoning`
- **Agent Type**: `validator`
- **Stack**: `Java Spring Boot maven surefire failsafe jacoco testcontainers integration test`
- **Parallel**: false
- Прогнать `mvn clean test -q` — все тесты зелёные.
- Прогнать `mvn -pl javaclaw-e2e test -q` — E2E зелёные.
- Проверить acceptance criteria (ниже).
- Выполнить manual-smoke curl через local app (инструкция ниже).
- Обновить `specs/chat-runtime-bug-fixes-e2e-coverage.md` статус #2_thinking → DONE.

## Acceptance Criteria

1. В запросах к OpenRouter body содержит `"reasoning": {"effort": "medium"}` (или другое значение из конфига) — проверяется unit-тестом `ReasoningRequestBuilderTest`.
2. `ReasoningAwareChatModel.stream()` для mock SSE с `reasoning_details[]` эмитит минимум один `ChatResponse` с `AssistantMessage.properties.get("thinking") == true` ДО первого content-чанка — проверяется `ReasoningAwareChatModelTest`.
3. После последнего reasoning-чанка и до первого content-чанка эмитится `ChatResponse` с `properties.containsKey("signature")` — для закрытия блока в `SseStreamingService`.
4. `SseStreamingService` при получении такого потока выдаёт `g:` кадры в SSE-ответе — проверяется `SseStreamingServiceReasoningCompatTest`.
5. E2E: frontend `reasoning-block` заполнен текстом до появления основного ответа — проверяется `OpenAiCompatReasoningE2ETest`.
6. Все существующие тесты (1187+) остаются зелёными — regression safety.
7. Если `javaclaw.reasoning.enabled: false` → делегирование в `OpenAiChatModel` без изменений, `g:` кадры не эмитятся — проверяется unit-тестом.
8. Tool calling работает без регрессий (reasoning не мешает function calling) — покрывается существующими `ChatServiceTest` + новым сценарием `stream_toolCallAfterReasoning_emitsToolCall`.

## Validation Commands

```bash
# Компиляция
mvn -pl javaclaw-provider/javaclaw-provider-openai -am compile

# Unit-тесты по новым классам
mvn -pl javaclaw-provider/javaclaw-provider-openai test -Dtest='ReasoningChunkParserTest,ReasoningRequestBuilderTest,ReasoningAwareChatModelTest,ReasoningPropertiesTest'

# Integration-тесты SseStreamingService
mvn -pl javaclaw-api/javaclaw-api-chat test -Dtest='SseStreamingServiceReasoningCompatTest,SseStreamingServiceTest'

# Regression suite (core + provider + api-chat)
mvn -pl javaclaw-core,javaclaw-provider/javaclaw-provider-openai,javaclaw-api/javaclaw-api-chat -am test

# E2E
mvn -pl javaclaw-e2e test -Dtest=OpenAiCompatReasoningE2ETest

# Полный прогон
mvn clean test

# Manual smoke test (local app running)
curl -N -X POST http://localhost:8080/api/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"conversationId":"test-reasoning","content":"Реши 2+2 с размышлениями"}' \
| grep -E '^(g|0):' | head -20
# Ожидание: сначала несколько g: кадров, затем 0: кадры.
```

## Notes

- **Spring AI 2.0.0-SNAPSHOT**: API всё ещё меняется. `OpenAiChatOptions.Builder` может иметь разные способы передачи extra parameters — builder должен проверить актуальный API в `mvn dependency:sources` или через IDE.
- **API key**: `ReasoningAwareChatModel` должен получать Authorization header тем же способом, что Spring AI (свойство `spring.ai.openai.api-key`). Инжектим через `@Value("${spring.ai.openai.api-key}")`.
- **Base URL**: `@Value("${spring.ai.openai.base-url}")` = `https://openrouter.ai/api`, endpoint `/v1/chat/completions`.
- **Dependencies**: `spring-webflux` (WebClient) уже должен быть транзитивно через spring-ai-openai; если нет — добавить явно в `javaclaw-core/pom.xml`. `okhttp3.mockwebserver` для тестов — проверить наличие, при необходимости `mvn add`.
- **Out of scope**: поддержка reasoning для non-stream режима (`call(Prompt)`), поддержка Google Gemini reasoning format (только OpenRouter/MiniMax в scope).
- **Backward compatibility**: при `reasoning.enabled=false` поведение идентично текущему — нет риска regression для Anthropic-пути (он не проходит через `ReasoningAwareChatModel`, т.к. Anthropic использует отдельный `AnthropicChatModel` bean).
- **Обновление спеки**: после завершения обновить `specs/chat-runtime-bug-fixes-e2e-coverage.md` — изменить статус `#2_thinking` с GAP на DONE.

