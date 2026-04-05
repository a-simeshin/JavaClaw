# Plan: OpenAPI 3.1 API Contract (Roadmap 0.1 + 3.1)

## Task Description

Создать исчерпывающий OpenAPI 3.1.0 контракт для всего текущего REST API JavaClaw, зафиксировав фактическое состояние контроллеров из `javaclaw-api/` и `javaclaw-app/`. Файл `specs/openapi.yaml` становится **единственным источником правды** для REST-контрактов и базой для:

1. Генерации типизированных клиентов во фронтенде (TypeScript, `openapi-typescript` / `orval`)
2. Контрактных тестов (REST Assured / MockMvc с `SwaggerRequestValidator`)
3. Документации (Swagger UI через `springdoc-openapi-starter-webmvc-ui` в последующей задаче 3.6 Actuator)
4. Стабилизации контрактов перед мутацией persistence (1.2 VirtualFile → DB, 1.4 Skills → JDBC, 1.5 MCP → JDBC) — контракты остаются теми же, меняется только реализация

Закрывает пункты родмапа **0.1** (Спека: API contract) и **3.1** (API contract спека перед Phase 3 доработками).

## Objective

По завершении:

- `specs/openapi.yaml` валиден по OpenAPI 3.1.0 spec (swagger-cli/redocly lint passes)
- Содержит **все 20 активных endpoints** + 2 deprecated (помечены `deprecated: true`)
- Содержит **все 13 DTO** как `components.schemas` с правильной типизацией (Instant → `date-time`, Records → objects, Map<String,String> → additionalProperties)
- Описывает SSE streaming endpoint (`/api/chat/send`) с кастомным content-type `text/plain` + header `x-vercel-ai-data-stream: v1` + документацией Vercel AI SDK v4 frame codes
- Описывает error envelope `ApiError` как переиспользуемый response для 400/404/429/500
- Security scheme `basicAuth` зарегистрирован (применится в 4.1)
- Все параметры с validation-ограничениями (`minLength`, `maxLength`, `pattern`) соответствуют Jakarta Bean Validation аннотациям из кода
- Есть `specs/openapi.yaml` + контрактные тесты, гарантирующие что контроллеры НЕ РАСХОДЯТСЯ со схемой

## Problem Statement

Текущее состояние:

- **Нет формального API-контракта.** Фронтенд (`javaclaw-frontend`) ходит в API по хардкод-путям без типизированного клиента; изменение DTO в Java ломает фронт молча.
- **Нет контрактных тестов.** Существующие интеграционные тесты (MockMvc) проверяют поведение, но не соответствие схеме.
- **Сложно онбордить новых разработчиков** — API размазан по 8 контроллерам в 3 модулях, нет единой точки.
- **SSE протокол недокументирован** — Vercel AI SDK v4 frame codes (0, 2, 3, 9, a, d, e) нигде не зафиксированы и реверс-инжинирятся из `SseStreamingService`.
- **Перед миграцией persistence (Phase 1.2-1.5)** критично зафиксировать контракт, чтобы смена хранения не ломала фронтенд.

## Solution Approach

**Подход: spec-first manually-authored OpenAPI 3.1 YAML** (НЕ генерация из кода через springdoc на этом этапе).

Причины:

1. Spring AI + ResponseBodyEmitter плохо описываются springdoc автогенератором — SSE-контракт придётся доопределять вручную
2. Хотим контракт-first подход: yaml - контракт, код обязан соответствовать (а не наоборот)
3. Отсутствие springdoc-зависимостей позволяет не грузить app jar лишними KB
4. После P0 COMPLETE (когда контракт стабилизирован) — можно подключить springdoc с `springdoc.swagger-ui.url=/openapi.yaml` чтобы Swagger UI читал наш "золотой" yaml

**Структура `specs/openapi.yaml`:**

