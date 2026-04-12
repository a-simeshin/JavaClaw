# Persistence SPI Design

**Task:** design-spi
**Date:** 2026-04-11
**Depends on:** audit-jsonb, audit-raw-sql, audit-id-generation, audit-spring-ai-schema
**Scope:** Full API surface for Phase 2-6 builders. No open questions — every decision is fixed here.

---

## 0. Package Layout

```
ai.javaclaw.persistence
├── dialect/
│   ├── PersistenceDialect.java                      (enum)
│   ├── PersistenceDialectProperties.java            (@ConfigurationProperties)
│   ├── PostgresJdbcConfiguration.java               (AbstractJdbcConfiguration)
│   ├── SqliteJdbcConfiguration.java                 (AbstractJdbcConfiguration)
│   └── sqlite/
│       └── SqliteJdbcDialect.java                   (extends AnsiDialect)
├── converter/
│   ├── JsonToStringConverter.java                   (shared, writing)
│   ├── StringToJsonConverter.java                   (shared, reading)
│   ├── postgres/
│   │   ├── MapToJsonbConverter.java                 (moved from mcp.converter)
│   │   └── JsonbToMapConverter.java                 (moved from mcp.converter)
│   └── sqlite/
│       ├── UuidToStringConverter.java
│       ├── StringToUuidConverter.java
│       ├── InstantToStringConverter.java
│       └── StringToInstantConverter.java
├── id/
│   ├── AppUserIdGeneratorCallback.java
│   ├── VirtualFileIdGeneratorCallback.java
│   ├── SkillIdGeneratorCallback.java
│   ├── McpServerIdGeneratorCallback.java
│   ├── McpRoleAllowlistIdGeneratorCallback.java
│   ├── MemoryIdGeneratorCallback.java
│   ├── ToolExampleIdGeneratorCallback.java
│   ├── ConversationSummaryIdGeneratorCallback.java
│   ├── SkillRoleAllowlistIdGeneratorCallback.java
│   ├── RoleAgentConfigIdGeneratorCallback.java
│   └── RoleModelAllowlistIdGeneratorCallback.java
├── chatmemory/
│   └── (no new dialect class — Spring AI jar already ships SqliteChatMemoryRepositoryDialect)
├── postgres/    (Query SPI Postgres impls)
│   ├── ChannelContextQueryRepositoryPgImpl.java
│   ├── ConversationQueryRepositoryPgImpl.java
│   ├── AppUserQueryRepositoryPgImpl.java
│   ├── AgentQuotaQueryRepositoryPgImpl.java
│   └── MemoryQueryRepositoryPgImpl.java
└── sqlite/      (Query SPI SQLite impls)
    ├── ChannelContextQueryRepositorySqliteImpl.java
    ├── ConversationQueryRepositorySqliteImpl.java
    ├── AppUserQueryRepositorySqliteImpl.java
    ├── AgentQuotaQueryRepositorySqliteImpl.java
    └── MemoryQueryRepositorySqliteImpl.java
```

The Query SPI interfaces themselves live alongside their primary Spring Data repositories (e.g. `ai.javaclaw.conversations.ConversationQueryRepository`) so business code does not depend on `persistence.*`.

---

## 1. Dialect SPI

### 1.1 `PersistenceDialect` (enum)

```java
package ai.javaclaw.persistence.dialect;

public enum PersistenceDialect {
    POSTGRESQL,
    SQLITE
}
```

### 1.2 `PersistenceDialectProperties`

```java
package ai.javaclaw.persistence.dialect;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "javaclaw.persistence")
public record PersistenceDialectProperties(PersistenceDialect dialect) {
    public PersistenceDialectProperties {
        if (dialect == null) {
            dialect = PersistenceDialect.POSTGRESQL;
        }
    }
}
```

Registered via `@EnableConfigurationProperties(PersistenceDialectProperties.class)` on both `PostgresJdbcConfiguration` and `SqliteJdbcConfiguration`. YAML binding is validated by `PersistenceDialectPropertiesTest`. Unknown enum values → Spring binder throws at startup (fail-fast).

Default: `POSTGRESQL` (enforced both by record constructor and by `matchIfMissing=true` on the Postgres `@ConditionalOnProperty`).

### 1.3 `SqliteJdbcDialect`

```java
package ai.javaclaw.persistence.dialect.sqlite;

import org.springframework.data.relational.core.dialect.AnsiDialect;
import org.springframework.data.relational.core.dialect.ArrayColumns;
import org.springframework.data.relational.core.dialect.LimitClause;
import org.springframework.data.relational.core.sql.IdentifierProcessing;
import org.springframework.data.jdbc.core.dialect.JdbcDialect;
import org.springframework.data.relational.core.dialect.IdGeneration;

public final class SqliteJdbcDialect extends AnsiDialect implements JdbcDialect {

    public static final SqliteJdbcDialect INSTANCE = new SqliteJdbcDialect();

    private static final LimitClause LIMIT_CLAUSE = new LimitClause() {
        @Override
        public String getLimit(long limit) {
            return "LIMIT " + limit;
        }
        @Override
        public String getOffset(long offset) {
            return "LIMIT -1 OFFSET " + offset;   // SQLite requires LIMIT when OFFSET is present
        }
        @Override
        public String getLimitOffset(long limit, long offset) {
            return String.format("LIMIT %d OFFSET %d", limit, offset);
        }
        @Override
        public Position getClausePosition() {
            return Position.AFTER_ORDER_BY;
        }
    };

    @Override
    public LimitClause limit() {
        return LIMIT_CLAUSE;
    }

    @Override
    public ArrayColumns getArraySupport() {
        return ArrayColumns.Unsupported.INSTANCE;
    }

    @Override
    public IdentifierProcessing getIdentifierProcessing() {
        // SQLite folds unquoted identifiers to their declared case; use ANSI quoting
        return IdentifierProcessing.ANSI;
    }

    @Override
    public IdGeneration getIdGeneration() {
        // Java-side UUID generation; no DB sequences, no RETURNING
        return IdGeneration.DEFAULT;
    }
}
```

