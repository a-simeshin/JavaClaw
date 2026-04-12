package ai.javaclaw.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * Integration test that runs the full SQLite Flyway migration chain against an
 * in-memory database and verifies that every hand-ported migration applies
 * cleanly. This is the acceptance gate for
 * {@code specs/multi-dialect-persistence-sqlite-standalone.md} Task #14.
 *
 * <p>Notes:
 * <ul>
 *   <li>Uses a {@link SingleConnectionDataSource} wrapping one physical
 *       JDBC connection to an in-memory SQLite database so that schema created
 *       by Flyway survives into the subsequent {@code info()} call.</li>
 *   <li>Does not depend on {@code flyway-database-sqlite} — SQLite support is
 *       shipped with {@code flyway-core} out of the box.</li>
 * </ul>
 */
class SqliteFlywayBootIntegrationTest {

    @Test
    void allSqliteMigrationsApplyCleanly() {
        final SingleConnectionDataSource ds = new SingleConnectionDataSource();
        ds.setDriverClassName("org.sqlite.JDBC");
        ds.setUrl("jdbc:sqlite::memory:");
        ds.setSuppressClose(true);

        try {
            final Flyway flyway = Flyway.configure()
                    .dataSource(ds)
                    .locations("classpath:db/migration/sqlite")
                    .cleanDisabled(false)
                    .load();

            final MigrateResult result = flyway.migrate();

            // 39 files in sqlite/ after V2 squash: 38 in javaclaw-core (V1, V4..V42 minus gaps)
            // + V2 (squashed chat_memory) in javaclaw-memory. Every one must have applied.
            assertThat(result.migrationsExecuted)
                    .as("number of migrations executed")
                    .isEqualTo(39);

            final MigrationInfo[] all = flyway.info().all();
            assertThat(all).as("Flyway.info().all()").hasSizeGreaterThanOrEqualTo(39);

            assertThat(Arrays.stream(all).allMatch(mi -> mi.getState() == MigrationState.SUCCESS))
                    .as("every migration in SUCCESS state")
                    .isTrue();

            final MigrationInfo current = flyway.info().current();
            assertThat(current).as("current migration").isNotNull();
            assertThat(current.getVersion()).as("current version object").isNotNull();
            assertThat(current.getVersion().getVersion())
                    .as("current version string")
                    .isEqualTo("42");
        } finally {
            ds.destroy();
        }
    }
}
