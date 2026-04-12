package ai.javaclaw.persistence.sqlite;

import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * Shared helpers for the in-memory SQLite unit tests under
 * {@code ai.javaclaw.persistence.sqlite}. Keeps a single JDBC connection open per
 * test so that schema created in {@code @BeforeEach} survives into subsequent
 * statements (the in-memory DB lives inside the connection).
 */
final class SqliteTestSupport {

    private SqliteTestSupport() {}

    static SingleConnectionDataSource newInMemoryDataSource() {
        final SingleConnectionDataSource ds = new SingleConnectionDataSource();
        ds.setDriverClassName("org.sqlite.JDBC");
        ds.setUrl("jdbc:sqlite::memory:");
        ds.setSuppressClose(true);
        return ds;
    }
}