```yaml
openapi: 3.1.0
info: {title, version, description, license}
servers: [{url: /}]
tags: [chat, conversations, files, skills, mcp-servers, system]
security: [{basicAuth: []}]  # применится в 4.1
paths:
  /api/chat/send: {post: ...}
  /api/chat/stream/{conversationId}: {post: ...}
  /api/conversations: {get, post, delete}
  /api/conversations/{id}/messages: {get}
  /api/files: {get, post}
  /api/files/{path}: {get, put, delete}  # path matched as /api/files/** in code
  /api/mcp-servers: {get, post}
  /api/mcp-servers/{id}: {put, delete}
  /api/mcp-servers/{id}/status: {get}
  /api/skills: {get, post}
  /api/skills/{id}: {put, delete}
  /api/me: {get}
  /api/health: {get}
components:
  schemas: {13 DTO}
  responses: {ApiError400, ApiError404, ApiError429}
  securitySchemes: {basicAuth}
  parameters: {pageParam, sizeParam}
```

**Полная таблица 22 endpoint (перед стартом builder-openapi):**

| #  | Method |               Path                |       operationId       |      Tag      | Deprecated |
|----|--------|-----------------------------------|-------------------------|---------------|------------|
| 1  | POST   | /api/chat/send                    | sendChatMessage         | chat          | no         |
| 2  | POST   | /api/chat/stream/{conversationId} | reopenChatStream        | chat          | no         |
| 3  | GET    | /api/conversations                | listConversations       | conversations | no         |
| 4  | POST   | /api/conversations                | createConversation      | conversations | no         |
| 5  | GET    | /api/conversations/{id}/messages  | getConversationMessages | conversations | no         |
| 6  | DELETE | /api/conversations/{id}           | deleteConversation      | conversations | no         |
| 7  | GET    | /api/files                        | getFileTree             | files         | no         |
| 8  | GET    | /api/files/{path}                 | readFile                | files         | no         |
| 9  | PUT    | /api/files/{path}                 | writeFile               | files         | no         |
| 10 | DELETE | /api/files/{path}                 | deleteFile              | files         | no         |
| 11 | POST   | /api/files                        | createFile              | files         | no         |
| 12 | GET    | /api/mcp-servers                  | listMcpServers          | mcp-servers   | no         |
| 13 | POST   | /api/mcp-servers                  | createMcpServer         | mcp-servers   | no         |
| 14 | PUT    | /api/mcp-servers/{id}             | updateMcpServer         | mcp-servers   | no         |
| 15 | DELETE | /api/mcp-servers/{id}             | deleteMcpServer         | mcp-servers   | no         |
| 16 | GET    | /api/mcp-servers/{id}/status      | getMcpServerStatus      | mcp-servers   | no         |
| 17 | GET    | /api/skills                       | listSkills              | skills        | no         |
| 18 | POST   | /api/skills                       | createSkill             | skills        | no         |
| 19 | PUT    | /api/skills/{id}                  | updateSkill             | skills        | no         |
| 20 | DELETE | /api/skills/{id}                  | deleteSkill             | skills        | no         |
| 21 | GET    | /api/me                           | getCurrentUser          | system        | no         |
| 22 | GET    | /api/health                       | getHealth               | system        | no         |
| 23 | GET    | /legacy/chat                      | legacyChatView          | legacy        | yes        |
| 24 | GET    | /index                            | legacyIndex             | legacy        | yes        |

(20 active + 2 deprecated legacy = 22 targeted; 24 total including legacy)

**Ключевые решения по схеме:**

|           Тема           |                                                              Решение                                                              |
|--------------------------|-----------------------------------------------------------------------------------------------------------------------------------|
| `Instant` → `format`     | `type: string, format: date-time` (ISO-8601)                                                                                      |
| `record` → schema        | `type: object, required: [...], properties: {...}`                                                                                |
| `Map<String,String>`     | `type: object, additionalProperties: {type: string}`                                                                              |
| Null vs missing          | JSR-380: `@NotBlank`/`@NotNull` → в `required:`; optional → не в `required:`                                                      |
| `PageResponse<T>`        | Generic через `allOf` + `content: items: $ref`                                                                                    |
| SSE response             | `content: text/plain: schema: {type: string}` + описание frame protocol в `description`                                           |
| `/api/files/**` wildcard | `parameters: - {in: path, name: path, schema: {type: string, pattern: '^(?!/).+'}}` + `x-extension: "matches AntPathMatcher /**"` |
| Deprecated endpoints     | `deprecated: true` + описание почему                                                                                              |
| Auth на будущее          | `securitySchemes.basicAuth` описан, но `security` на операциях пока `[]` — включится в 4.1                                        |

## Relevant Files