Rationale for each override:
- `LimitClause` — SQLite syntax is `LIMIT n OFFSET m`, no `FETCH FIRST`. AnsiDialect default uses `OFFSET n ROWS FETCH NEXT m ROWS ONLY`.
- `ArrayColumns.Unsupported.INSTANCE` — SQLite has no native arrays. Matches Spring Data JDBC pattern used in HsqlDbDialect.
- `IdentifierProcessing.ANSI` — quoted identifiers are case-sensitive and preserved as written; column `"timestamp"` in schema V2 matches what we emit.
- `IdGeneration.DEFAULT` — no identity columns, no sequences. All string PKs are pre-generated via `BeforeConvertCallback`; `BIGSERIAL` becomes `INTEGER PRIMARY KEY AUTOINCREMENT`, which SQLite auto-fills without a `RETURNING`/sequence call.

### 1.4 `PostgresJdbcConfiguration`

```java
package ai.javaclaw.persistence.dialect;

import ai.javaclaw.persistence.converter.JsonToStringConverter;
import ai.javaclaw.persistence.converter.StringToJsonConverter;
import ai.javaclaw.persistence.converter.postgres.JsonbToMapConverter;
import ai.javaclaw.persistence.converter.postgres.MapToJsonbConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PersistenceDialectProperties.class)
@ConditionalOnProperty(
        prefix = "javaclaw.persistence",
        name = "dialect",
        havingValue = "postgresql",
        matchIfMissing = true)
public class PostgresJdbcConfiguration extends AbstractJdbcConfiguration {

    private final ObjectMapper objectMapper;

    public PostgresJdbcConfiguration(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected List<?> userConverters() {
        return List.of(
                // Dialect-specific: PGobject <-> Map (JSONB column on mcp_servers.headers only)
                new MapToJsonbConverter(objectMapper),
                new JsonbToMapConverter(objectMapper),
                // Shared: plain JSON text (portable path for future TEXT columns)
                new JsonToStringConverter(objectMapper),
                new StringToJsonConverter(objectMapper)
                // Instant <-> TIMESTAMPTZ is handled natively by PostgreSQL JDBC driver
                // UUID is handled natively by PostgreSQL JDBC driver
        );
    }

    // jdbcDialect(NamedParameterJdbcOperations) is inherited — Spring Data JDBC
    // auto-detects PostgresDialect from DataSource metadata.
}
```

### 1.5 `SqliteJdbcConfiguration`

```java
package ai.javaclaw.persistence.dialect;

import ai.javaclaw.persistence.converter.JsonToStringConverter;
import ai.javaclaw.persistence.converter.StringToJsonConverter;
import ai.javaclaw.persistence.converter.sqlite.InstantToStringConverter;
import ai.javaclaw.persistence.converter.sqlite.StringToInstantConverter;
import ai.javaclaw.persistence.converter.sqlite.StringToUuidConverter;
import ai.javaclaw.persistence.converter.sqlite.UuidToStringConverter;
import ai.javaclaw.persistence.dialect.sqlite.SqliteJdbcDialect;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.jdbc.SqliteChatMemoryRepositoryDialect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jdbc.core.dialect.JdbcDialect;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PersistenceDialectProperties.class)
@ConditionalOnProperty(
        prefix = "javaclaw.persistence",
        name = "dialect",
        havingValue = "sqlite")
public class SqliteJdbcConfiguration extends AbstractJdbcConfiguration {

    private final ObjectMapper objectMapper;

    public SqliteJdbcConfiguration(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public JdbcDialect jdbcDialect(NamedParameterJdbcOperations operations) {
        return SqliteJdbcDialect.INSTANCE;
    }

    @Override
    protected List<?> userConverters() {
        return List.of(
                new UuidToStringConverter(),
                new StringToUuidConverter(),
                new InstantToStringConverter(),
                new StringToInstantConverter(),
                new JsonToStringConverter(objectMapper),
                new StringToJsonConverter(objectMapper)
        );
    }

    /**
     * Spring AI chat memory: replace the autoconfigured bean so it is built with the
     * bundled SQLite dialect. JdbcChatMemoryRepositoryAutoConfiguration is annotated
     * @ConditionalOnMissingBean and backs off when this bean exists.
     */
    @Bean
    public JdbcChatMemoryRepository jdbcChatMemoryRepository(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager) {
        return JdbcChatMemoryRepository.builder()
                .jdbcTemplate(jdbcTemplate)
                .dialect(new SqliteChatMemoryRepositoryDialect())
                .transactionManager(transactionManager)
                .build();
    }
}
```

**Note on `JdbcCustomConversions`:** both configurations inherit `AbstractJdbcConfiguration.jdbcCustomConversions(...)` which calls `userConverters()` and merges with dialect-provided converters. The legacy `@Primary JdbcCustomConversions` bean in the existing `JdbcConfig` is removed in task `transform-jdbc-config` — leaving it in place would shadow both configurations and break the whole chain.

