# Plan: javaclaw-memory — Spring Data JDBC port of chat memory

## Task Description

Создать новый Maven-модуль `javaclaw-memory` внутри директории `javaclaw-core/`, реализовать в нём полноценный
порт персистентности chat-памяти на Spring Data JDBC, устранив половинчатое делегирование к
`JdbcChatMemoryRepository` из Spring AI. Перенести туда же все существующие классы memory-слоя.

Ключевые изменения:

1. `javaclaw-core/pom.xml` — объявляет `javaclaw-memory` как субмодуль И зависит от него как артефакт.
2. Новая сущность `ChatMemoryEntry` (`@Table("SPRING_AI_CHAT_MEMORY")`, `@Id Long id`) + Spring Data JDBC репозиторий `ChatMemoryEntryRepository`.
3. `SpringDataChatMemoryRepository` реализует `AppendableChatMemoryRepository` целиком через Spring Data JDBC — без делегирования к `JdbcChatMemoryRepository`.
4. V43 миграция для PostgreSQL и SQLite: добавление `id` PK и переименование `"timestamp"` → `created_at`.
5. Удаление `spring-ai-starter-model-chat-memory-repository-jdbc` из `javaclaw-core`.
6. Перенос `AppendableChatMemoryRepository` и `JavaClawMessageWindowChatMemory` в `javaclaw-memory` (те же package-имена — импорты в downstream модулях не меняются).

## Objective

После выполнения плана `javaclaw-core` не содержит ни одного class-level import`а из
`org.springframework.ai.chat.memory.repository.jdbc.*`. Вся chat-memory персистентность реализована
через `ChatMemoryEntryRepository extends ListCrudRepository<ChatMemoryEntry, Long>`. Оба диалекта
(PostgreSQL, SQLite) покрыты интеграционными тестами.

## Problem Statement

`JdbcAppendableChatMemoryRepository` является «обёрткой над обёрткой»: `appendAll()` работает напрямую
через `JdbcTemplate.batchUpdate()`, а все остальные методы делегируются в Spring AI-овский
`JdbcChatMemoryRepository`, который сам внутри тоже `JdbcTemplate`. Никакого Spring Data JDBC.
Маппинг, конвертация типов и диалектные отличия обрабатываются вручную. Это дублирование кода и нарушение
принципа единственной ответственности.

## Solution Approach

Вводится Spring Data JDBC entity `ChatMemoryEntry` + автогенерированный суррогатный ключ `id`. Миграция
V43 добавляет этот ключ в обоих диалектах и одновременно переименовывает `timestamp` (зарезервированное
слово PostgreSQL, требующее кавычек) → `created_at` (нейтральное имя, работает без кавычек в
обоих диалектах). SQLite-миграция одновременно меняет тип `timestamp INTEGER` → `created_at TEXT`
(ISO-8601) для согласования с `InstantToStringConverter`, уже зарегистрированным в `SqliteJdbcConfiguration`.

`SpringDataChatMemoryRepository` реализует все методы `AppendableChatMemoryRepository`:

- `appendAll()` → `chatMemoryEntryRepository.saveAll(newEntries)`, каждое entry получает `Instant.ofEpochSecond(base + i)` для гарантированного порядка.
- `saveAll()` → `@Transactional` delete + appendAll.
- `findByConversationId()` → `@Query("… ORDER BY created_at")` на репозитории.
- `deleteByConversationId()` → `@Modifying @Query`.
- `findConversationIds()` → `NamedParameterJdbcTemplate.queryForList("SELECT DISTINCT conversation_id …")` (Spring Data JDBC не поддерживает scalar `List<String>` из `@Query` надёжно — надёжнее явный JdbcTemplate).

Зависимость `spring-ai-starter-model-chat-memory-repository-jdbc` полностью удаляется из `javaclaw-core`.
`SqliteJdbcConfiguration` перестаёт объявлять бин `JdbcChatMemoryRepository`.

## Relevant Files

### Existing Files (to modify or delete)

- `javaclaw-core/pom.xml` — добавить `<modules>`, добавить `<dependency>javaclaw-memory</dependency>`,
  удалить `spring-ai-starter-model-chat-memory-repository-jdbc`
- `javaclaw-core/src/main/java/ai/javaclaw/persistence/dialect/SqliteJdbcConfiguration.java` — удалить
  импорт `JdbcChatMemoryRepository` / `SqliteChatMemoryRepositoryDialect` и `@Bean jdbcChatMemoryRepository`
- `javaclaw-core/src/main/java/ai/javaclaw/JavaClawConfiguration.java` — импорты обновятся автоматически
  (классы в тех же пакетах, просто переехали в `javaclaw-memory`)
- **УДАЛИТЬ**: `javaclaw-core/src/main/java/ai/javaclaw/ai/memory/AppendableChatMemoryRepository.java`
- **УДАЛИТЬ**: `javaclaw-core/src/main/java/ai/javaclaw/ai/memory/JavaClawMessageWindowChatMemory.java`
- **УДАЛИТЬ**: `javaclaw-core/src/main/java/ai/javaclaw/agent/memory/JdbcAppendableChatMemoryRepository.java`
- **УДАЛИТЬ**: `javaclaw-core/src/test/java/ai/javaclaw/agent/memory/JdbcAppendableChatMemoryRepositoryTest.java`
- **УДАЛИТЬ**: `javaclaw-core/src/test/java/ai/javaclaw/persistence/chatmemory/SqliteChatMemoryIntegrationTest.java`
- **ОБНОВИТЬ**: `javaclaw-core/src/test/java/ai/javaclaw/persistence/dialect/SqliteJdbcConfigurationTest.java` — удалить/обновить тест `declaresJdbcChatMemoryRepositoryBeanMethod` (бин намеренно удаляется)
- **ОБНОВИТЬ**: `javaclaw-app/src/main/resources/application.yaml` — удалить свойство `spring.ai.chat.memory.repository.jdbc.initialize-schema: never` (после удаления стартера binding упадёт с ошибкой)

### New Files

- `javaclaw-core/javaclaw-memory/pom.xml`
- `javaclaw-core/javaclaw-memory/src/main/java/ai/javaclaw/ai/memory/AppendableChatMemoryRepository.java`
- `javaclaw-core/javaclaw-memory/src/main/java/ai/javaclaw/ai/memory/JavaClawMessageWindowChatMemory.java`
- `javaclaw-core/javaclaw-memory/src/main/java/ai/javaclaw/agent/memory/ChatMemoryEntry.java`
- `javaclaw-core/javaclaw-memory/src/main/java/ai/javaclaw/agent/memory/ChatMemoryEntryRepository.java`
- `javaclaw-core/javaclaw-memory/src/main/java/ai/javaclaw/agent/memory/SpringDataChatMemoryRepository.java`
- `javaclaw-core/javaclaw-memory/src/main/resources/db/migration/postgresql/V43__add_chat_memory_id_and_rename_timestamp.sql`
- `javaclaw-core/javaclaw-memory/src/main/resources/db/migration/sqlite/V43__add_chat_memory_id_and_rename_timestamp.sql`
- `javaclaw-core/javaclaw-memory/src/test/java/ai/javaclaw/agent/memory/SpringDataChatMemoryRepositoryTest.java`
- `javaclaw-core/javaclaw-memory/src/test/java/ai/javaclaw/agent/memory/ChatMemoryEntryRepositoryPostgresIT.java`
- `javaclaw-core/javaclaw-memory/src/test/java/ai/javaclaw/agent/memory/ChatMemoryEntryRepositorySqliteIT.java`

## Implementation Phases

### Phase 1: Module scaffolding + pom wiring

Создать директорию `javaclaw-core/javaclaw-memory/` и `pom.xml`.

```xml
<!-- javaclaw-core/javaclaw-memory/pom.xml -->
<parent>
    <groupId>ai.javaclaw</groupId>
    <artifactId>javaclaw-parent</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <relativePath>../../pom.xml</relativePath>