### Источники контрактов (read-only для плана, НЕ редактируем)

- `javaclaw-api/javaclaw-api-chat/src/main/java/ai/javaclaw/api/chat/rest/ChatRestController.java` — POST `/api/chat/send`, POST `/api/chat/stream/{id}`
- `javaclaw-api/javaclaw-api-chat/src/main/java/ai/javaclaw/api/chat/rest/ConversationController.java` — CRUD `/api/conversations`
- `javaclaw-api/javaclaw-api-chat/src/main/java/ai/javaclaw/api/chat/rest/SseStreamingService.java` — SSE frame protocol (Vercel AI SDK v4)
- `javaclaw-api/javaclaw-api-chat/src/main/java/ai/javaclaw/api/chat/rest/VercelSseEvent.java` — типы SSE событий
- `javaclaw-api/javaclaw-api-chat/src/main/java/ai/javaclaw/api/chat/rest/ChatSendRequest.java`
- `javaclaw-api/javaclaw-api-chat/src/main/java/ai/javaclaw/api/chat/rest/CreateConversationRequest.java`
- `javaclaw-api/javaclaw-api-chat/src/main/java/ai/javaclaw/api/chat/rest/ConversationDto.java`
- `javaclaw-api/javaclaw-api-chat/src/main/java/ai/javaclaw/api/chat/rest/MessageDto.java`
- `javaclaw-api/javaclaw-api-chat/src/main/java/ai/javaclaw/api/chat/rest/PageResponse.java`
- `javaclaw-api/javaclaw-api-admin/src/main/java/ai/javaclaw/api/admin/files/FileController.java` — CRUD `/api/files`, wildcard `/**`
- `javaclaw-api/javaclaw-api-admin/src/main/java/ai/javaclaw/api/admin/files/FileNodeDto.java`
- `javaclaw-api/javaclaw-api-admin/src/main/java/ai/javaclaw/api/admin/files/FileContentDto.java`
- `javaclaw-api/javaclaw-api-admin/src/main/java/ai/javaclaw/api/admin/files/CreateFileRequest.java`
- `javaclaw-api/javaclaw-api-admin/src/main/java/ai/javaclaw/api/admin/mcp/McpServerController.java` — CRUD `/api/mcp-servers`
- `javaclaw-api/javaclaw-api-admin/src/main/java/ai/javaclaw/api/admin/mcp/McpServerDto.java`
- `javaclaw-api/javaclaw-api-admin/src/main/java/ai/javaclaw/api/admin/mcp/McpServerStatusDto.java`
- `javaclaw-api/javaclaw-api-admin/src/main/java/ai/javaclaw/api/admin/skills/SkillController.java` — CRUD `/api/skills`
- `javaclaw-api/javaclaw-api-admin/src/main/java/ai/javaclaw/api/admin/skills/SkillDto.java`
- `javaclaw-api/javaclaw-api-admin/src/main/java/ai/javaclaw/api/admin/AdminExceptionHandler.java` — error mapping
- `javaclaw-api/javaclaw-api-admin/src/main/java/ai/javaclaw/api/admin/ApiError.java`
- `javaclaw-app/src/main/java/ai/javaclaw/api/SystemController.java` — `/api/me`, `/api/health`

### Референсные конфиги для тестирования

- `pom.xml` (корневой) — проверить версии Spring Boot, подтянуть тестовые зависимости
- `javaclaw-app/src/test/java/**/*Test.java` — паттерны существующих интеграционных тестов (MockMvc + AssertJ + Testcontainers)
- `javaclaw-app/src/test/resources/application-test.yaml` — тест-конфиг

### New Files

- `specs/openapi.yaml` — основной контракт OpenAPI 3.1.0
- `javaclaw-app/src/test/java/ai/javaclaw/contract/OpenApiSchemaValidationTest.java` — unit-тесты валидации самой схемы (swagger-parser)
- `javaclaw-app/src/test/java/ai/javaclaw/contract/OpenApiContractComplianceTest.java` — интеграционные тесты MockMvc + SwaggerRequestValidator (atlassian-openapi-schema-validator или aoss-swagger-request-validator)
- `javaclaw-app/src/test/resources/contract/openapi.yaml` — копия/симлинк для тестов (или `@TestPropertySource(locations="classpath:openapi.yaml")`)
- `docs/api/README.md` — как читать контракт, как добавлять endpoints, как запускать contract-compliance тесты