### 1.6 Transformation of legacy `JdbcConfig`

File: `javaclaw-core/src/main/java/ai/javaclaw/config/JdbcConfig.java`

Action: **delete** the class. Its two responsibilities are redistributed:
1. The `@Primary JdbcCustomConversions` bean is removed entirely — superseded by `AbstractJdbcConfiguration.jdbcCustomConversions()` provided by the per-dialect configurations.
2. `MapToJsonbConverter`/`JsonbToMapConverter` imports and references are updated to the new package `ai.javaclaw.persistence.converter.postgres`.

No replacement `package-info.java` is needed.

---

## 2. Converters — Exact Signatures

### 2.1 Shared: `JsonToStringConverter` / `StringToJsonConverter`

```java
package ai.javaclaw.persistence.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;

@WritingConverter
public final class JsonToStringConverter implements Converter<Map<String, String>, String> {

    private final ObjectMapper objectMapper;

    public JsonToStringConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String convert(Map<String, String> source) {
        if (source == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(source);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize Map<String,String> to JSON", e);
        }
    }
}
```

```java
package ai.javaclaw.persistence.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

@ReadingConverter
public final class StringToJsonConverter implements Converter<String, Map<String, String>> {

    private static final TypeReference<Map<String, String>> TYPE =
            new TypeReference<Map<String, String>>() {};

    private final ObjectMapper objectMapper;

    public StringToJsonConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, String> convert(String source) {
        if (source == null || source.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(source, TYPE);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to deserialize JSON to Map<String,String>", e);
        }
    }
}
```

Scope: `Map<String, String>` is the only JSON shape used as entity field (confirmed by audit-jsonb: `mcp_servers.headers`). If a future entity needs a richer JSON shape, it gets its own converter pair in the same package.

### 2.2 SQLite only: `UuidToStringConverter` / `StringToUuidConverter`

```java
package ai.javaclaw.persistence.converter.sqlite;

import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;

@WritingConverter
public final class UuidToStringConverter implements Converter<UUID, String> {
    @Override
    public String convert(UUID source) {
        return source == null ? null : source.toString();
    }
}
```

```java
package ai.javaclaw.persistence.converter.sqlite;

import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

@ReadingConverter
public final class StringToUuidConverter implements Converter<String, UUID> {
    @Override
    public UUID convert(String source) {
        return (source == null || source.isBlank()) ? null : UUID.fromString(source);
    }
}
```

Not used by Postgres profile — `pgjdbc` maps `UUID` natively.

### 2.3 SQLite only: `InstantToStringConverter` / `StringToInstantConverter`

```java
package ai.javaclaw.persistence.converter.sqlite;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;

@WritingConverter
public final class InstantToStringConverter implements Converter<Instant, String> {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_INSTANT;

    @Override
    public String convert(Instant source) {
        return source == null ? null : ISO.format(source);
    }
}
```

```java
package ai.javaclaw.persistence.converter.sqlite;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

@ReadingConverter
public final class StringToInstantConverter implements Converter<String, Instant> {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_INSTANT;

    @Override
    public Instant convert(String source) {
        return (source == null || source.isBlank()) ? null : Instant.from(ISO.parse(source));
    }
}
```

Format: ISO-8601 UTC (`2026-04-11T12:34:56Z`). Sortable as text. No timezone suffix other than `Z`.

### 2.4 Moved Postgres converters

`MapToJsonbConverter` and `JsonbToMapConverter` currently live at `ai/javaclaw/mcp/converter/`. They are moved — not rewritten — to `ai/javaclaw/persistence/converter/postgres/` via `git mv`. The only changes are the package declaration and the `import` in `McpServer` (plus the new `PostgresJdbcConfiguration` importing them). Their bodies, which wrap `PGobject("jsonb", ...)`, stay identical.

---

## 3. ID Generation — 11 Callbacks

Pattern (one file per entity, all in `ai.javaclaw.persistence.id`):

```java
package ai.javaclaw.persistence.id;

import ai.javaclaw.users.AppUser;
import java.util.UUID;
import org.springframework.data.relational.core.mapping.event.BeforeConvertCallback;
import org.springframework.stereotype.Component;

@Component
public class AppUserIdGeneratorCallback implements BeforeConvertCallback<AppUser> {
    @Override
    public AppUser onBeforeConvert(AppUser entity) {
        if (entity.getId() != null) {
            return entity;
        }
        return entity.withId(UUID.randomUUID().toString());
    }
}
```

For entities without a `withId(...)` helper, the callback invokes the existing record `with`-copy or reconstructs via the canonical constructor (same approach as `TaskIdGeneratorCallback` which enumerates every field). Each callback is `@Component`-scanned; Spring Data JDBC picks them up automatically.

### 3.1 Complete list

