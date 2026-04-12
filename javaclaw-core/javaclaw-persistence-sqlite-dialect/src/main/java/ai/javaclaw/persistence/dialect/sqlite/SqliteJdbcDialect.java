package ai.javaclaw.persistence.dialect.sqlite;

import org.springframework.data.jdbc.core.dialect.JdbcArrayColumns;
import org.springframework.data.jdbc.core.dialect.JdbcDialect;
import org.springframework.data.relational.core.dialect.AnsiDialect;
import org.springframework.data.relational.core.dialect.ArrayColumns;
import org.springframework.data.relational.core.dialect.IdGeneration;
import org.springframework.data.relational.core.dialect.LimitClause;
import org.springframework.data.relational.core.sql.IdentifierProcessing;

/**
 * Spring Data JDBC dialect for SQLite.
 *
 * <p>Spring Data Relational ships dialects only for the 8 canonical databases its
 * {@code DialectResolver} recognises (HSQL, H2, MySQL, MariaDB, PostgreSQL, SQL Server,
 * DB2, Oracle). SQLite is out of scope for that resolver, so any application that targets
 * it must supply its own dialect — this class fills that gap.
 *
 * <p>Lives in {@code javaclaw-persistence-sqlite-dialect}, a leaf module with no
 * Spring Boot / Spring AI dependencies. Both {@code javaclaw-core} (for production wiring
 * via {@code SqliteJdbcConfiguration}) and {@code javaclaw-memory} (test-scope, for
 * {@code JdbcChatMemorySqliteIT}) depend on this module, which lets the two share a single
 * dialect implementation instead of maintaining a duplicate test-only copy.
 *
 * <p>Extends {@link AnsiDialect} and overrides only the pieces that differ from ANSI SQL:
 * <ul>
 *   <li>{@link LimitClause} — SQLite uses {@code LIMIT n OFFSET m}, no {@code FETCH FIRST}.</li>
 *   <li>{@link ArrayColumns} — SQLite has no native array type.</li>
 *   <li>{@link IdentifierProcessing} — ANSI quoting keeps {@code "timestamp"} and friends stable.</li>
 *   <li>{@link IdGeneration} — Java-side UUID / {@code INTEGER PRIMARY KEY AUTOINCREMENT}, no
 *       sequences and no {@code RETURNING} clauses.</li>
 * </ul>
 *
 * <p>Mirrors the contract of Spring Data JDBC's dialect implementations so the bean returned from
 * {@code jdbcDialect(NamedParameterJdbcOperations)} stays a valid {@link JdbcDialect}.
 */
@SuppressWarnings("NullableProblems")
public final class SqliteJdbcDialect extends AnsiDialect implements JdbcDialect {

    public static final SqliteJdbcDialect INSTANCE = new SqliteJdbcDialect();

    /**
     * SQLite-specific {@link IdGeneration} overrides:
     *
     * <ul>
     *   <li>{@code supportedForBatchOperations = false} — sqlite-jdbc's
     *       {@link java.sql.Statement#executeBatch()} + {@link java.sql.Statement#getGeneratedKeys()}
     *       does not return one key per batched row reliably. Spring Data JDBC's
     *       {@code IdGeneratingBatchInsertStrategy} branches on this flag and, when
     *       {@code false}, falls back to serial single-row inserts that use
     *       {@code INSERT … returning generated keys} one at a time — which sqlite-jdbc
     *       does support. Returning {@code true} here is what causes
     *       {@code "After saving the identifier must not be null"} in
     *       {@code JdbcAggregateTemplate.afterExecute}.</li>
     *   <li>{@code sequencesSupported = false} — SQLite has no native {@code CREATE SEQUENCE}.</li>
     * </ul>
     */
    private static final IdGeneration SQLITE_ID_GENERATION = new IdGeneration() {
        @Override
        public boolean supportedForBatchOperations() {
            return false;
        }

        @Override
        public boolean sequencesSupported() {
            return false;
        }
    };

    private static final LimitClause LIMIT_CLAUSE = new LimitClause() {
        @Override
        public String getLimit(long limit) {
            return "LIMIT " + limit;
        }

        @Override
        public String getOffset(long offset) {
            // SQLite requires a LIMIT when OFFSET is present; -1 means "no upper bound".
            return "LIMIT -1 OFFSET " + offset;
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

    private SqliteJdbcDialect() {
        // singleton — use SqliteJdbcDialect.INSTANCE
    }

    @Override
    public LimitClause limit() {
        return LIMIT_CLAUSE;
    }

    /**
     * {@link JdbcDialect} narrows the return type of {@link AnsiDialect#getArraySupport()}
     * from {@link ArrayColumns} to {@link JdbcArrayColumns}. SQLite has no native array type,
     * so we return the JDBC-flavoured {@code Unsupported} singleton.
     */
    @Override
    public JdbcArrayColumns getArraySupport() {
        return JdbcArrayColumns.Unsupported.INSTANCE;
    }

    @Override
    public IdentifierProcessing getIdentifierProcessing() {
        // SQLite folds unquoted identifiers to their declared case; use ANSI quoting.
        return IdentifierProcessing.ANSI;
    }

    @Override
    public IdGeneration getIdGeneration() {
        return SQLITE_ID_GENERATION;
    }
}