## Implementation Phases

### Phase 1: Foundation (authoring yaml)

- Создать скелет `specs/openapi.yaml` с `info`, `servers`, `tags`, `security`, пустыми `paths` и `components`
- Описать reusable `components`: `ApiError`, стандартные responses, `basicAuth` security scheme, pagination parameters
- Прогнать через `redocly lint` / `swagger-cli validate` для базовой валидности

### Phase 2: Core Implementation (endpoints + schemas)

- Описать все 13 DTO в `components.schemas` с полной типизацией и validation constraints
- Описать все 22 endpoint (20 active + 2 deprecated) с параметрами, request/response body, error codes
- Особо задокументировать SSE: frame codes 0/2/3/9/a/d/e, heartbeat, disconnect semantics, Vercel AI SDK v4 совместимость
- Кросс-линки: каждый endpoint ссылается на schema через `$ref`
- `x-codegen-*` расширения для будущей кодогенерации фронтенда

### Phase 3: Integration & Polish (contract tests + docs)

- Написать `OpenApiSchemaValidationTest` — unit: yaml парсится, все `$ref` резолвятся, нет orphan schemas, все endpoints имеют operationId
- Написать `OpenApiContractComplianceTest` — integration: для каждого endpoint прогнать MockMvc request с валидным body и проверить соответствие response schema через `OpenApiValidationFilter` или ручной `SchemaValidator`
- Пройтись по фронтенду (`javaclaw-frontend/src/lib/api`) и проверить что хардкод-пути совпадают со схемой (вручную на этом этапе, автогенерация позже)
- Добавить `docs/api/README.md` с инструкциями
- `mvn verify` должен включать запуск contract-compliance тестов

## Team Orchestration

- Я (orchestrator) НЕ пишу yaml руками и не трогаю код — делегирую сабагентам.
- Сабагенты читают этот план, выполняют свою задачу и репортят через TaskUpdate.

### Team Members

- **Builder-OpenAPI**
  - Name: `builder-openapi`
  - Role: Пишет `specs/openapi.yaml` — полный контракт с всеми endpoints, схемами, валидациями, SSE-документацией, error envelope, security scheme.
  - Agent Type: `builder`
  - Resume: true
- **Builder-Docs**
  - Name: `builder-docs`
  - Role: Пишет `docs/api/README.md` (как читать/расширять контракт, как запускать contract-тесты, где хранятся схемы для генерации клиента).
  - Agent Type: `builder`
  - Resume: true
- **Test-Builder**
  - Name: `test-builder-contract`
  - Role: Пишет unit (OpenApiSchemaValidationTest через swagger-parser) + integration (OpenApiContractComplianceTest через MockMvc + aoss-swagger-request-validator) + 1 E2E-smoke тест в браузере через Playwright/Selenide проверяющий что SPA успешно логинится и отправляет chat-сообщение.
  - Agent Type: `builder`
  - Resume: true
- **Validator**
  - Name: `validator-senior-tester`
  - Role: Senior-тестировщик. Верифицирует: (1) yaml валиден через `redocly lint` и `swagger-cli validate`, (2) все contract-тесты зелёные через `mvn verify`, (3) реально запускает E2E с Chrome через mcp__claude-in-chrome, (4) оценивает UI/UX через реальный браузер на login page + chat, (5) сверяет фронтенд-вызовы со схемой. Возвращает PASS/FAIL.
  - Agent Type: `validator`
  - Resume: true

## Testing Strategy

Test pyramid ratio: **80% unit / 15% integration-API / 5% UI e2e**

### Unit Tests (80%)

Файл: `javaclaw-app/src/test/java/ai/javaclaw/contract/OpenApiSchemaValidationTest.java`

