package ai.javaclaw.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class DbSchemaV1SpecTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    static Connection conn;

    // -----------------------------------------------------------------------
    // DDL from spec (V1-V3 inline + V4-V10 from spec)
    // -----------------------------------------------------------------------

    private static final String V1_DDL =
            """
            CREATE TABLE tasks (
                id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::varchar,
                name VARCHAR(255) NOT NULL,
                description TEXT,
                status VARCHAR(50) NOT NULL DEFAULT 'new',
                feedback TEXT,
                source_channel_name VARCHAR(50),
                created_at TIMESTAMP NOT NULL DEFAULT now(),
                updated_at TIMESTAMP NOT NULL DEFAULT now()
            );
            CREATE INDEX idx_tasks_status ON tasks(status);
            CREATE INDEX idx_tasks_created_at ON tasks(created_at);
            CREATE INDEX idx_tasks_created_at_status ON tasks(created_at, status);
            CREATE TABLE recurring_tasks (
                id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::varchar,
                name VARCHAR(255) NOT NULL,
                description TEXT,
                cron_expression VARCHAR(100) NOT NULL,
                job_id VARCHAR(255),
                created_at TIMESTAMP NOT NULL DEFAULT now()
            );
            """;

    private static final String V2_DDL =
            """
            CREATE TABLE IF NOT EXISTS "SPRING_AI_CHAT_MEMORY" (
                conversation_id VARCHAR(256) NOT NULL,
                content         TEXT NOT NULL,
                type            VARCHAR(100) NOT NULL,
                "timestamp"     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            );
            CREATE INDEX idx_spring_ai_chat_memory_conversation_id
                ON "SPRING_AI_CHAT_MEMORY" (conversation_id);
            """;

    private static final String V3_DDL =
            """
            ALTER TABLE "SPRING_AI_CHAT_MEMORY" ALTER COLUMN content DROP NOT NULL;
            """;

    private static final String V4_DDL =
            """
            CREATE TABLE users (
                id            VARCHAR(36)  PRIMARY KEY DEFAULT gen_random_uuid()::varchar,
                username      VARCHAR(64)  NOT NULL,
                password_hash VARCHAR(255),
                role          VARCHAR(32)  NOT NULL DEFAULT 'USER'
                                  CHECK (role IN ('ADMIN', 'USER')),
                active        BOOLEAN      NOT NULL DEFAULT true,
                created_at    TIMESTAMP    NOT NULL DEFAULT now(),
                updated_at    TIMESTAMP    NOT NULL DEFAULT now()
            );
            CREATE UNIQUE INDEX uq_users_username ON users(username);
            CREATE INDEX idx_users_role           ON users(role);
            CREATE INDEX idx_users_active         ON users(active);
            """;

    private static final String V5_DDL =
            """
            CREATE TABLE conversations (
                id         VARCHAR(256) PRIMARY KEY,
                user_id    VARCHAR(36)  REFERENCES users(id) ON DELETE SET NULL,
                title      VARCHAR(255),
                created_at TIMESTAMP    NOT NULL DEFAULT now(),
                updated_at TIMESTAMP    NOT NULL DEFAULT now()
            );
            CREATE INDEX idx_conversations_user_id    ON conversations(user_id);
            CREATE INDEX idx_conversations_created_at ON conversations(created_at);
            ALTER TABLE "SPRING_AI_CHAT_MEMORY"
                ADD CONSTRAINT fk_chat_memory_conversation_id
                FOREIGN KEY (conversation_id)
                REFERENCES conversations(id)
                ON DELETE CASCADE
                DEFERRABLE INITIALLY DEFERRED;
            """;

    private static final String V6_DDL =
            """
            CREATE TABLE virtual_files (
                id           VARCHAR(36)  PRIMARY KEY DEFAULT gen_random_uuid()::varchar,
                owner_id     VARCHAR(36)  REFERENCES users(id) ON DELETE CASCADE,
                path         VARCHAR(512) NOT NULL,
                content      TEXT,
                content_type VARCHAR(100) NOT NULL DEFAULT 'text/plain',
                size_bytes   BIGINT       NOT NULL DEFAULT 0,
                created_at   TIMESTAMP    NOT NULL DEFAULT now(),
                updated_at   TIMESTAMP    NOT NULL DEFAULT now()
            );
            CREATE INDEX idx_virtual_files_owner_id ON virtual_files(owner_id);
            CREATE INDEX idx_virtual_files_path     ON virtual_files(path);
            CREATE UNIQUE INDEX uq_virtual_files_user_path
                ON virtual_files(owner_id, path) WHERE owner_id IS NOT NULL;
            CREATE UNIQUE INDEX uq_virtual_files_global_path
                ON virtual_files(path) WHERE owner_id IS NULL;
            """;

    private static final String V7_DDL =
            """
            CREATE TABLE skills (
                id          VARCHAR(36)  PRIMARY KEY DEFAULT gen_random_uuid()::varchar,
                owner_id    VARCHAR(36)  REFERENCES users(id) ON DELETE CASCADE,
                name        VARCHAR(120) NOT NULL,
                description VARCHAR(500),
                content     TEXT,
                enabled     BOOLEAN      NOT NULL DEFAULT false,
                created_at  TIMESTAMP    NOT NULL DEFAULT now(),
                updated_at  TIMESTAMP    NOT NULL DEFAULT now()
            );
            CREATE INDEX idx_skills_owner_id ON skills(owner_id);
            CREATE INDEX idx_skills_enabled  ON skills(enabled);
            CREATE UNIQUE INDEX uq_skills_user_name
                ON skills(owner_id, name) WHERE owner_id IS NOT NULL;
            CREATE UNIQUE INDEX uq_skills_global_name
                ON skills(name) WHERE owner_id IS NULL;
            """;

    private static final String V8_DDL =
            """
            CREATE TABLE mcp_servers (
                id         VARCHAR(36)  PRIMARY KEY DEFAULT gen_random_uuid()::varchar,
                owner_id   VARCHAR(36)  REFERENCES users(id) ON DELETE CASCADE,
                name       VARCHAR(120) NOT NULL,
                transport  VARCHAR(10)  NOT NULL
                               CHECK (transport IN ('stdio', 'http')),
                command    TEXT,
                url        TEXT,
                headers    JSONB,
                enabled    BOOLEAN      NOT NULL DEFAULT false,
                created_at TIMESTAMP    NOT NULL DEFAULT now(),
                updated_at TIMESTAMP    NOT NULL DEFAULT now()
            );
            CREATE INDEX idx_mcp_servers_owner_id ON mcp_servers(owner_id);
            CREATE INDEX idx_mcp_servers_enabled  ON mcp_servers(enabled);
            CREATE UNIQUE INDEX uq_mcp_servers_user_name
                ON mcp_servers(owner_id, name) WHERE owner_id IS NOT NULL;
            CREATE UNIQUE INDEX uq_mcp_servers_global_name
                ON mcp_servers(name) WHERE owner_id IS NULL;
            """;

    private static final String V9_DDL =
            """
            CREATE TABLE config (
                id           VARCHAR(36)  PRIMARY KEY DEFAULT gen_random_uuid()::varchar,
                config_key   VARCHAR(255) NOT NULL,
                scope        VARCHAR(16)  NOT NULL
                                 CHECK (scope IN ('global', 'user')),
                owner_id     VARCHAR(36)  REFERENCES users(id) ON DELETE CASCADE,
                config_value TEXT,
                created_at   TIMESTAMP    NOT NULL DEFAULT now(),
                updated_at   TIMESTAMP    NOT NULL DEFAULT now()
            );
            CREATE INDEX idx_config_config_key ON config(config_key);
            CREATE INDEX idx_config_owner_id   ON config(owner_id);
            CREATE UNIQUE INDEX uq_config_user_scoped
                ON config(config_key, scope, owner_id) WHERE owner_id IS NOT NULL;
            CREATE UNIQUE INDEX uq_config_global_scoped
                ON config(config_key, scope) WHERE owner_id IS NULL;
            """;

    private static final String V10_DDL =
            """
            INSERT INTO users (id, username, password_hash, role, active)
            VALUES
                (gen_random_uuid()::varchar, 'admin', NULL, 'ADMIN', true),
                (gen_random_uuid()::varchar, 'user',  NULL, 'USER',  true)
            ON CONFLICT DO NOTHING;
            """;

    // -----------------------------------------------------------------------
    // Setup
    // -----------------------------------------------------------------------

    @BeforeAll
    static void setup() throws Exception {
        conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        applyMigrations(conn);
    }

    private static void applyMigrations(Connection c) throws SQLException {
        for (String block :
                new String[] {V1_DDL, V2_DDL, V3_DDL, V4_DDL, V5_DDL, V6_DDL, V7_DDL, V8_DDL, V9_DDL, V10_DDL}) {
            // Split on semicolons and execute each statement individually
            for (String stmt : block.split(";")) {
                String trimmed = stmt.strip();
                if (!trimmed.isEmpty()) {
                    try (Statement s = c.createStatement()) {
                        s.execute(trimmed);
                    }
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private int queryInt(String sql) throws SQLException {
        try (Statement s = conn.createStatement();
                ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private void execute(String sql) throws SQLException {
        try (Statement s = conn.createStatement()) {
            s.execute(sql);
        }
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    void applyingAllDdl_creates10Tables() throws Exception {
        int count = queryInt(
                """
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('users','conversations','virtual_files',
                                     'skills','mcp_servers','config',
                                     'tasks','recurring_tasks')
                """);
        assertThat(count).isEqualTo(8);

        // SPRING_AI_CHAT_MEMORY is uppercase — check separately
        int chatMemoryCount = queryInt(
                """
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND upper(table_name) = 'SPRING_AI_CHAT_MEMORY'
                """);
        assertThat(chatMemoryCount).isEqualTo(1);
    }

    @Test
    void usersTable_acceptsValidInserts() throws Exception {
        execute(
                """
                INSERT INTO users (id, username, password_hash, role, active)
                VALUES
                    ('spec-usr-001', 'specadmin', '$2a$12$hash_admin', 'ADMIN', true),
                    ('spec-usr-002', 'specuser',  '$2a$12$hash_user',  'USER',  true)
                """);

        int count = queryInt(
                """
                SELECT count(*) FROM users
                WHERE id IN ('spec-usr-001', 'spec-usr-002')
                """);
        assertThat(count).isEqualTo(2);

        // Cleanup
        execute("DELETE FROM users WHERE id IN ('spec-usr-001','spec-usr-002')");
    }

    @Test
    void usersTable_rejectsDuplicateUsername() throws Exception {
        execute("INSERT INTO users (id, username, role) VALUES ('dup-usr-001', 'dupuser', 'USER')");

        assertThatThrownBy(() ->
                        execute("INSERT INTO users (id, username, role) VALUES ('dup-usr-002', 'dupuser', 'USER')"))
                .isInstanceOf(SQLException.class);

        execute("DELETE FROM users WHERE id = 'dup-usr-001'");
    }

    @Test
    void usersTable_rejectsInvalidRole() {
        assertThatThrownBy(
                        () -> execute(
                                "INSERT INTO users (id, username, role) VALUES ('bad-role-001', 'superadminuser', 'SUPERADMIN')"))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void conversationsTable_fkToUsers_onDeleteSetNull() throws Exception {
        execute("INSERT INTO users (id, username, role) VALUES ('conv-owner-001', 'convowner', 'USER')");
        execute("INSERT INTO conversations (id, user_id, title) VALUES ('conv-test-001', 'conv-owner-001', 'Test')");

        execute("DELETE FROM users WHERE id = 'conv-owner-001'");

        int nullCount = queryInt(
                """
                SELECT count(*) FROM conversations
                WHERE id = 'conv-test-001' AND user_id IS NULL
                """);
        assertThat(nullCount).isEqualTo(1);

        execute("DELETE FROM conversations WHERE id = 'conv-test-001'");
    }

    @Test
    void virtualFilesTable_allowsNullOwnerForGlobal() throws Exception {
        execute(
                """
                INSERT INTO virtual_files (id, owner_id, path, content, content_type, size_bytes)
                VALUES ('vf-global-001', NULL, '/AGENT.md', '# Global', 'text/markdown', 8)
                """);

        int count = queryInt("SELECT count(*) FROM virtual_files WHERE id = 'vf-global-001'");
        assertThat(count).isEqualTo(1);

        execute("DELETE FROM virtual_files WHERE id = 'vf-global-001'");
    }

    @Test
    void virtualFilesTable_rejectsDuplicateGlobalPath() throws Exception {
        execute(
                """
                INSERT INTO virtual_files (id, owner_id, path, content_type, size_bytes)
                VALUES ('vf-dup-001', NULL, '/global-dup.md', 'text/plain', 0)
                """);

        assertThatThrownBy(
                        () -> execute(
                                """
                        INSERT INTO virtual_files (id, owner_id, path, content_type, size_bytes)
                        VALUES ('vf-dup-002', NULL, '/global-dup.md', 'text/plain', 0)
                        """))
                .isInstanceOf(SQLException.class);

        execute("DELETE FROM virtual_files WHERE id IN ('vf-dup-001','vf-dup-002')");
    }

    @Test
    void virtualFilesTable_cascadesOnUserDelete() throws Exception {
        execute("INSERT INTO users (id, username, role) VALUES ('vf-owner-001', 'vfowner', 'USER')");
        execute(
                """
                INSERT INTO virtual_files (id, owner_id, path, content_type, size_bytes)
                VALUES ('vf-cascade-001', 'vf-owner-001', '/myfile.txt', 'text/plain', 0)
                """);

        execute("DELETE FROM users WHERE id = 'vf-owner-001'");

        int count = queryInt("SELECT count(*) FROM virtual_files WHERE id = 'vf-cascade-001'");
        assertThat(count).isEqualTo(0);
    }

    @Test
    void skillsTable_globalAndPersonalCoexist() throws Exception {
        execute("INSERT INTO users (id, username, role) VALUES ('sk-owner-001', 'skillowner', 'USER')");

        execute(
                """
                INSERT INTO skills (id, owner_id, name, enabled)
                VALUES ('sk-global-001', NULL, 'code-reviewer', false)
                """);
        execute(
                """
                INSERT INTO skills (id, owner_id, name, enabled)
                VALUES ('sk-personal-001', 'sk-owner-001', 'code-reviewer', false)
                """);

        int count = queryInt(
                """
                SELECT count(*) FROM skills
                WHERE name = 'code-reviewer'
                  AND id IN ('sk-global-001', 'sk-personal-001')
                """);
        assertThat(count).isEqualTo(2);

        execute("DELETE FROM users WHERE id = 'sk-owner-001'");
        execute("DELETE FROM skills WHERE id = 'sk-global-001'");
    }

    @Test
    void mcpServersTable_rejectsInvalidTransport() {
        assertThatThrownBy(
                        () -> execute(
                                """
                        INSERT INTO mcp_servers (id, name, transport, enabled)
                        VALUES ('mcp-bad-001', 'bad-server', 'ftp', false)
                        """))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void mcpServersTable_acceptsJsonbHeaders() throws Exception {
        execute(
                """
                INSERT INTO mcp_servers (id, owner_id, name, transport, url, headers, enabled)
                VALUES ('mcp-hdr-001', NULL, 'remote-mcp', 'http',
                        'https://mcp.example.com',
                        '{"Authorization":"Bearer token"}', false)
                """);

        String headerValue;
        try (Statement s = conn.createStatement();
                ResultSet rs =
                        s.executeQuery("SELECT headers->>'Authorization' FROM mcp_servers WHERE id = 'mcp-hdr-001'")) {
            rs.next();
            headerValue = rs.getString(1);
        }
        assertThat(headerValue).isEqualTo("Bearer token");

        execute("DELETE FROM mcp_servers WHERE id = 'mcp-hdr-001'");
    }

    @Test
    void configTable_partialUniqueWorks() throws Exception {
        execute("INSERT INTO users (id, username, role) VALUES ('cfg-owner-001', 'cfgowner', 'USER')");

        // Same key: once as global, once per user — both must succeed
        execute(
                """
                INSERT INTO config (id, config_key, scope, owner_id, config_value)
                VALUES ('cfg-global-001', 'ai.model.default', 'global', NULL, 'gpt-4o')
                """);
        execute(
                """
                INSERT INTO config (id, config_key, scope, owner_id, config_value)
                VALUES ('cfg-user-001', 'ai.model.default', 'user', 'cfg-owner-001', 'claude-3-5-sonnet')
                """);

        int count = queryInt(
                """
                SELECT count(*) FROM config
                WHERE config_key = 'ai.model.default'
                  AND id IN ('cfg-global-001','cfg-user-001')
                """);
        assertThat(count).isEqualTo(2);

        execute("DELETE FROM users WHERE id = 'cfg-owner-001'");
        execute("DELETE FROM config WHERE id = 'cfg-global-001'");
    }

    @Test
    void allExpectedIndexesCreated() throws Exception {
        try (Statement s = conn.createStatement();
                ResultSet rs = s.executeQuery(
                        """
                     SELECT indexname FROM pg_indexes
                     WHERE tablename IN ('users','conversations','virtual_files',
                                         'skills','mcp_servers','config')
                     """)) {
            java.util.Set<String> indexes = new java.util.HashSet<>();
            while (rs.next()) {
                indexes.add(rs.getString(1));
            }

            assertThat(indexes)
                    .contains(
                            // users
                            "uq_users_username",
                            "idx_users_role",
                            "idx_users_active",
                            // conversations
                            "idx_conversations_user_id",
                            "idx_conversations_created_at",
                            // virtual_files
                            "idx_virtual_files_owner_id",
                            "idx_virtual_files_path",
                            "uq_virtual_files_user_path",
                            "uq_virtual_files_global_path",
                            // skills
                            "idx_skills_owner_id",
                            "idx_skills_enabled",
                            "uq_skills_user_name",
                            "uq_skills_global_name",
                            // mcp_servers
                            "idx_mcp_servers_owner_id",
                            "idx_mcp_servers_enabled",
                            "uq_mcp_servers_user_name",
                            "uq_mcp_servers_global_name",
                            // config
                            "idx_config_config_key",
                            "idx_config_owner_id",
                            "uq_config_user_scoped",
                            "uq_config_global_scoped");
        }
    }
}
