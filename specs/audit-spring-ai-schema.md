# Audit: Spring AI JDBC Chat Memory Schema

**Date:** 2026-04-11
**Scope:** Spring AI 2.0.0-SNAPSHOT — `spring-ai-model-chat-memory-repository-jdbc`

---

## 1. Spring AI Version

```
spring-ai.version = 2.0.0-SNAPSHOT
```

Artifact used in javaclaw-core:

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-chat-memory-repository-jdbc</artifactId>
</dependency>
```

Resolved jar (latest SNAPSHOT in local .m2):
`~/.m2/repository/org/springframework/ai/spring-ai-model-chat-memory-repository-jdbc/2.0.0-SNAPSHOT/spring-ai-model-chat-memory-repository-jdbc-2.0.0-SNAPSHOT.jar`

---

## 2. DDL: PostgreSQL (official, bundled in jar)

File: `org/springframework/ai/chat/memory/repository/jdbc/schema-postgresql.sql`

```sql
CREATE TABLE IF NOT EXISTS SPRING_AI_CHAT_MEMORY (
    conversation_id VARCHAR(36) NOT NULL,
    content         TEXT        NOT NULL,
    type            VARCHAR(10) NOT NULL CHECK (type IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL')),
    "timestamp"     TIMESTAMP   NOT NULL
);

CREATE INDEX IF NOT EXISTS SPRING_AI_CHAT_MEMORY_CONVERSATION_ID_TIMESTAMP_IDX
ON SPRING_AI_CHAT_MEMORY(conversation_id, "timestamp");
```

Column breakdown:

|      Column       |    Type     |                       Constraints                       |
|-------------------|-------------|---------------------------------------------------------|
| `conversation_id` | VARCHAR(36) | NOT NULL — identifies a chat session                    |
| `content`         | TEXT        | NOT NULL — raw message text                             |
| `type`            | VARCHAR(10) | NOT NULL, CHECK IN ('USER','ASSISTANT','SYSTEM','TOOL') |
| `"timestamp"`     | TIMESTAMP   | NOT NULL — quoted identifier (reserved word)            |

Index: composite `(conversation_id, "timestamp")` — supports ordered fetch by session.

No surrogate primary key — the table is append-only; conversation_id + timestamp identify rows logically.

---

## 3. DDL: SQLite (official, bundled in jar)

File: `org/springframework/ai/chat/memory/repository/jdbc/schema-sqlite.sql`

```sql
CREATE TABLE IF NOT EXISTS SPRING_AI_CHAT_MEMORY (
    conversation_id TEXT    NOT NULL,
    content         TEXT    NOT NULL,
    type            TEXT    NOT NULL,
    timestamp       INTEGER NOT NULL,
    CHECK (type IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL'))
);

CREATE INDEX IF NOT EXISTS SPRING_AI_CHAT_MEMORY_CONVERSATION_ID_TIMESTAMP_IDX
ON SPRING_AI_CHAT_MEMORY(conversation_id, timestamp);
```

Key differences from PostgreSQL DDL:

|      Column       |     PostgreSQL     |       SQLite       |                 Notes                 |
|-------------------|--------------------|--------------------|---------------------------------------|
| `conversation_id` | VARCHAR(36)        | TEXT               | SQLite has no max-length enforcement  |
| `type`            | VARCHAR(10)        | TEXT               | CHECK constraint moved to table level |
| `"timestamp"`     | TIMESTAMP (quoted) | INTEGER (unquoted) | SQLite stores as epoch millis (long)  |

---

## 4. Interface: `JdbcChatMemoryRepositoryDialect`

Package: `org.springframework.ai.chat.memory.repository.jdbc`

```java
public interface JdbcChatMemoryRepositoryDialect {

    /** SELECT content, type FROM ... WHERE conversation_id = ? ORDER BY timestamp */
    String getSelectMessagesSql();

    /** INSERT INTO SPRING_AI_CHAT_MEMORY (conversation_id, content, type, timestamp) VALUES (?, ?, ?, ?) */
    String getInsertMessageSql();

    /** SELECT DISTINCT conversation_id FROM SPRING_AI_CHAT_MEMORY */
    String getSelectConversationIdsSql();

    /** DELETE FROM SPRING_AI_CHAT_MEMORY WHERE conversation_id = ? */
    String getDeleteMessagesSql();

    /**
     * Factory method: detects dialect from DataSource metadata.
     * Falls back to PostgresChatMemoryRepositoryDialect if metadata is unavailable.
     */
    static JdbcChatMemoryRepositoryDialect from(DataSource dataSource) { ... }

    static final Logger logger;
}
```

All four methods return a SQL string. Parameters are positional `?` — used via `JdbcTemplate`.

### Implemented dialects (bundled)

|                 Class                  |     DB product name      |
|----------------------------------------|--------------------------|
| `PostgresChatMemoryRepositoryDialect`  | `"PostgreSQL"`           |
| `MysqlChatMemoryRepositoryDialect`     | `"MySQL"`                |
| `MariaDB...`                           | `"MariaDB"`              |
| `SqlServerChatMemoryRepositoryDialect` | `"Microsoft SQL Server"` |
| `HsqldbChatMemoryRepositoryDialect`    | `"HSQL Database Engine"` |
| `SqliteChatMemoryRepositoryDialect`    | `"SQLite"`               |
| `H2ChatMemoryRepositoryDialect`        | `"H2"`                   |
| `OracleChatMemoryRepositoryDialect`    | `"Oracle"`               |

Default (fallback): `PostgresChatMemoryRepositoryDialect`

---

## 5. SQL Strings: `SqliteChatMemoryRepositoryDialect` (decompiled)

```java
getSelectMessagesSql():
  "SELECT content, type FROM SPRING_AI_CHAT_MEMORY WHERE conversation_id = ? ORDER BY timestamp"

getInsertMessageSql():
  "INSERT INTO SPRING_AI_CHAT_MEMORY (conversation_id, content, type, timestamp) VALUES (?, ?, ?, ?)"

getSelectConversationIdsSql():
  "SELECT DISTINCT conversation_id FROM SPRING_AI_CHAT_MEMORY"

getDeleteMessagesSql():
  "DELETE FROM SPRING_AI_CHAT_MEMORY WHERE conversation_id = ?"
```

Note: `timestamp` is unquoted in SQLite dialect (it is an INTEGER column, not a reserved word issue).

---

## 6. SQL Strings: `PostgresChatMemoryRepositoryDialect` (decompiled)

```java
getSelectMessagesSql():
  "SELECT content, type FROM SPRING_AI_CHAT_MEMORY WHERE conversation_id = ? ORDER BY \"timestamp\""

getInsertMessageSql():
  "INSERT INTO SPRING_AI_CHAT_MEMORY (conversation_id, content, type, \"timestamp\") VALUES (?, ?, ?, ?)"

getSelectConversationIdsSql():
  "SELECT DISTINCT conversation_id FROM SPRING_AI_CHAT_MEMORY"

getDeleteMessagesSql():
  "DELETE FROM SPRING_AI_CHAT_MEMORY WHERE conversation_id = ?"
```

`"timestamp"` is double-quoted in Postgres because `timestamp` is a reserved word in PostgreSQL.

---

## 7. Autoconfiguration: How Custom Dialect Is Wired

Class: `JdbcChatMemoryRepositoryAutoConfiguration`
Package: `org.springframework.ai.model.chat.memory.repository.jdbc.autoconfigure`

```java
@AutoConfiguration(before = ChatMemoryAutoConfiguration.class)
@ConditionalOnClass({JdbcChatMemoryRepository.class, DataSource.class, JdbcTemplate.class})
@EnableConfigurationProperties(JdbcChatMemoryRepositoryProperties.class)
public class JdbcChatMemoryRepositoryAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    JdbcChatMemoryRepository jdbcChatMemoryRepository(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        JdbcChatMemoryRepositoryDialect dialect = JdbcChatMemoryRepositoryDialect.from(dataSource);
        return JdbcChatMemoryRepository.builder()
                .jdbcTemplate(jdbcTemplate)
                .dialect(dialect)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    @Conditional(OnJdbcChatMemoryRepositoryDatasourceInitializationCondition.class)
    JdbcChatMemoryRepositorySchemaInitializer jdbcChatMemoryScriptDatabaseInitializer(
            DataSource dataSource,
            JdbcChatMemoryRepositoryProperties properties) {
        return new JdbcChatMemoryRepositorySchemaInitializer(dataSource, properties);
    }
}
```

**Key finding**: The auto-configured `jdbcChatMemoryRepository` bean is annotated `@ConditionalOnMissingBean`.

This means: **to inject a custom dialect, declare your own `JdbcChatMemoryRepository` bean** — the autoconfiguration backs off entirely.

The dialect is NOT a separate injectable bean in autoconfiguration — it is instantiated inline by `JdbcChatMemoryRepositoryDialect.from(dataSource)`. There is no `@ConditionalOnMissingBean` on a dialect bean.

### Registration pattern for custom dialect (e.g., SQLite)

```java
@Configuration
public class SqliteChatMemoryConfig {

    @Bean
    JdbcChatMemoryRepository jdbcChatMemoryRepository(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager txManager) {
        return JdbcChatMemoryRepository.builder()
                .jdbcTemplate(jdbcTemplate)
                .dialect(new SqliteChatMemoryRepositoryDialect())
                .transactionManager(txManager)
                .build();
    }
}
```

Since this bean is declared explicitly, Spring autoconfiguration's `@ConditionalOnMissingBean` suppresses the default bean.

---

## 8. Schema Initializer: Script Location Pattern

Config prefix: `spring.ai.chat.memory.repository.jdbc`

Default schema location template:

```
classpath:org/springframework/ai/chat/memory/repository/jdbc/schema-@@platform@@.sql
```

`@@platform@@` is replaced by Spring Boot's `DatabaseInitializationSettings` with the detected DB platform name (e.g., `sqlite`, `postgresql`, `h2`).

For a SQLite-only standalone module that bypasses autoconfiguration, manual `ScriptUtils.executeSqlScript()` or a Flyway migration referencing `schema-sqlite.sql` must be used explicitly.

---

## 9. Summary for `persistence-spi-design.md`

|            Concern             |                                  Finding                                  |
|--------------------------------|---------------------------------------------------------------------------|
| Spring AI version              | `2.0.0-SNAPSHOT`                                                          |
| Table name                     | `SPRING_AI_CHAT_MEMORY`                                                   |
| No surrogate PK                | Append-only; rows identified by (conversation_id, timestamp)              |
| SQLite timestamp type          | `INTEGER` (epoch millis), not `TIMESTAMP`                                 |
| Dialect auto-detection         | `JdbcChatMemoryRepositoryDialect.from(DataSource)` via DB metadata        |
| Custom dialect injection point | Declare own `JdbcChatMemoryRepository` bean — autoconfiguration backs off |
| Bundled SQLite dialect         | `SqliteChatMemoryRepositoryDialect` already ships in the jar              |
| SQLite DDL                     | Official `schema-sqlite.sql` already in jar classpath                     |