- `specOpens_isValidOpenApi31()` — парсит yaml через `io.swagger.v3.parser.OpenAPIV3Parser`, проверяет отсутствие messages
- `allReferencesResolve()` — рекурсивно обходит `$ref`, fails если ссылка не резолвится
- `everyOperationHasOperationId()` — для контрактных клиентов и кодогенерации
- `everyOperationHasResponses_2xx_4xx()` — каждая операция имеет минимум 2xx и 4xx (400) ответы
- `errorResponses_referenceApiError()` — все 400/404/422/500 ссылаются на `ApiError` schema
- `dtoSchemas_matchJavaRecords()` — через reflection проходим по Java records и сверяем имена полей со схемой (fails при расхождении)
- `validationConstraints_matchAnnotations()` — `@NotBlank` → поле в `required:`; `@Size(max=N)` → `maxLength: N`; `@Pattern` → `pattern:`
- `noOrphanSchemas()` — каждая schema в `components.schemas` используется хотя бы одним endpoint или другой schema
- `sseEndpoint_documentsFrameCodes()` — POST `/api/chat/send` описание содержит упоминания кодов `0:`, `3:`, `9:`, `a:`, `d:`, `e:`

### Integration / API Tests (15%)

Файл: `javaclaw-app/src/test/java/ai/javaclaw/contract/OpenApiContractComplianceTest.java`

- `@SpringBootTest(webEnvironment = MOCK)` + `@AutoConfigureMockMvc` + Testcontainers PostgreSQL (чтобы схема БД поднялась)
- Используем `com.atlassian.oai:swagger-request-validator-mockmvc:2.x` для автоматической проверки каждого MockMvc request/response против openapi.yaml
- По одному тесту на endpoint:
  - `getConversations_returnsValidPageResponse()` — GET `/api/conversations?page=0&size=20`
  - `createConversation_returnsValidDto()` — POST `/api/conversations` с валидным body
  - `createConversation_rejectsOversizeTitle()` — POST с title длиной 121 → 400 ApiError
  - `getMessages_byConversationId()` — GET `/api/conversations/{id}/messages`
  - `deleteConversation_returns204()` — DELETE
  - `listFiles_returnsTree()` — GET `/api/files`
  - `readFile_byWildcardPath()` — GET `/api/files/src/Main.java`
  - `writeFile_acceptsContent()` — PUT
  - `createFile_rejectsBlankPath()` — POST `/api/files` с `path=""` → 400
  - `listMcpServers()` + `createMcpServer()` + `updateMcpServer()` + `deleteMcpServer()` + `mcpServerStatus()`
  - `createMcpServer_rejectsInvalidTransport()` — transport="ftp" → 400 (нарушает `@Pattern(stdio|http)`)
  - `listSkills()` + `createSkill()` + `updateSkill()` + `deleteSkill()`
  - `getMe_returnsGuestWhenNoPrincipal()` — без auth
  - `getHealth_returnsUp()`
  - `sendChatMessage_returnsSseStream()` — отдельный тест с проверкой content-type + frame format (assert стартует с `0:`)

### UI E2E Tests (5%)

Файл: `javaclaw-e2e/src/test/java/ai/javaclaw/e2e/ChatFlowE2ETest.java` (если модуль javaclaw-e2e есть, иначе создаётся)

- Реальный Chrome через Playwright/Selenide (проверить наличие модуля, адаптировать)
- Один critical flow: `chatFlow_fullUserJourney()`
  1. Открыть `http://localhost:8080/`
  2. Перейти на `/login`, ввести логин/пароль (admin/admin или guest если Phase 4 ещё не готова)
  3. Редирект на `/chat` — проверить наличие chat-input
  4. Отправить сообщение "Hello"
  5. Дождаться SSE streaming ответа — verify DOM обновляется прогрессивно (появляется typing indicator, затем текст)
  6. Проверить что в сайдбаре появился новый conversation entry
  7. Открыть DevTools network log → verify fetch запрос на `POST /api/chat/send` с валидным payload соответствующим schema
  8. Скриншот + GIF записать в `target/e2e-screenshots/`
- `navigation_allCriticalRoutesLoad()` — /chat, /conversations, /admin, /overview не 500/404
- Visual UI/UX assessment: провести senior-tester-критику (читаемость, spacing, feedback при загрузке, error states)

## Step by Step Tasks

### 0. Pre-flight exploration (существующая структура)

