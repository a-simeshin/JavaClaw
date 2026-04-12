package ai.javaclaw.persistence.sqlite;

import static org.assertj.core.api.Assertions.assertThat;

import ai.javaclaw.memory.Memory;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class MemoryQueryRepositorySqliteImplTest {

    private MemoryQueryRepositorySqliteImpl repository;
    private NamedParameterJdbcTemplate jdbc;
    private SingleConnectionDataSource dataSource;
    private String ownerId;

    @BeforeEach
    void setUp() {
        this.dataSource = SqliteTestSupport.newInMemoryDataSource();
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
        jdbc.getJdbcTemplate()
                .execute(
                        """
                        CREATE TABLE memories (
                            id         TEXT PRIMARY KEY,
                            owner_id   TEXT,
                            key        TEXT NOT NULL,
                            content    TEXT NOT NULL,
                            category   TEXT,
                            created_at TEXT NOT NULL DEFAULT (datetime('now')),
                            updated_at TEXT NOT NULL DEFAULT (datetime('now'))
                        )
                        """);
        this.repository = new MemoryQueryRepositorySqliteImpl(jdbc);
        this.ownerId = UUID.randomUUID().toString();

        insertMemory(ownerId, "favourite-color", "I love BLUE sky");
        insertMemory(ownerId, "pet-name", "Rex the dog");
        insertMemory(null, "global-fact", "The Sun is blue? no, YELLOW");
    }

    @AfterEach
    void tearDown() {
        if (dataSource != null) {
            dataSource.destroy();
        }
    }

    @Test
    void searchForOwnerMatchesKeyCaseInsensitive() {
        List<Memory> found = repository.searchForOwner(ownerId, "FAVOURITE");
        assertThat(found).extracting(Memory::key).containsExactly("favourite-color");
    }

    @Test
    void searchForOwnerMatchesContentCaseInsensitive() {
        List<Memory> found = repository.searchForOwner(ownerId, "blue");
        assertThat(found).extracting(Memory::key).containsExactly("favourite-color");
    }

    @Test
    void searchForOwnerIgnoresOtherOwners() {
        List<Memory> found = repository.searchForOwner("some-other-owner", "blue");
        assertThat(found).isEmpty();
    }

    @Test
    void searchGlobalOnlyReturnsOwnerlessRows() {
        List<Memory> found = repository.searchGlobal("yellow");
        assertThat(found).extracting(Memory::key).containsExactly("global-fact");
    }

    private void insertMemory(final String owner, final String key, final String content) {
        final MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID().toString())
                .addValue("owner", owner)
                .addValue("key", key)
                .addValue("content", content);
        jdbc.update(
                """
                INSERT INTO memories (id, owner_id, key, content, category)
                VALUES (:id, :owner, :key, :content, NULL)
                """,
                params);
    }
}
