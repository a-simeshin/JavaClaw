# JSONB Audit — JavaClaw

**Date:** 2026-04-11
**Scope:** `javaclaw-core/src/main/resources/db/migration/`, `javaclaw-core/src/main/java/`, `javaclaw-api/src/main/java/`, `javaclaw-security/src/main/java/`

---

## JSONB Columns Found

|  Column   |     Table     |          Migration           | SQL inside JSON? | Decision |
|-----------|---------------|------------------------------|------------------|----------|
| `headers` | `mcp_servers` | `V8__create_mcp_servers.sql` | No               | **TEXT** |

---

## Detail: `mcp_servers.headers`

### Migration context

```sql
-- V8__create_mcp_servers.sql, line 10
headers    JSONB,
```

Stores HTTP headers for MCP server connections as a flat key-value map (`Map<String, String>`).

### Java usage

Entity: `javaclaw-core/src/main/java/ai/javaclaw/mcp/McpServer.java`

```java
@Column("headers") Map<String, String> headers,
```

Access pattern: the field is **read and written as a whole object** via Spring Data JDBC.
No SQL operators (`->`, `->>`, `@>`, `jsonb_path_exists`, `jsonb_extract_path`, `json_extract`)
are present anywhere in the Java codebase — searches across all three modules returned zero hits.

### Verdict: TEXT

The column holds a flat JSON object that is never queried structurally inside SQL.
It can safely be stored as `TEXT` (JSON serialised string) instead of `JSONB`,
making it portable to SQLite without any dialect split.

**Suggested type for dialect-neutral DDL:**

```sql
-- PostgreSQL (compatible, no functional change)
headers TEXT,

-- SQLite (json1 extension, same TEXT storage)
headers TEXT,
```

No dialect-specific SQL variants are needed.

---

## Summary

|   Decision    | Count |        Columns        |
|---------------|-------|-----------------------|
| TEXT          | 1     | `mcp_servers.headers` |
| dialect-split | 0     | —                     |

**Total JSONB columns audited: 1**
**All 1 columns → TEXT migration: safe, no dialect split required.**