- **Task ID**: preflight-exploration
- **Depends On**: none
- **Assigned To**: builder-openapi
- **Agent Type**: builder
- **Stack**: Java Spring Boot maven
- **Parallel**: false
- **Tests**: none
- Проверить существование модуля `javaclaw-e2e` (ls корня + pom.xml). Зафиксировать: (а) существует ли (б) использует ли Selenide или Playwright
- Проверить Spring Boot версию в корневом pom.xml для выбора совместимой версии `swagger-request-validator-mockmvc` (2.44.x для Spring Boot 3.2+, 2.35.x для 3.0-3.1)
- Через Context7 получить актуальные docs: `swagger-parser` v3, `swagger-request-validator-mockmvc` compatibility matrix
- Записать найденные решения в comment в начало `specs/openapi.yaml` и в `docs/api/README.md`

### 1. Write specs/openapi.yaml foundation

- **Task ID**: write-openapi-foundation
- **Depends On**: none
- **Assigned To**: builder-openapi
- **Agent Type**: builder
- **Stack**: Java Spring Boot controller exception error handling
- **Parallel**: false
- **Tests**: покрывается задачей write-contract-tests (schema validation unit tests)
- Прочитать все файлы контроллеров и DTO из Relevant Files
- Создать `specs/openapi.yaml` с секциями: `openapi: 3.1.0`, `info`, `servers: [{url: /}]`, `tags`, `security` (декларативно), пустые `paths` и `components`
- В `components.schemas` положить `ApiError` первым (он используется всеми error responses)
- В `components.securitySchemes.basicAuth` положить Basic Auth scheme (для будущей Phase 4.1)
- В `components.parameters` положить переиспользуемые `PageParam` и `SizeParam`
- В `components.responses` положить `Error400`, `Error404`, `Error429`, `Error500` ссылающиеся на `ApiError`
- Прогнать `npx @redocly/cli lint specs/openapi.yaml` локально — должно быть без warnings (или с допустимыми)

### 2. Add all DTOs to components.schemas

- **Task ID**: write-openapi-schemas
- **Depends On**: write-openapi-foundation
- **Assigned To**: builder-openapi
- **Agent Type**: builder
- **Stack**: Java Spring Boot entity record
- **Parallel**: false
- **Tests**: dtoSchemas_matchJavaRecords() в OpenApiSchemaValidationTest
- Описать 13 DTO: ChatSendRequest, CreateConversationRequest, ConversationDto, MessageDto, PageResponse (через generic), FileContentDto, CreateFileRequest, FileNodeDto (с self-referencing children), McpServerDto, McpServerStatusDto, SkillDto, ApiError, UserInfoDto
- Для каждого DTO: `type: object`, `required:` из `@NotBlank`/`@NotNull`, `properties:` с типами, description каждого поля
- Маппинг: `Instant` → `format: date-time`, `int` → `format: int32`, `long` → `format: int64`, `Map<String,String>` → `additionalProperties: {type: string}`
- FileNodeDto: рекурсивная self-reference для `children: {type: array, items: $ref: '#/components/schemas/FileNodeDto', nullable: true}`
- PageResponse<T>: generic через discriminator ИЛИ отдельная schema `PageResponse_ConversationDto` + `PageResponse_MessageDto` (проще для кодогенерации)
- Validation: `@Size(max=120) String name` → `name: {type: string, maxLength: 120}`
- `@Pattern(regexp = "stdio|http")` → `pattern: '^(stdio|http)$'` + `enum: [stdio, http]` для удобства клиента

### 3. Describe all paths + operations

- **Task ID**: write-openapi-paths
- **Depends On**: write-openapi-schemas
- **Assigned To**: builder-openapi
- **Agent Type**: builder
- **Stack**: Java Spring Boot controller
- **Parallel**: false
- **Tests**: OpenApiContractComplianceTest — каждый endpoint проверяется
- Chat: POST `/api/chat/send` (SSE, content-type text/plain, x-vercel-ai-data-stream header в описании), POST `/api/chat/stream/{conversationId}`
- Conversations: GET `/api/conversations` (+ pageParam/sizeParam), POST `/api/conversations` (body optional), GET `/api/conversations/{id}/messages`, DELETE `/api/conversations/{id}`
- Files: GET `/api/files` (tree), GET `/api/files/{path}` (path wildcard — описать через `style: simple, explode: false` или `allowReserved: true`), PUT `/api/files/{path}`, DELETE `/api/files/{path}`, POST `/api/files`
- MCP: GET `/api/mcp-servers`, POST `/api/mcp-servers`, PUT `/api/mcp-servers/{id}`, DELETE `/api/mcp-servers/{id}`, GET `/api/mcp-servers/{id}/status`
- Skills: GET/POST `/api/skills`, PUT/DELETE `/api/skills/{id}`
- System: GET `/api/me`, GET `/api/health`
- Deprecated: GET `/legacy/chat`, GET `/index` с `deprecated: true`
- Каждая операция: `operationId` (camelCase), `summary`, `description`, `tags`, `parameters`, `requestBody` (если есть), `responses`, опционально `security: []`
- Error responses: `400: {$ref: '#/components/responses/Error400'}`, `404: ...`, `429: ...`
- SSE endpoint `description` обязательно содержит markdown table с frame codes и примерами (скопировать из отчёта explore-агента)

