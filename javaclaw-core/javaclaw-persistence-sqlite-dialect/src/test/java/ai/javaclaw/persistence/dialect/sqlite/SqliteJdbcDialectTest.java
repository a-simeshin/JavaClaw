package ai.javaclaw.persistence.dialect.sqlite;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.data.jdbc.core.dialect.JdbcArrayColumns;
import org.springframework.data.relational.core.dialect.IdGeneration;
import org.springframework.data.relational.core.dialect.LimitClause;
import org.springframework.data.relational.core.sql.IdentifierProcessing;

/**
 * Unit tests for {@link SqliteJdbcDialect}.
 *
 * <p>Verifies the SQLite-specific overrides that distinguish this dialect from the stock
 * {@code AnsiDialect}: the {@code LIMIT/OFFSET} grammar, the lack of array support, ANSI
 * identifier quoting, and the default id-generation strategy.
 */
class SqliteJdbcDialectTest {

    private final SqliteJdbcDialect dialect = SqliteJdbcDialect.INSTANCE;

    @Test
    void limitClauseUsesSqliteLimitSyntax() {
        LimitClause limit = dialect.limit();
        assertThat(limit.getLimit(10)).isEqualTo("LIMIT 10");
    }

    @Test
    void limitClauseUsesSqliteOffsetSyntax() {
        LimitClause limit = dialect.limit();
        // SQLite requires LIMIT even when only OFFSET is given; -1 means "no upper bound".
        assertThat(limit.getOffset(20)).isEqualTo("LIMIT -1 OFFSET 20");
    }

    @Test
    void limitClauseCombinesLimitAndOffset() {
        LimitClause limit = dialect.limit();
        assertThat(limit.getLimitOffset(10, 20)).isEqualTo("LIMIT 10 OFFSET 20");
    }

    @Test
    void limitClauseIsAppliedAfterOrderBy() {
        LimitClause limit = dialect.limit();
        assertThat(limit.getClausePosition()).isEqualTo(LimitClause.Position.AFTER_ORDER_BY);
    }

    @Test
    void arrayColumnsAreUnsupported() {
        JdbcArrayColumns arraySupport = dialect.getArraySupport();
        assertThat(arraySupport).isSameAs(JdbcArrayColumns.Unsupported.INSTANCE);
        assertThat(arraySupport.isSupported()).isFalse();
    }

    @Test
    void identifierProcessingIsAnsi() {
        assertThat(dialect.getIdentifierProcessing()).isSameAs(IdentifierProcessing.ANSI);
    }

    @Test
    void idGenerationDisablesBatchOperationsAndSequences() {
        // SQLite-specific: sqlite-jdbc's executeBatch() + getGeneratedKeys() does not return
        // one key per batched row, so Spring Data JDBC's IdGeneratingBatchInsertStrategy
        // must take the serial-insert fallback branch — hence supportedForBatchOperations = false.
        // SQLite has no CREATE SEQUENCE either.
        IdGeneration idGeneration = dialect.getIdGeneration();
        assertThat(idGeneration.supportedForBatchOperations()).isFalse();
        assertThat(idGeneration.sequencesSupported()).isFalse();
    }
}