</parent>
<artifactId>javaclaw-memory</artifactId>
<dependencies>
    <dependency>spring-ai-client-chat</dependency>       <!-- ChatMemoryRepository, Message types -->
    <dependency>spring-boot-starter-data-jdbc</dependency>
    <dependency>jackson-databind</dependency>
    <!-- test -->
    <dependency>spring-boot-starter-test (test)</dependency>
    <dependency>spring-boot-starter-data-jdbc-test (test)</dependency>
    <dependency>spring-boot-flyway (test)</dependency>
    <dependency>flyway-database-postgresql (test)</dependency>
    <dependency>spring-boot-testcontainers (test)</dependency>
    <dependency>testcontainers:junit-jupiter (test)</dependency>
    <dependency>testcontainers:postgresql (test)</dependency>
    <dependency>sqlite-jdbc (test)</dependency>
    <dependency>postgresql (test)</dependency>
</dependencies>
```

В `javaclaw-core/pom.xml`:

```xml
<modules>
    <module>javaclaw-memory</module>
</modules>

<!-- добавить в <dependencies> -->
<dependency>
    <groupId>ai.javaclaw</groupId>
    <artifactId>javaclaw-memory</artifactId>
    <version>${project.version}</version>
</dependency>
```

Также добавить `javaclaw-memory` в `<dependencyManagement>` корневого `pom.xml`.

### Phase 2: V43 Migrations

**PostgreSQL `V43__add_chat_memory_id_and_rename_timestamp.sql`**:

```sql
-- Step 1: add id column with IDENTITY
ALTER TABLE SPRING_AI_CHAT_MEMORY
    ADD COLUMN id BIGINT GENERATED ALWAYS AS IDENTITY;

-- Step 2: make id the primary key
ALTER TABLE SPRING_AI_CHAT_MEMORY
    ADD CONSTRAINT pk_spring_ai_chat_memory PRIMARY KEY (id);

-- Step 3: rename timestamp -> created_at
ALTER TABLE SPRING_AI_CHAT_MEMORY
    RENAME COLUMN "timestamp" TO created_at;

-- Step 4: refresh composite index
DROP INDEX IF EXISTS idx_spring_ai_chat_memory_conversation_id;
CREATE INDEX idx_spring_ai_chat_memory_conversation_id_created_at
    ON SPRING_AI_CHAT_MEMORY (conversation_id, created_at);