### 4. Write contract compliance test harness

- **Task ID**: write-contract-tests
- **Depends On**: write-openapi-paths
- **Assigned To**: test-builder-contract
- **Agent Type**: builder
- **Stack**: Java MockMvc integration test Testcontainers assertj allure test structure
- **Parallel**: false
- **Tests**: это сами тесты
- Добавить зависимости в `javaclaw-app/pom.xml` (scope=test): `io.swagger.parser.v3:swagger-parser:2.1.x`, `com.atlassian.oai:swagger-request-validator-mockmvc:2.44.x`
- Создать `OpenApiSchemaValidationTest.java` со всеми unit-тестами из Testing Strategy (9 кейсов)
- Создать `OpenApiContractComplianceTest.java` с `@SpringBootTest` + `@AutoConfigureMockMvc` + Testcontainers PostgreSQL; каждый endpoint — отдельный @Test с AssertJ ассертами + `OpenApiValidationFilter`
- В `MockMvcConfigurer` подключить `OpenApiValidationFilter(specFile)` чтобы каждый request/response автоматически валидировался против схемы
- Для SSE endpoint: проверить content-type, начало тела (`0:`), наличие `e:` + `d:` frames
- Negative tests: оверсайз title (121 char), пустой path, невалидный transport, невалидный JSON
- Минимум 25 contract-тестов

### 5. E2E browser smoke test

- **Task ID**: write-e2e-smoke
- **Depends On**: write-contract-tests
- **Assigned To**: test-builder-contract
- **Agent Type**: builder
- **Stack**: Java selenide e2e page object
- **Parallel**: false
- **Tests**: E2E тест chatFlow_fullUserJourney
- Проверить существует ли `javaclaw-e2e` модуль; если нет — использовать существующий `javaclaw-app/src/test/java/**/e2e/` путь или создать отдельный модуль
- Написать `ChatFlowE2ETest` с Selenide/Playwright (выбор по проектной конвенции — проверить наличие `selenide` в pom.xml)
- Флоу: запуск Spring Boot через `@SpringBootTest(webEnvironment=RANDOM_PORT)` → открыть браузер → login → chat → assertion → screenshot
- Дополнительный тест `navigation_allCriticalRoutesLoad()` проверяет что /chat, /conversations, /admin, /overview не падают
- Запись GIF через Selenide video recording

### 6. Write docs/api/README.md

- **Task ID**: write-api-docs
- **Depends On**: write-openapi-paths
- **Assigned To**: builder-docs
- **Agent Type**: builder
- **Stack**: (docs only, no code)
- **Parallel**: true
- **Tests**: none
- Создать `docs/api/README.md`: (1) путь к `specs/openapi.yaml` (2) как валидировать локально (`npx @redocly/cli lint`) (3) как смотреть в Swagger UI (`npx @redocly/cli preview-docs specs/openapi.yaml`) (4) как генерировать TS-клиент (`npx openapi-typescript specs/openapi.yaml -o javaclaw-frontend/src/api/schema.ts`) (5) как запускать contract-compliance тесты (`mvn -pl javaclaw-app test -Dtest=OpenApi*Test`) (6) правила: "любой новый endpoint сначала в yaml, затем в код"

### 7. Full validation + E2E UI/UX review