| #  |                 Callback                 |        Entity         |        Entity package        |
|----|------------------------------------------|-----------------------|------------------------------|
| 1  | `AppUserIdGeneratorCallback`             | `AppUser`             | `ai.javaclaw.users`          |
| 2  | `VirtualFileIdGeneratorCallback`         | `VirtualFile`         | `ai.javaclaw.files`          |
| 3  | `SkillIdGeneratorCallback`               | `Skill`               | `ai.javaclaw.skills`         |
| 4  | `McpServerIdGeneratorCallback`           | `McpServer`           | `ai.javaclaw.mcp`            |
| 5  | `McpRoleAllowlistIdGeneratorCallback`    | `McpRoleAllowlist`    | `ai.javaclaw.mcp`            |
| 6  | `MemoryIdGeneratorCallback`              | `Memory`              | `ai.javaclaw.memory`         |
| 7  | `ToolExampleIdGeneratorCallback`         | `ToolExample`         | `ai.javaclaw.agent.pipeline` |
| 8  | `ConversationSummaryIdGeneratorCallback` | `ConversationSummary` | `ai.javaclaw.agent.pipeline` |
| 9  | `SkillRoleAllowlistIdGeneratorCallback`  | `SkillRoleAllowlist`  | `ai.javaclaw.skills`         |
| 10 | `RoleAgentConfigIdGeneratorCallback`     | `RoleAgentConfig`     | `ai.javaclaw.agent.config`   |
| 11 | `RoleModelAllowlistIdGeneratorCallback`  | `RoleModelAllowlist`  | `ai.javaclaw.agent.config`   |

Entities not needing a callback (documented in audit-id-generation for verification):
- `Task`, `RecurringTask`, `TaskExecution`, `ApprovalRequest`, `DeliveryQueue` — already have callbacks.
- `Conversation`, `ConversationChannelContext` — IDs are externally assigned.
- `UserSession` — id generated manually in `OpaqueSessionTokenService` (persists via raw INSERT, not `save()`).
- All `Long @Id` entities (`AgentQuota`, `ChatAuditLog`, `AuthAuditLog`, `TaskAuditLog`, `DeliveryAuditLog`, `SkillUsageAudit`, `CustomRole`, `RolePermission`, `ConversationShare`) — rely on DB identity/sequence. In SQLite these become `INTEGER PRIMARY KEY AUTOINCREMENT` which SQLite fills before insert; Spring Data JDBC uses the generated key via JDBC `Statement.RETURN_GENERATED_KEYS`.
- `config` — no Spring Data JDBC entity.

### 3.2 Critical bug fix: `McpRoleAllowlist`

Audit confirmed that `V34__mcp_role_allowlist.sql` declares the PK **without** `DEFAULT gen_random_uuid()`:

```sql
id VARCHAR(36) PRIMARY KEY,
```

and `McpRoleAllowlist.create(...)` passes `id = null`. Under Postgres the current code would fail with a NOT NULL / PK violation the first time it tries to persist a row; it works today only because no integration test exercises that path.

