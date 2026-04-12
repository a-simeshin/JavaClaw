package ai.javaclaw.persistence.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import ai.javaclaw.memory.Memory;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJdbcTest
@Testcontainers
@ActiveProfiles("test")
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import(MemoryQueryRepositoryPgImpl.class)
class MemoryQueryRepositoryPgImplTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    MemoryQueryRepositoryPgImpl repository;

    @Autowired
    NamedParameterJdbcTemplate jdbc;

    private String ownerId;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM memories", Map.of());
        ownerId = UUID.randomUUID().toString();
        jdbc.update(
                """
                INSERT INTO users (id, username, password_hash, role, active, created_at, updated_at)
                VALUES (:id, 'mem-' || :id, 'hash', 'USER', true, now(), now())
                """,
                Map.of("id", ownerId));

        insertMemory(ownerId, "favourite-color", "I love BLUE sky");
        insertMemory(ownerId, "pet-name", "Rex the dog");
        insertMemory(null, "global-fact", "The Sun is blue? no, YELLOW");
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
    void searchForOwnerReturnsEmptyForOtherOwner() {
        List<Memory> found = repository.searchForOwner(UUID.randomUUID().toString(), "blue");
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
                INSERT INTO memories (id, owner_id, key, content, category, created_at, updated_at)
                VALUES (:id, :owner, :key, :content, NULL, now(), now())
                """,
                params);
    }
}