- **Task ID**: validate-all
- **Depends On**: write-openapi-paths, write-contract-tests, write-e2e-smoke, write-api-docs
- **Assigned To**: validator-senior-tester
- **Agent Type**: validator
- **Stack**: Java MockMvc integration test Testcontainers e2e
- **Parallel**: false
- Запустить `npx @redocly/cli lint specs/openapi.yaml` → 0 errors
- Запустить `npx @redocly/cli bundle specs/openapi.yaml -o /tmp/bundle.yaml` → успешно
- Запустить `mvn -pl javaclaw-app clean verify -Dtest='OpenApi*Test'` → все contract-тесты зелёные
- Запустить `mvn -pl javaclaw-e2e verify` (или соответствующий путь) → E2E зелёные
- Через mcp__claude-in-chrome или chrome-devtools реально открыть http://localhost:8080/, выполнить login → chat flow → сделать screenshot → оценить UI/UX (spacing, readability, feedback, error states, dark/light mode)
- Senior-tester чеклист: (1) схема yaml валидна (2) все 22 endpoint в yaml (3) все 13 DTO в schemas (4) все error codes покрыты (5) SSE задокументирован (6) контракт-тесты покрывают каждый endpoint (7) E2E прошёл в реальном браузере (8) UI функционально работает (9) UX: нет видимых glitches, приемлемая readability
- Вернуть PASS/FAIL verdict + список найденных проблем

## Acceptance Criteria

- [ ] `specs/openapi.yaml` существует, OpenAPI 3.1.0, валиден по `redocly lint`
- [ ] Содержит 22 path operations (20 active + 2 deprecated)
- [ ] Содержит 13 DTO schemas в `components.schemas`
- [ ] `ApiError` используется как response для 400/404/429/500
- [ ] Basic Auth security scheme определён
- [ ] SSE endpoint POST /api/chat/send задокументирован с frame codes (0, 2, 3, 9, a, d, e) и примерами
- [ ] `OpenApiSchemaValidationTest` содержит минимум 9 unit-тестов, все зелёные
- [ ] `OpenApiContractComplianceTest` содержит минимум 25 integration-тестов, все зелёные, каждый endpoint покрыт
- [ ] Минимум 1 E2E тест `chatFlow_fullUserJourney` зелёный в реальном Chrome
- [ ] `docs/api/README.md` содержит инструкции по всем 6 темам (validate/preview/generate/test/add/governance)
- [ ] `mvn -pl javaclaw-app verify` проходит полностью
- [ ] Валидатор подтвердил UI/UX smoke в реальном браузере
- [ ] Отметка в `specs/roadmap.md`: 0.1 "API contract" → `[x]`, 3.1 "API contract (спека)" → ✅

## Validation Commands

- `npx @redocly/cli lint specs/openapi.yaml` — схема валидна
- `npx @redocly/cli bundle specs/openapi.yaml -o /tmp/bundle.yaml` — все `$ref` резолвятся
- `mvn -pl javaclaw-app clean verify -Dtest='OpenApi*Test'` — unit + integration contract-тесты
- `mvn -pl javaclaw-app verify` — полный билд проходит
- `mvn verify` на корне — все модули зелёные
- `npx openapi-typescript specs/openapi.yaml -o /tmp/schema.ts` — клиент генерится без ошибок
- mcp__claude-in-chrome открыть http://localhost:8080/, выполнить полный flow login→chat→tool call, сохранить GIF

## Notes

- **Зависимости для тестов** (`javaclaw-app/pom.xml` scope=test): `swagger-parser:2.1.22`, `swagger-request-validator-mockmvc:2.44.0`. Проверить существующие версии через `mvn dependency:tree`.
- **Если E2E модуля нет**: создать `javaclaw-e2e/` как отдельный Maven-модуль с Selenide (Playwright если уже где-то используется во фронте).
- **Будущая задача (не в этом плане)**: подключить springdoc-openapi для рантайм-Swagger UI с `springdoc.api-docs.url=/v3/api-docs` (3.6 Actuator task).
- **Генерация TS-клиента** — не в рамках этого плана, но `docs/api/README.md` фиксирует команду для follow-up задачи в Phase 5.
- **Riesgo**: SSE endpoint не все OpenAPI-тулы парсят корректно (текст-поток с кастомными кодами). В крайнем случае — оставить `content: text/plain: schema: {type: string}` + подробный markdown в `description`.
- **Owner roadmap marker**: после PASS-вердикта валидатора отметить в `specs/roadmap.md` пункты 0.1 и 3.1 статусом ✅ и закрыть этот план archive.