```

**SQLite `V43__add_chat_memory_id_and_rename_timestamp.sql`**:

```sql
-- SQLite: full table rebuild (add id PK, rename timestamp->created_at, INTEGER->TEXT)
-- Convert epoch-millis INTEGER timestamps to ISO-8601 TEXT via strftime
CREATE TABLE SPRING_AI_CHAT_MEMORY_V43 (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    conversation_id TEXT NOT NULL,
    content         TEXT,
    type            TEXT NOT NULL,
    created_at      TEXT NOT NULL,
    CHECK (type IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL')),
    FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE
);

INSERT INTO SPRING_AI_CHAT_MEMORY_V43 (conversation_id, content, type, created_at)
SELECT
    conversation_id,
    content,
    type,
    -- Convert unix epoch millis to ISO-8601 (strftime works with epoch seconds)
    strftime('%Y-%m-%dT%H:%M:%S.000Z', CAST(timestamp AS REAL) / 1000.0, 'unixepoch')
FROM SPRING_AI_CHAT_MEMORY;

DROP TABLE SPRING_AI_CHAT_MEMORY;
ALTER TABLE SPRING_AI_CHAT_MEMORY_V43 RENAME TO SPRING_AI_CHAT_MEMORY;

CREATE INDEX IF NOT EXISTS SPRING_AI_CHAT_MEMORY_CONVERSATION_ID_CREATED_AT_IDX
    ON SPRING_AI_CHAT_MEMORY(conversation_id, created_at);
```

### Phase 3: Entity и Repository

**`ChatMemoryEntry.java`** (package `ai.javaclaw.agent.memory`):

```java
@Table("SPRING_AI_CHAT_MEMORY")
public record ChatMemoryEntry(
    @Id Long id,
    @Column("conversation_id") String conversationId,
    String content,            // nullable — tool-call messages have no content
    String type,               // MessageType.name(): USER / ASSISTANT / SYSTEM / TOOL
    @Column("created_at") Instant createdAt
) {}
```

**`ChatMemoryEntryRepository.java`** (package `ai.javaclaw.agent.memory`):

```java
public interface ChatMemoryEntryRepository extends ListCrudRepository<ChatMemoryEntry, Long> {

    @Query("SELECT * FROM SPRING_AI_CHAT_MEMORY WHERE conversation_id = :conversationId ORDER BY created_at")
    List<ChatMemoryEntry> findByConversationId(@Param("conversationId") String conversationId);

    @Modifying
    @Query("DELETE FROM SPRING_AI_CHAT_MEMORY WHERE conversation_id = :conversationId")
    void deleteByConversationId(@Param("conversationId") String conversationId);
}
```

`findConversationIds()` реализуется в `SpringDataChatMemoryRepository` напрямую через
`NamedParameterJdbcTemplate` — scalar `List<String>` из `@Query` в Spring Data JDBC не является
надёжным публичным API.

### Phase 4: Реализация SpringDataChatMemoryRepository

**`SpringDataChatMemoryRepository.java`** (package `ai.javaclaw.agent.memory`):

```java
@Component
@Primary
public class SpringDataChatMemoryRepository implements AppendableChatMemoryRepository {

    private static final String SELECT_DISTINCT_CONV_IDS =
            "SELECT DISTINCT conversation_id FROM SPRING_AI_CHAT_MEMORY";

    private final ChatMemoryEntryRepository repository;
    private final NamedParameterJdbcTemplate namedJdbc;

    public SpringDataChatMemoryRepository(
            ChatMemoryEntryRepository repository,
            NamedParameterJdbcTemplate namedJdbc) { ... }

    @Override
    public void appendAll(String conversationId, List<Message> messages) {
        if (messages == null || messages.isEmpty()) return;
        long baseSecond = Instant.now().getEpochSecond();
        List<ChatMemoryEntry> entries = IntStream.range(0, messages.size())
                .mapToObj(i -> {
                    Message m = messages.get(i);
                    return new ChatMemoryEntry(null, conversationId,
                            m.getText(), m.getMessageType().name(),
                            Instant.ofEpochSecond(baseSecond + i));
                })
                .toList();
        repository.saveAll(entries);
    }

    @Override
    @Transactional
    public void saveAll(String conversationId, List<Message> messages) {
        repository.deleteByConversationId(conversationId);
        appendAll(conversationId, messages);
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        return repository.findByConversationId(conversationId)
                .stream()
                .map(this::toMessage)
                .toList();
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        repository.deleteByConversationId(conversationId);
    }

    @Override
    public List<String> findConversationIds() {
        return namedJdbc.queryForList(SELECT_DISTINCT_CONV_IDS, Map.of(), String.class);
    }

    private Message toMessage(ChatMemoryEntry entry) {
        return switch (MessageType.valueOf(entry.type())) {
            case USER      -> new UserMessage(entry.content());
            case ASSISTANT -> new AssistantMessage(entry.content());
            case SYSTEM    -> new SystemMessage(entry.content());
            case TOOL      -> ToolResponseMessage.builder().responses(List.of()).build();
        };
    }
}
```

### Phase 5: Перенос интерфейса и ChatMemory + чистка core

**Перенести** (идентичный контент, только меняется источник jar):

- `AppendableChatMemoryRepository.java` → `javaclaw-memory/src/.../ai/javaclaw/ai/memory/`
- `JavaClawMessageWindowChatMemory.java` → `javaclaw-memory/src/.../ai/javaclaw/ai/memory/`

Package-имена не меняются → импорты в `JavaClawConfiguration`, `AgentToolsConfiguration`, `ChatServiceConfiguration` остаются без изменений.

**Удалить** из `javaclaw-core`:

- `JdbcAppendableChatMemoryRepository.java`
- `AppendableChatMemoryRepository.java`
- `JavaClawMessageWindowChatMemory.java`

**Обновить** `SqliteJdbcConfiguration.java`:

- Удалить `import org.springframework.ai.chat.memory.repository.jdbc.*`
- Удалить `@Bean jdbcChatMemoryRepository(...)` целиком
- Сохранить все остальные бины (`jdbcDialect`, `userConverters`)

**Обновить** `javaclaw-core/pom.xml`:

- Убрать `spring-ai-starter-model-chat-memory-repository-jdbc`
- Добавить `<module>javaclaw-memory</module>` и `<dependency>javaclaw-memory</dependency>`

## Team Orchestration

Работаем как team lead. Все изменения — через `Task` builder.

### Team Members

- Builder
  - Name: builder-memory
  - Role: Реализует все 5 фаз кода и миграций последовательно
  - Agent Type: builder
  - Resume: true
- Builder
  - Name: builder-tests
  - Role: Пишет unit и integration тесты для javaclaw-memory
  - Agent Type: builder
  - Resume: false
- Validator
  - Name: validator-final
  - Role: Финальная проверка компиляции и тестов
  - Agent Type: validator
  - Resume: false

## Testing Strategy

Цель: **100% unit / 80% integration на обоих диалектах (PostgreSQL + SQLite)**.
Ни один публичный метод не остаётся без unit-теста. Каждый сценарий интеграционного уровня
проверяется на реальной базе обоих диалектов.

### Unit Tests — 100% покрытие всех классов

#### `SpringDataChatMemoryRepositoryTest`

Мокирует `ChatMemoryEntryRepository` + `NamedParameterJdbcTemplate`. Один метод = один assert-набор.

|                     Метод теста                     |                                Что проверяется                                 |
|-----------------------------------------------------|--------------------------------------------------------------------------------|
| `appendAll_null_isNoOp`                             | null messages → ноль взаимодействий с repo и jdbcTemplate                      |
| `appendAll_emptyList_isNoOp`                        | пустой список → ноль взаимодействий                                            |
| `appendAll_singleMessage_createsOneEntry`           | 1 message → `repo.saveAll` вызван с 1 entry, id=null, conversationId совпадает |
| `appendAll_multipleMessages_timestampsAreMonotonic` | 3 messages → timestamps = base, base+1, base+2 (секундная гранулярность)       |
| `appendAll_setsCorrectFields`                       | проверяет content и type в созданных entry                                     |
| `saveAll_callsDeleteThenAppendAll`                  | `repo.deleteByConversationId` вызван ДО `repo.saveAll` (InOrder Mockito)       |
| `saveAll_emptyMessages_stillCallsDelete`            | пустой список → delete всё равно вызван (clear-семантика)                      |
| `findByConversationId_emptyRepo_returnsEmptyList`   | пустой список entries → пустой список Messages                                 |
| `findByConversationId_mapsUserMessage`              | entry(type=USER, content="hi") → `UserMessage("hi")`                           |
| `findByConversationId_mapsAssistantMessage`         | entry(type=ASSISTANT) → `AssistantMessage`                                     |
| `findByConversationId_mapsSystemMessage`            | entry(type=SYSTEM) → `SystemMessage`                                           |
| `findByConversationId_mapsTool_withNullContent`     | entry(type=TOOL, content=null) → `ToolResponseMessage(responses=[])`           |
| `findByConversationId_preservesOrder`               | 3 entries в заданном порядке → Messages в том же порядке                       |
| `deleteByConversationId_delegatesToRepo`            | verify `repo.deleteByConversationId(id)` вызван ровно 1 раз                    |
| `findConversationIds_delegatesToNamedJdbc`          | verify `namedJdbc.queryForList(SELECT_DISTINCT…, Map.of(), String.class)`      |

#### `JavaClawMessageWindowChatMemoryTest`

Без Spring-контекста. Использует `DelegatingAppendableChatMemoryRepository` поверх мока.

|                        Метод теста                        |                                  Что проверяется                                   |
|-----------------------------------------------------------|------------------------------------------------------------------------------------|
| `add_delegatesToAppendAll`                                | `add(id, msgs)` вызывает `appendAll(id, msgs)`                                     |
| `add_nullConversationId_throws`                           | `Assert.hasText` выбрасывает для null                                              |
| `add_nullMessages_throws`                                 | `Assert.notNull` выбрасывает для null messages                                     |
| `add_messagesWithNullElement_throws`                      | `Assert.noNullElements` выбрасывает                                                |
| `get_belowWindowSize_returnsAll`                          | 5 messages, maxMessages=20 → все 5                                                 |
| `get_atWindowSize_returnsAll`                             | ровно 20 messages, maxMessages=20 → все 20                                         |
| `get_aboveWindowSize_trimsOldestNonSystem`                | 25 non-system messages, maxMessages=20 → последние 20                              |
| `get_systemMessagesAlwaysRetained`                        | 3 system + 20 non-system → 3 system + 17 non-system (oldest non-system удалён)     |
| `get_onlySystemMessages_allRetained`                      | 25 system messages, maxMessages=20 → все 25 (system не режутся)                    |
| `get_emptyHistory_returnsEmpty`                           | пустой repo → пустой список                                                        |
| `get_nullConversationId_throws`                           | `Assert.hasText` выбрасывает                                                       |
| `clear_delegatesToDeleteByConversationId`                 | `clear(id)` вызывает `deleteByConversationId(id)`                                  |
| `clear_nullConversationId_throws`                         | выбрасывает для null                                                               |
| `builder_defaultMaxMessages_is20`                         | `builder().build()` → maxMessages = 20                                             |
| `builder_customMaxMessages`                               | `builder().maxMessages(5).build()` → обрезает при 6 messages                       |
| `delegating_withAppendableDelegate_callsAppendAll`        | делегат реализует `AppendableChatMemoryRepository` → `appendAll()` вызван напрямую |
| `delegating_withPlainDelegate_fallsBackToReadModifyWrite` | делегат — обычный `ChatMemoryRepository` → fallback: find + saveAll                |

### Integration Tests — 80% сценариев на обоих диалектах

Два класса тестируют **`SpringDataChatMemoryRepository` на реальной базе** (сервисный уровень, не только repo).
Аннотации: `@DataJdbcTest @ImportAutoConfiguration(FlywayAutoConfiguration.class) @Import({<JdbcConfig>, SpringDataChatMemoryRepository.class})`

**Инициализация `@BeforeEach`** (оба класса):
- Вставить строку в таблицу `conversations` для каждого `conversationId` используемого в тесте — FK
`SPRING_AI_CHAT_MEMORY.conversation_id → conversations(id)` активен (V5); без seed insert упадёт.
Образец: `SqliteChatMemoryIntegrationTest.setUp()`.
- После каждого теста очищать `SPRING_AI_CHAT_MEMORY` (или использовать уникальные conversationId per test).

#### Тест-кейсы (одинаковые для PG и SQLite)

| №  |                      Метод теста                       |                                                       Сценарий                                                        |
|----|--------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------|
| 1  | `appendAll_addsToExistingMessages`                     | appendAll дважды → второй батч добавляется, первый НЕ удаляется                                                       |
| 2  | `appendAll_multipleMessages_preservesCreatedAtOrder`   | 3 messages в одном appendAll → `findByConversationId` возвращает их в порядке created_at                              |
| 3  | `appendAll_twoBatches_allMessagesAccumulate`           | 2 batcha по 2 messages → итого 4 messages в правильном порядке                                                        |
| 4  | `saveAll_replacesExistingMessages`                     | appendAll(2), затем saveAll(3) → только 3 новых message                                                               |
| 5  | `saveAll_emptyList_clearsAllMessages`                  | appendAll(2), затем saveAll([]) → ноль messages                                                                       |
| 6  | `findByConversationId_mapsAllFourTypes`                | insert USER/ASSISTANT/SYSTEM/TOOL → findByConversationId возвращает правильные подтипы                                |
| 7  | `findByConversationId_nullContent_toolMessage`         | TOOL с null content → round-trip без NPE, ToolResponseMessage                                                         |
| 8  | `findByConversationId_isolationBetweenConversations`   | appendAll в conv-A и conv-B → `find(conv-A)` не содержит messages из conv-B                                           |
| 9  | `deleteByConversationId_removesOnlyTargetConversation` | appendAll в conv-A и conv-B, delete(conv-A) → conv-B нетронут, conv-A пуст                                            |
| 10 | `findConversationIds_returnsDistinctIds`               | appendAll в 3 разных conversationId → findConversationIds содержит все 3                                              |
| 11 | `findConversationIds_doesNotIncludeEmptyConversations` | deleteByConversationId после appendAll → ID больше не в findConversationIds                                           |
| 12 | `instantRoundTrip_preservesPrecision`                  | Instant с наносекундами → round-trip через БД → equals (диалект-специфично: PG=µs точность, SQLite=мс через ISO-8601) |

80% = минимум 10 из 12 сценариев на КАЖДОМ диалекте. Рекомендуется покрыть все 12.

#### `SpringDataChatMemoryRepositoryPostgresIT`

```
@DataJdbcTest
@Testcontainers
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import({PostgresJdbcConfiguration.class, SpringDataChatMemoryRepository.class})
@ActiveProfiles("test")

@Container @ServiceConnection
static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

@Autowired SpringDataChatMemoryRepository chatMemoryRepository;
@Autowired NamedParameterJdbcTemplate namedJdbc;   // для seed conversations
```

#### `SpringDataChatMemoryRepositorySqliteIT`

```
// Без @DataJdbcTest — по образцу SqliteChatMemoryIntegrationTest:
// SingleConnectionDataSource + in-memory SQLite + Flyway.migrate() в @BeforeEach
// Ручная инициализация: SqliteJdbcConfiguration, JdbcTemplate, NamedParameterJdbcTemplate,
// ChatMemoryEntryRepository (через SqliteJdbcConfiguration.jdbcOperations() + Spring Data proxy),
// SpringDataChatMemoryRepository создаётся вручную new SpringDataChatMemoryRepository(repo, namedJdbc)
```

### UI E2E Tests

Не применимо — нет UI для прямого тестирования memory persistence. Существующие интеграционные тесты
(`ConversationIsolationIntegrationTest` и др.) косвенно покрывают chat memory через живой stack.

## Step by Step Tasks

### 1. Module scaffolding — создать структуру `javaclaw-memory`

- **Task ID**: scaffold-module
- **Depends On**: none
- **Assigned To**: builder-memory
- **Agent Type**: builder
- **Stack**: Java Spring Boot maven java-patterns#basics
- **Parallel**: false
- **Tests**: нет тестов на этой задаче (только структура)
- Создать директорию `javaclaw-core/javaclaw-memory/` со стандартной Maven-структурой (`src/main/java`, `src/main/resources`, `src/test/java`, `src/test/resources`)
- Написать `javaclaw-core/javaclaw-memory/pom.xml`:
  - parent = `javaclaw-parent` (relativePath = `../../pom.xml`)
  - artifactId = `javaclaw-memory`
  - packaging = jar (default)
  - dependencies: `spring-ai-client-chat`, `spring-boot-starter-data-jdbc`, `jackson-databind` (scope compile); тестовые зависимости по образцу `javaclaw-core/pom.xml`
- В `javaclaw-core/pom.xml` добавить `<modules><module>javaclaw-memory</module></modules>` и `<dependency>javaclaw-memory</dependency>`
- В корневом `pom.xml` добавить `javaclaw-memory` в `<dependencyManagement>` (без версии, using `${project.version}`)
- Проверить: `mvn validate -pl javaclaw-core/javaclaw-memory` без ошибок

### 2. V43 Migrations — оба диалекта

- **Task ID**: migration-v43
- **Depends On**: scaffold-module
- **Assigned To**: builder-memory
- **Agent Type**: builder
- **Stack**: Java Spring Boot java-patterns#basics
- **Parallel**: false
- **Tests**: покрываются в integration tests (task write-tests)
- Написать `javaclaw-core/javaclaw-memory/src/main/resources/db/migration/postgresql/V43__add_chat_memory_id_and_rename_timestamp.sql`: ALTER TABLE ADD COLUMN `id BIGINT GENERATED ALWAYS AS IDENTITY`, ADD CONSTRAINT PRIMARY KEY, RENAME COLUMN `"timestamp"` → `created_at`, обновить индексы
- Написать `javaclaw-core/javaclaw-memory/src/main/resources/db/migration/sqlite/V43__add_chat_memory_id_and_rename_timestamp.sql`: full table rebuild с `id INTEGER PRIMARY KEY AUTOINCREMENT`, `created_at TEXT` (конвертация из epoch-millis через `strftime`), сохранение FK на `conversations`, `CHECK` constraint, `FOREIGN KEY`

### 3. Entity и Repository — ChatMemoryEntry, ChatMemoryEntryRepository

- **Task ID**: entity-and-repo
- **Depends On**: scaffold-module
- **Assigned To**: builder-memory
- **Agent Type**: builder
- **Stack**: Java Spring Boot spring data jdbc entity record java-patterns#basics java-patterns#java21
- **Parallel**: false
- **Tests**: unit-тест `SpringDataChatMemoryRepositoryTest` (часть в task write-tests)
- Написать `ChatMemoryEntry.java` — `@Table("SPRING_AI_CHAT_MEMORY")`, record, `@Id Long id`, `@Column("conversation_id") String conversationId`, `String content` (nullable), `String type`, `@Column("created_at") Instant createdAt`
- Написать `ChatMemoryEntryRepository.java` — `extends ListCrudRepository<ChatMemoryEntry, Long>` с `@Query` методами: `findByConversationId` (ORDER BY created_at), `@Modifying deleteByConversationId`
- Убедиться, что `@Table` имя в upper case совпадает с таблицей (Spring Data JDBC чувствителен к регистру по умолчанию для `@Table`)

### 4. Перенос AppendableChatMemoryRepository и JavaClawMessageWindowChatMemory

- **Task ID**: move-interfaces
- **Depends On**: scaffold-module
- **Assigned To**: builder-memory
- **Agent Type**: builder
- **Stack**: Java Spring Boot java-patterns#basics
- **Parallel**: false
- **Tests**: нет новых тестов — классы идентичны, package не меняется
- Скопировать `AppendableChatMemoryRepository.java` в `javaclaw-memory/src/.../ai/javaclaw/ai/memory/` (идентичное содержимое)
- Скопировать `JavaClawMessageWindowChatMemory.java` в `javaclaw-memory/src/.../ai/javaclaw/ai/memory/` (идентичное содержимое)
- УДАЛИТЬ оригиналы из `javaclaw-core/src/main/java/ai/javaclaw/ai/memory/`
- УДАЛИТЬ `javaclaw-core/src/main/java/ai/javaclaw/agent/memory/JdbcAppendableChatMemoryRepository.java`

### 5. Реализация SpringDataChatMemoryRepository

- **Task ID**: impl-spring-data-repo
- **Depends On**: entity-and-repo, move-interfaces
- **Assigned To**: builder-memory
- **Agent Type**: builder
- **Stack**: Java Spring Boot spring data jdbc java-patterns#basics java-patterns#java21 record
- **Parallel**: false
- **Tests**: `SpringDataChatMemoryRepositoryTest` (в task write-tests)
- Написать `SpringDataChatMemoryRepository.java` в `javaclaw-memory/src/.../ai/javaclaw/agent/memory/`:
  - `@Component @Primary implements AppendableChatMemoryRepository`
  - Инжектировать `ChatMemoryEntryRepository` и `NamedParameterJdbcTemplate`
  - `appendAll()`: создаёт entries с `Instant.ofEpochSecond(base + i)`, вызывает `repository.saveAll()`
  - `saveAll()`: `@Transactional`, delete + appendAll
  - `findByConversationId()`: делегирует в `repository.findByConversationId()`, маппит через switch по `MessageType`
  - `deleteByConversationId()`: делегирует в `repository.deleteByConversationId()`
  - `findConversationIds()`: `namedJdbc.queryForList(SELECT_DISTINCT_CONV_IDS, Map.of(), String.class)`
- TOOL-case: `ToolResponseMessage.builder().responses(List.of()).build()` (null content игнорируется)

### 6. Чистка javaclaw-core — удаление старых зависимостей и бинов

- **Task ID**: cleanup-core
- **Depends On**: move-interfaces, impl-spring-data-repo
- **Assigned To**: builder-memory
- **Agent Type**: builder
- **Stack**: Java Spring Boot maven java-patterns#basics
- **Parallel**: false
- **Tests**: нет новых тестов
- В `javaclaw-core/pom.xml` удалить `spring-ai-starter-model-chat-memory-repository-jdbc`
- В `SqliteJdbcConfiguration.java` удалить:
  - `import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository`
  - `import org.springframework.ai.chat.memory.repository.jdbc.SqliteChatMemoryRepositoryDialect`
  - `import org.springframework.transaction.PlatformTransactionManager`
  - Весь `@Bean jdbcChatMemoryRepository(JdbcTemplate, PlatformTransactionManager)` метод
- В `javaclaw-core/pom.xml` удалить `spring-boot-flyway` из test scope (если теперь не нужен — проверить по наличию других flyway-тестов в javaclaw-core)
- Удалить тестовые файлы: `JdbcAppendableChatMemoryRepositoryTest.java`, `SqliteChatMemoryIntegrationTest.java`
- В `SqliteJdbcConfigurationTest.java` удалить метод `declaresJdbcChatMemoryRepositoryBeanMethod()` и любые связанные с `JdbcChatMemoryRepository` assert-ы (бин удалён намеренно)
- В `javaclaw-app/src/main/resources/application.yaml` удалить свойство `spring.ai.chat.memory.repository.jdbc.initialize-schema: never` — после удаления стартера его `JdbcChatMemoryRepositoryProperties` нет на classpath и Spring Boot 4.x завалит binding
- Проверить: `mvn compile -pl javaclaw-core` без ошибок (all imports resolved from javaclaw-memory transitively)

### 7. Написание тестов

- **Task ID**: write-tests
- **Depends On**: impl-spring-data-repo, migration-v43, cleanup-core
- **Assigned To**: builder-tests
- **Agent Type**: builder
- **Stack**: Java Spring Boot spring data jdbc mockito assertj allure test structure integration test testcontainers database test jdbc test java-testing#structure java-testing#mockito java-testing#jdbc java-testing#integration
- **Parallel**: false
- **Tests**: все тесты этого плана

**Unit (80%)**:

- `SpringDataChatMemoryRepositoryTest` в `javaclaw-memory/src/test/`:
  - Mock `ChatMemoryEntryRepository`, mock `NamedParameterJdbcTemplate`
  - `appendAll_createsEntryPerMessage_withMonotonicTimestamps()`
  - `appendAll_null_isNoOp()`, `appendAll_empty_isNoOp()`
  - `saveAll_deletesBeforeAppend_inTransaction()` (verify delete called before saveAll)
  - `findByConversationId_mapsAllMessageTypes()` (USER, ASSISTANT, SYSTEM, TOOL)
  - `findByConversationId_toolMessage_nullContent_isHandled()`
  - `deleteByConversationId_delegates()`
  - `findConversationIds_usesNamedJdbc()`

**Integration (15%)**:

- `ChatMemoryEntryRepositoryPostgresIT` в `javaclaw-memory/src/test/`:
  - `@DataJdbcTest @Testcontainers @ImportAutoConfiguration(FlywayAutoConfiguration.class)`
  - `@Import(PostgresJdbcConfiguration.class)` (для конверторов)
  - `@ActiveProfiles("test")`
  - `@ServiceConnection PostgreSQLContainer postgres = new PostgreSQLContainer<>("postgres:17")`
  - Тест-кейсы: insert + findByConversationId ordering, deleteByConversationId isolation, null content round-trip
- `ChatMemoryEntryRepositorySqliteIT` в `javaclaw-memory/src/test/`:
  - По образцу `SqliteChatMemoryIntegrationTest` — `SingleConnectionDataSource` + in-memory SQLite + Flyway
  - Импортирует `SqliteJdbcConfiguration` для конверторов
  - Тест-кейсы: save + load round-trip (проверка ISO-8601 Instant round-trip), ordering, isolation

**E2E (5%)**: покрывается существующими интеграционными тестами (`ConversationIsolationIntegrationTest` и др.) без изменений.

### 8. Финальная валидация

- **Task ID**: validate-all
- **Depends On**: scaffold-module, migration-v43, entity-and-repo, move-interfaces, impl-spring-data-repo, cleanup-core, write-tests
- **Assigned To**: validator-final
- **Agent Type**: validator
- **Stack**: Java Spring Boot maven surefire jacoco java-testing#maven
- **Parallel**: false
- Выполнить `mvn clean install -pl javaclaw-core/javaclaw-memory -am` — сборка javaclaw-memory и её зависимостей
- Выполнить `mvn clean install -pl javaclaw-core -am` — сборка javaclaw-core с новой зависимостью
- Выполнить `mvn clean install` от корня — полная сборка
- Убедиться: нет `import org.springframework.ai.chat.memory.repository.jdbc` нигде кроме удалённых файлов
- Убедиться: `spring-ai-client-chat` jar не реэкспортирует `JdbcChatMemoryRepositoryAutoConfiguration` — проверить: `jar tf ~/.m2/repository/org/springframework/ai/spring-ai-client-chat/*/spring-ai-client-chat-*.jar | grep AutoConfiguration.imports` не должно содержать `JdbcChatMemoryRepositoryAutoConfiguration`
- Убедиться: `spring-ai-starter-model-chat-memory-repository-jdbc` не встречается ни в одном `pom.xml` (кроме, возможно, `pom.xml.bak`)
- Убедиться: все тесты `javaclaw-memory` зелёные (unit + postgres IT + sqlite IT)
- Убедиться: все тесты `javaclaw-core` зелёные (старые тесты memory удалены, остальные без изменений)

## Acceptance Criteria

1. **Модуль создан**: `javaclaw-core/javaclaw-memory/` существует, компилируется и публикует артефакт `ai.javaclaw:javaclaw-memory:1.0.0-SNAPSHOT`.
2. **Нет делегирования**: в `javaclaw-core` и `javaclaw-memory` отсутствуют импорты `org.springframework.ai.chat.memory.repository.jdbc.*`.
3. **Зависимость удалена**: `spring-ai-starter-model-chat-memory-repository-jdbc` не объявлена ни в одном `pom.xml` проекта.
4. **Entity с PK**: `SPRING_AI_CHAT_MEMORY` имеет колонку `id` (auto-generated) в обоих диалектах после V43 миграции.
5. **Колонка переименована**: `timestamp` переименована в `created_at`; обращений к `"timestamp"` (с кавычками) нет ни в коде, ни в SQL-запросах.
6. **Spring Data JDBC**: `ChatMemoryEntryRepository extends ListCrudRepository<ChatMemoryEntry, Long>` — является единственным механизмом чтения/записи/удаления memory-записей.
7. **Порядок сообщений**: `appendAll` гарантирует монотонно возрастающие `created_at` (second-level granularity) для каждого батча.
8. **Тесты зелёные**: `SpringDataChatMemoryRepositoryTest`, `ChatMemoryEntryRepositoryPostgresIT`, `ChatMemoryEntryRepositorySqliteIT` — все passed.
9. **Root build green**: `mvn clean install` от корня завершается без ошибок.
10. **Downstream без изменений**: `javaclaw-security`, `javaclaw-api`, `javaclaw-channel`, `javaclaw-app` — pom.xml не требуют изменений (transitive dependency).

## Validation Commands

```bash
# 1. Сборка только javaclaw-memory и её зависимостей
mvn clean install -pl javaclaw-core/javaclaw-memory -am -q

# 2. Сборка javaclaw-core с новой структурой
mvn clean install -pl javaclaw-core -am -q

# 3. Полная сборка от корня
mvn clean install -q

# 4. Убедиться что spring-ai jdbc starter удалён
grep -r "spring-ai-starter-model-chat-memory-repository-jdbc" --include="pom.xml" .

# 5. Убедиться что нет старых imports
grep -r "repository.jdbc.JdbcChatMemoryRepository\|repository.jdbc.SqliteChatMemoryRepositoryDialect" \
     --include="*.java" javaclaw-core/src javaclaw-memory/src 2>/dev/null

# 6. Тесты javaclaw-memory отдельно
mvn test -pl javaclaw-core/javaclaw-memory -q
```

## Notes

### Maven jar+aggregator pattern

`javaclaw-core/pom.xml` остаётся с `<packaging>jar</packaging>` (implicit) И объявляет `<modules>`.
Это валидный Maven-паттерн: `javaclaw-core` производит jar-артефакт И агрегирует `javaclaw-memory`
в реакторную сборку. Maven выстраивает порядок через dependency graph: `javaclaw-memory` строится
раньше `javaclaw-core` (потому что `javaclaw-core` зависит от него).

### Flyway multi-jar scanning

Flyway настроен на `classpath:db/migration/{vendor}`. Classpath включает все jar-файлы приложения,
в том числе `javaclaw-memory.jar`. Поэтому `V43__...sql` из `javaclaw-memory` будет автоматически
обнаружен и применён Flyway в правильном порядке (после V42 из `javaclaw-core`).

### SQLite timestamp конвертация

Существующий `SqliteJdbcConfiguration.userConverters()` содержит `InstantToStringConverter` /
`StringToInstantConverter` (ISO-8601 TEXT). После V43 столбец `created_at` в SQLite — TEXT. Конвертер
работает «из коробки» для `ChatMemoryEntry.createdAt Instant` без дополнительных изменений.

### @DataJdbcTest slice для javaclaw-memory тестов

Для `ChatMemoryEntryRepositoryPostgresIT` и `ChatMemoryEntryRepositorySqliteIT` нужно явно импортировать
соответствующий `*JdbcConfiguration` класс (он находится в `javaclaw-core`, который является
transitive dependency):

```java
@DataJdbcTest
@Import(PostgresJdbcConfiguration.class)  // или SqliteJdbcConfiguration.class
```

### Не нужно трогать

- `javaclaw-security/pom.xml`, `javaclaw-api/**/pom.xml`, `javaclaw-channel/pom.xml`, `javaclaw-app/pom.xml` — зависимость на `javaclaw-memory` приходит транзитивно через `javaclaw-core`.
- `JavaClawConfiguration.java` — импорты `ai.javaclaw.ai.memory.*` остаются теми же, классы просто переехали в другой jar.
- `AgentToolsConfiguration.java` — импортирует `AppendableChatMemoryRepository` из того же package, транзитивно на classpath.