**Decision:**
1. Add `McpRoleAllowlistIdGeneratorCallback` (item #5 above). This fixes the insert both for Postgres and SQLite.
2. **Do not touch V34 DDL.** The PK constraint is correct as-is — the column must be `NOT NULL PRIMARY KEY` with no DB default; the value arrives from Java. No new Postgres migration is needed.
3. Add a unit test `McpRoleAllowlistIdGeneratorCallbackTest` plus an integration test that saves a `McpRoleAllowlist` row via Spring Data JDBC to prove the regression is closed before Phase 2 is declared done.

---

## 4. Schema SPI — Porting Rules

Every SQLite migration is a **hand port** of the corresponding Postgres file (same version number, including gaps at V15/V16). The following rules are mechanical.

### 4.1 Type mapping

|                         PostgreSQL                          |               SQLite                |                                         Notes                                         |
|-------------------------------------------------------------|-------------------------------------|---------------------------------------------------------------------------------------|
| `JSONB`                                                     | `TEXT`                              | Only column: `mcp_servers.headers`. Stored via shared `Map<String,String>` converter. |
| `UUID`                                                      | `TEXT`                              | Stored as 36-char canonical form via `UuidToStringConverter`. No DB default.          |
| `TIMESTAMPTZ`, `TIMESTAMP`                                  | `TEXT`                              | ISO-8601 UTC via `InstantToStringConverter`.                                          |
| `DATE`                                                      | `TEXT`                              | ISO-8601 date.                                                                        |
| `BIGSERIAL`, `BIGINT GENERATED ALWAYS AS IDENTITY`          | `INTEGER PRIMARY KEY AUTOINCREMENT` | Single-column PK only. SQLite fills via ROWID.                                        |
| `VARCHAR(n)`                                                | `TEXT`                              | SQLite ignores length.                                                                |
| `BOOLEAN`                                                   | `INTEGER`                           | `0`/`1`. JDBC driver handles transparently.                                           |
| `TEXT`, `INTEGER`, `SMALLINT`, `BIGINT`, `DOUBLE PRECISION` | unchanged                           | ANSI.                                                                                 |

### 4.2 Function / default mapping

|         PostgreSQL          |           SQLite            |                                                                                                         Notes                                                                                                         |
|-----------------------------|-----------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `DEFAULT gen_random_uuid()` | **removed**                 | Generation moves to `BeforeConvertCallback`. Postgres loses the default in new migration V42.                                                                                                                         |
| `DEFAULT now()`             | `DEFAULT (datetime('now'))` | Where column is `TEXT` and DDL-level default is still desired.                                                                                                                                                        |
| `now()` inline (DML)        | `datetime('now')`           | Handled in Query SPI, not in DDL.                                                                                                                                                                                     |
| `CURRENT_DATE`              | `date('now')`               | Only used in `agent_quotas` reset — moved to Query SPI.                                                                                                                                                               |
| `ON CONFLICT ... DO UPDATE` | `ON CONFLICT ... DO UPDATE` | SQLite 3.24+ upsert is syntactically compatible for simple cases. Where a target conflict-column matches the PK and update is straightforward, the same SQL string works on both. Handled per-statement in Query SPI. |
| `ILIKE`                     | `LIKE ... COLLATE NOCASE`   | Query SPI.                                                                                                                                                                                                            |
| `ctid`                      | `rowid`                     | Query SPI.                                                                                                                                                                                                            |
| `CONCURRENTLY` (index)      | **removed**                 | SQLite does not support; non-functional hint in Postgres too.                                                                                                                                                         |

### 4.3 V2 `init_chat_memory` — official SQLite schema

The SQLite V2 migration must be **byte-equivalent** to Spring AI's bundled `schema-sqlite.sql` (audit-spring-ai-schema §3). Content to write into `db/migration/sqlite/V2__init_chat_memory.sql`:

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

Copy verbatim from `spring-ai-model-chat-memory-repository-jdbc-2.0.0-SNAPSHOT.jar!/org/springframework/ai/chat/memory/repository/jdbc/schema-sqlite.sql`. `timestamp` is `INTEGER` (epoch millis) — **not TEXT**, because the bundled `SqliteChatMemoryRepositoryDialect` issues `ORDER BY timestamp` on a numeric column (see audit §5). Any deviation from the official schema will break Spring AI queries.

### 4.4 V10 `seed_default_users`

Current Postgres V10 uses `gen_random_uuid()::varchar` inside `INSERT ... VALUES` (not as column default), so the V42 "drop defaults" migration does not touch it.

**Decision:** use literal UUIDs in **both** dialects. Rationale: deterministic IDs for seed accounts improve test reproducibility, match CI fixtures, and remove the only remaining `gen_random_uuid()` call site in DML.

- SQLite `db/migration/sqlite/V10__seed_default_users.sql`:

  ```sql
  INSERT INTO users (id, username, password_hash, role, active, created_at, updated_at)
  VALUES ('00000000-0000-0000-0000-000000000001', 'admin', '...', 'ADMIN', 1, datetime('now'), datetime('now'));
  INSERT INTO users (id, username, password_hash, role, active, created_at, updated_at)
  VALUES ('00000000-0000-0000-0000-000000000002', 'user',  '...', 'USER',  1, datetime('now'), datetime('now'));
  ```
- Postgres: **add a new migration** `V43__align_seed_user_ids.sql` that performs `UPDATE users SET id = '0000...01' WHERE username = 'admin'` and similarly for `user`. Direct `UPDATE` on PK is allowed under Postgres as long as no FKs reference these IDs at migration time (verified: V10 runs before any table that FKs `users.id` has seed rows; task `schema-sqlite-port` performs the check). V10 itself is **not** edited — the project rule forbids modifying applied migrations.
- Existing Postgres deployments (none yet in prod) would receive the new IDs via V43.
- The V43 migration is added to `db/migration/postgresql/` in the same PR as `schema-sqlite-port`. The SQLite side uses literals directly in its V10 — no V43 counterpart on SQLite.

### 4.5 V42 `drop_db_uuid_defaults` (Postgres-only)

Created in task `drop-db-uuid-defaults` before the git-mv. Contents:

```sql
ALTER TABLE tasks                ALTER COLUMN id DROP DEFAULT;
ALTER TABLE recurring_tasks      ALTER COLUMN id DROP DEFAULT;
ALTER TABLE users                ALTER COLUMN id DROP DEFAULT;
ALTER TABLE virtual_files        ALTER COLUMN id DROP DEFAULT;
ALTER TABLE skills               ALTER COLUMN id DROP DEFAULT;
ALTER TABLE mcp_servers          ALTER COLUMN id DROP DEFAULT;
ALTER TABLE config               ALTER COLUMN id DROP DEFAULT;
ALTER TABLE conversation_summaries ALTER COLUMN id DROP DEFAULT;
ALTER TABLE tool_examples        ALTER COLUMN id DROP DEFAULT;
ALTER TABLE memories             ALTER COLUMN id DROP DEFAULT;
ALTER TABLE skill_role_allowlist ALTER COLUMN id DROP DEFAULT;
ALTER TABLE role_agent_config    ALTER COLUMN id DROP DEFAULT;
ALTER TABLE role_model_allowlist ALTER COLUMN id DROP DEFAULT;
```

`mcp_role_allowlist` is absent from this list — it never had a default (audit §5). `conversations` is absent — its id is externally assigned.

The corresponding SQLite `V42__drop_db_uuid_defaults.sql` is an empty sentinel (comment only) — SQLite never had the defaults. A file must still exist so both vendor folders track identical version numbers; Flyway is fine with a no-op script.

### 4.6 Transaction constraints (SQLite Flyway)

- No `BEGIN`/`COMMIT` / `SAVEPOINT` blocks inside migration files.
- Flyway wraps each migration in a single implicit transaction.
- No `CREATE INDEX CONCURRENTLY`.
- Partial indexes are supported (SQLite has `CREATE INDEX ... WHERE ...`); port as-is where used.

---

## 5. Query SPI — 12 Dialect-Sensitive Fix-ups

Each dialect-sensitive query becomes an interface + two impls. Business code depends on the interface only. Impl package: `ai.javaclaw.persistence.postgres.*` / `ai.javaclaw.persistence.sqlite.*`. Interfaces live next to the original repository class so the existing `@Autowired Xyz` injections stay valid.

All interfaces get `@ConditionalOnProperty` on the impl classes: Postgres impls match `havingValue="postgresql", matchIfMissing=true`, SQLite impls match `havingValue="sqlite"`.

### 5.1 `ChannelContextService.java:26` — `ON CONFLICT ... DO UPDATE`

**Interface:** `ai.javaclaw.channels.ChannelContextQueryRepository`

```java
public interface ChannelContextQueryRepository {
    void upsert(String conversationId, String channelName, String channelKey, Instant now);
}
```

**Postgres impl SQL** (extracted verbatim):

```sql
INSERT INTO conversation_channel_context (conversation_id, channel_name, channel_key, updated_at)
VALUES (:conversationId, :channelName, :channelKey, :now)
ON CONFLICT (conversation_id) DO UPDATE SET
    channel_name = EXCLUDED.channel_name,
    channel_key  = EXCLUDED.channel_key,
    updated_at   = EXCLUDED.updated_at
```

**SQLite impl SQL** — identical (SQLite 3.24+ accepts the same upsert syntax and `EXCLUDED.*`):

```sql
INSERT INTO conversation_channel_context (conversation_id, channel_name, channel_key, updated_at)
VALUES (:conversationId, :channelName, :channelKey, :now)
ON CONFLICT (conversation_id) DO UPDATE SET
    channel_name = excluded.channel_name,
    channel_key  = excluded.channel_key,
    updated_at   = excluded.updated_at
```

Despite the SQL being syntactically identical, the query still gets two impls — this keeps the pattern uniform and leaves room for the SQLite impl to diverge if the SQL ever grows.

### 5.2 `ConversationQueryService.java:156` + `:47` — `ctid ASC` tiebreaker

**Interface:** `ai.javaclaw.conversations.ConversationQueryRepository`

```java
public interface ConversationQueryRepository {
    List<ChatMemoryRow> findMessagesOrdered(String conversationId);
    Optional<String> findLastMessagePreview(String conversationId);  // line 47
}
```

**Postgres impl** keeps the existing SQL with `ctid ASC` tiebreaker:

```sql
SELECT content, type, "timestamp"
  FROM SPRING_AI_CHAT_MEMORY
 WHERE conversation_id = :convId
 ORDER BY "timestamp" ASC, ctid ASC
```

**SQLite impl** replaces `ctid` with `rowid` and drops the quoting on `timestamp` (SQLite schema uses unquoted INTEGER column):

```sql
SELECT content, type, timestamp
  FROM SPRING_AI_CHAT_MEMORY
 WHERE conversation_id = :convId
 ORDER BY timestamp ASC, rowid ASC
```

Both impls live in the same package as the Spring AI dialect so the schema assumptions stay local.

### 5.3 `ConversationRepository.java:17` — `now()`

**Interface:** `ConversationQueryRepository.touchTitleIfMissing(String id, String title)` (added to the same interface as 5.2).

**Postgres impl:**

```sql
UPDATE conversations SET updated_at = now(),
                         title      = COALESCE(title, :title)
 WHERE id = :id
```

**SQLite impl:**

```sql
UPDATE conversations SET updated_at = datetime('now'),
                         title      = COALESCE(title, :title)
 WHERE id = :id
```

### 5.4 `AppUserRepository.java:22`, `:26`, `:30` — three `now()` updates

**Interface:** `ai.javaclaw.users.AppUserQueryRepository`

```java
public interface AppUserQueryRepository {
    void deactivate(String userId);
    void updatePassword(String userId, String passwordHash);
    void updateRole(String userId, String role);
}
```

**Postgres impl SQL** uses `now()`; **SQLite impl SQL** uses `datetime('now')`. Three one-statement methods each, same shape as 5.3.

### 5.5 `AgentQuotaRepository.java:15`, `:19`, `:24` — `now()` + `CURRENT_DATE`

**Interface:** `ai.javaclaw.agent.quota.AgentQuotaQueryRepository`

```java
public interface AgentQuotaQueryRepository {
    void incrementUsage(String userId);
    void resetDaily(String userId);
    void updateLimit(String userId, int limit);
}
```

**Postgres impl:**

```sql
UPDATE agent_quotas SET daily_used = daily_used + 1, updated_at = now() WHERE user_id = :userId;
UPDATE agent_quotas SET daily_used = 0, reset_date = CURRENT_DATE, updated_at = now() WHERE user_id = :userId;
UPDATE agent_quotas SET daily_limit = :limit, updated_at = now() WHERE user_id = :userId;
```

**SQLite impl:**

```sql
UPDATE agent_quotas SET daily_used = daily_used + 1, updated_at = datetime('now') WHERE user_id = :userId;
UPDATE agent_quotas SET daily_used = 0, reset_date = date('now'), updated_at = datetime('now') WHERE user_id = :userId;
UPDATE agent_quotas SET daily_limit = :limit, updated_at = datetime('now') WHERE user_id = :userId;
```

### 5.6 `MemoryRepository.java:20`, `:24` — `ILIKE`

**Interface:** `ai.javaclaw.memory.MemoryQueryRepository`

```java
public interface MemoryQueryRepository {
    List<Memory> searchForOwner(String ownerId, String query);
    List<Memory> searchGlobal(String query);
}
```

**Postgres impl** (verbatim from current source):

```sql
SELECT * FROM memories
 WHERE owner_id = :ownerId
   AND (key   ILIKE '%' || :query || '%'
        OR value ILIKE '%' || :query || '%')
 ORDER BY updated_at DESC
```

**SQLite impl:**

```sql
SELECT * FROM memories
 WHERE owner_id = :ownerId
   AND (key   LIKE '%' || :query || '%' COLLATE NOCASE
        OR value LIKE '%' || :query || '%' COLLATE NOCASE)
 ORDER BY updated_at DESC
```

`searchGlobal` is the same shape but with `owner_id IS NULL`. `COLLATE NOCASE` covers ASCII case-insensitive matching, which is the existing semantics (Postgres `ILIKE` is also ASCII-centric unless a non-deterministic collation is installed).

### 5.7 Rewritable queries (25 total, from audit §)

The 25 `rewritable` queries from audit-raw-sql.md are handled in task `query-spi-refactor` by converting them to Spring Data JDBC derived queries (`findByUsernameAndActiveTrue`, `deleteByServerId`, `existsByRoleAndPermission`, etc.). These do **not** get Query SPI interfaces — they are simply replaced in place in the existing repository class. Full list is already tabulated in audit-raw-sql §; each row classified `rewritable` has a concrete derived-query suggestion.

### 5.8 Portable queries (18 total)

No action. `OpaqueSessionTokenService` raw INSERT, `JdbcAppendableChatMemoryRepository` append INSERT, `AuthAuditService` INSERT, `UserSessionRepository` queries, `ConversationQueryService` ANSI selects, `ConversationShare`/`ConversationQueryService` counts — all ANSI-portable and stay as-is.

---

## 6. Spring AI Chat Memory — No Custom Dialect

**Finding (audit-spring-ai-schema §4):** Spring AI 2.0.0-SNAPSHOT already ships `org.springframework.ai.chat.memory.repository.jdbc.SqliteChatMemoryRepositoryDialect` in `spring-ai-model-chat-memory-repository-jdbc-2.0.0-SNAPSHOT.jar`. Writing our own is strictly duplicated work.

**Decision:** import the bundled class. The registration site is the `@Bean JdbcChatMemoryRepository` inside `SqliteJdbcConfiguration` (see §1.5 above) — reproduced here for clarity:

```java
@Bean
public JdbcChatMemoryRepository jdbcChatMemoryRepository(
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager) {
    return JdbcChatMemoryRepository.builder()
            .jdbcTemplate(jdbcTemplate)
            .dialect(new org.springframework.ai.chat.memory.repository.jdbc.SqliteChatMemoryRepositoryDialect())
            .transactionManager(transactionManager)
            .build();
}
```

This bean triggers `@ConditionalOnMissingBean` on `JdbcChatMemoryRepositoryAutoConfiguration.jdbcChatMemoryRepository(...)` (verified in audit §7) so the autoconfig bean is suppressed and does not attempt its `JdbcChatMemoryRepositoryDialect.from(dataSource)` autodetect path.

**Postgres path:** no change. `PostgresJdbcConfiguration` declares **no** `JdbcChatMemoryRepository` bean, so the autoconfig builds the default repository with `PostgresChatMemoryRepositoryDialect` via autodetect.

**Schema:** V2 in both vendor folders creates `SPRING_AI_CHAT_MEMORY` matching the bundled schema files byte-for-byte (see §4.3). `spring.ai.chat.memory.repository.jdbc.initialize-schema: never` remains in `application.yaml` so Flyway owns the table.

**No new files in `ai.javaclaw.persistence.chatmemory`.** The original plan's `SqliteJdbcChatMemoryRepositoryDialect.java` listed under "New Files" is **deleted** from the backlog. Task `spring-ai-sqlite-dialect` is retitled to "wire bundled SqliteChatMemoryRepositoryDialect via custom @Bean" and its implementation collapses to the bean method above plus the `SqliteChatMemoryIntegrationTest`.

---

## 7. Maven Coordinates

### 7.1 SQLite JDBC driver

```xml
<dependency>
    <groupId>org.xerial</groupId>
    <artifactId>sqlite-jdbc</artifactId>
    <version>3.46.1.3</version>
    <scope>runtime</scope>
</dependency>
```

- Version `3.46.1.3` is the last stable release compatible with JDK 21 and Spring Boot 4.0.5. `xerial/sqlite-jdbc` 3.46.x publishes multi-target native binaries including `aarch64-macos`, `x86_64-linux`, `x86_64-macos`, covering every developer and CI target currently used.
- Added to `javaclaw-app/pom.xml` at runtime scope.
- Added to `javaclaw-core/pom.xml` at test scope for `SqliteFlywayBootIntegrationTest` and `SqliteRepositoryIntegrationTest`.
- Builder task `deps-and-properties` must verify the coordinate resolves against Maven Central before pinning; if a newer stable patch exists at the time of execution (e.g. 3.46.1.4), it is acceptable to use it, provided it is a bugfix release on the 3.46 line.

### 7.2 JobRunr SQLite storage provider

**No extra artifact.** The main `org.jobrunr:jobrunr` jar already contains `org.jobrunr.storage.sql.sqlite.SqLiteStorageProvider`. The configuration is:

```java
StorageProvider storageProvider = SqlStorageProviderFactory.using(sqliteDataSource);
```

`SqlStorageProviderFactory` auto-detects the product name `"SQLite"` from `DataSource` metadata and instantiates `SqLiteStorageProvider`. No new dependency entry in any `pom.xml`. Verified against the JobRunr version already pinned in the root `pom.xml` by `project_mvp_postgresql_migration` memory (and confirmed by the plan's `Relevant Files` note at line 133 of `multi-dialect-persistence-sqlite-standalone.md`).

### 7.3 Flyway SQLite support

**No extra artifact.** Spring Boot 4.0.5 pulls `flyway-core` which includes native SQLite support (Community edition). Unlike Postgres which requires the separate `flyway-database-postgresql` artifact (already present), SQLite is bundled inside `flyway-core`. No change to `pom.xml` beyond what already exists.

### 7.4 Existing dependencies — unchanged

|                       Artifact                        |                           Status                           |
|-------------------------------------------------------|------------------------------------------------------------|
| `postgresql` JDBC driver                              | stays in `javaclaw-app/pom.xml`                            |
| `flyway-database-postgresql`                          | stays                                                      |
| `spring-ai-starter-model-chat-memory-repository-jdbc` | stays, already bundles `SqliteChatMemoryRepositoryDialect` |

---

## 8. Spring AI Autoconfig — Verification Details

From audit-spring-ai-schema §7 (verified against decompiled `JdbcChatMemoryRepositoryAutoConfiguration.class`):

```java
@AutoConfiguration(before = ChatMemoryAutoConfiguration.class)
@ConditionalOnClass({JdbcChatMemoryRepository.class, DataSource.class, JdbcTemplate.class})
@EnableConfigurationProperties(JdbcChatMemoryRepositoryProperties.class)
public class JdbcChatMemoryRepositoryAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean    // <-- our SqliteJdbcConfiguration bean replaces this
    JdbcChatMemoryRepository jdbcChatMemoryRepository(JdbcTemplate jt, DataSource ds) {
        JdbcChatMemoryRepositoryDialect dialect = JdbcChatMemoryRepositoryDialect.from(ds);
        return JdbcChatMemoryRepository.builder()
                .jdbcTemplate(jt)
                .dialect(dialect)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    @Conditional(OnJdbcChatMemoryRepositoryDatasourceInitializationCondition.class)
    JdbcChatMemoryRepositorySchemaInitializer jdbcChatMemoryScriptDatabaseInitializer(
            DataSource ds, JdbcChatMemoryRepositoryProperties props) {
        return new JdbcChatMemoryRepositorySchemaInitializer(ds, props);
    }
}
```

**Conclusion:** exposing a `JdbcChatMemoryRepository` bean in any `@Configuration` disables the autoconfig bean via `@ConditionalOnMissingBean`. This is the sanctioned extension point. Dialect is an inline object inside the repository, not a standalone bean — we cannot "override just the dialect"; we must override the repository bean itself.

**Schema initializer:** our `spring.ai.chat.memory.repository.jdbc.initialize-schema=never` property disables `JdbcChatMemoryRepositorySchemaInitializer` via `JdbcChatMemoryRepositoryProperties`; Flyway owns the schema.

**Why not exclude the autoconfig class:** `@EnableAutoConfiguration(exclude = JdbcChatMemoryRepositoryAutoConfiguration.class)` is unnecessary because `@ConditionalOnMissingBean` already does the job, and excluding the class would also disable the schema-initializer bean that we want the option to use for other databases.

---

## 9. Acceptance — Design Self-Check

|                      Criterion                       |                                            Status                                             |
|------------------------------------------------------|-----------------------------------------------------------------------------------------------|
| Full API surface (classes, methods, signatures)      | Covered in §1-§6                                                                              |
| Every audit-jsonb finding resolved                   | Yes — single TEXT converter, §2.1, §4.1                                                       |
| Every audit-raw-sql dialect-sensitive row has a plan | Yes — §5.1 through §5.6 cover all 12                                                          |
| Every audit-raw-sql rewritable row has a disposition | Yes — §5.7 delegates to task `query-spi-refactor` using audit's own derived-query suggestions |
| Every audit-id-generation callback is listed         | Yes — 11 entries in §3.1                                                                      |
| Critical `McpRoleAllowlist` bug fixed                | Yes — §3.2 adds callback; DDL left alone; test required                                       |
| Spring AI SQLite dialect source decided              | Yes — use bundled class, §6                                                                   |
| V10 seed-users decision fixed                        | Yes — literals in both dialects, V43 added on Postgres side, §4.4                             |
| V42 drop-defaults migration spec                     | Yes — exact ALTER list in §4.5                                                                |
| Maven coordinates fixed                              | Yes — §7                                                                                      |
| Spring AI autoconfig override mechanism verified     | Yes — §8                                                                                      |
| Package layout                                       | §0                                                                                            |
| No open questions                                    | All "TODO" / "decide later" items from the plan are resolved here                             |

**Phase 2-6 builders can proceed without further design clarification.**

---

## 10. Cross-references

- Plan: `specs/multi-dialect-persistence-sqlite-standalone.md`
- Audit inputs: `specs/audit-jsonb.md`, `specs/audit-raw-sql.md`, `specs/audit-id-generation.md`, `specs/audit-spring-ai-schema.md`
- Existing reference callback: `javaclaw-core/src/main/java/ai/javaclaw/tasks/TaskIdGeneratorCallback.java`
- Existing legacy JdbcConfig (to be deleted): `javaclaw-core/src/main/java/ai/javaclaw/config/JdbcConfig.java`
- Existing Postgres converters (to be moved): `javaclaw-core/src/main/java/ai/javaclaw/mcp/converter/MapToJsonbConverter.java`, `.../JsonbToMapConverter.java`

