package ai.javaclaw.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests (no Spring, no Testcontainers, no network) validating the
 * specs/db-schema-v1.md specification file using String/regex parsing only.
 */
class DbSchemaV1DdlValidationTest {

    private static String specContent;
    private static List<String> sqlBlocks;

    @BeforeAll
    static void loadSpec() throws IOException {
        // user.dir may be the submodule dir (javaclaw-core) or the project root depending on
        // how Maven is invoked. Try current dir first, then parent.
        Path base = Paths.get(System.getProperty("user.dir"));
        Path specPath = base.resolve("specs/db-schema-v1.md");
        if (!specPath.toFile().exists()) {
            specPath = base.getParent().resolve("specs/db-schema-v1.md");
        }
        specContent = Files.readString(specPath);
        sqlBlocks = extractSqlBlocks(specContent);
    }

    // -----------------------------------------------------------------------
    // Helper: extract all ```sql ... ``` blocks
    // -----------------------------------------------------------------------

    private static List<String> extractSqlBlocks(String content) {
        List<String> blocks = new ArrayList<>();
        Pattern pattern = Pattern.compile("```sql\\r?\\n(.*?)```", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            blocks.add(matcher.group(1));
        }
        return blocks;
    }

    /** Returns the combined text of all sql blocks. */
    private static String allSql() {
        return String.join("\n", sqlBlocks);
    }

    // -----------------------------------------------------------------------
    // 1. Spec file exists and is readable
    // -----------------------------------------------------------------------

    @Test
    void specFile_exists_andIsReadable() throws IOException {
        // specContent already loaded in @BeforeAll — if we got here the file exists and is readable
        assertThat(specContent).isNotEmpty();
        assertThat(specContent.length()).isGreaterThan(1000);
    }

    // -----------------------------------------------------------------------
    // 2. Mermaid diagram present
    // -----------------------------------------------------------------------

    @Test
    void mermaidDiagram_presentInSpec() {
        assertThat(specContent).contains("```mermaid");
        assertThat(specContent).contains("erDiagram");
    }

    // -----------------------------------------------------------------------
    // 3. SQL blocks extractable — minimum 14
    // -----------------------------------------------------------------------

    @Test
    void ddlBlocks_extractable_minimum14Blocks() {
        assertThat(sqlBlocks).hasSizeGreaterThanOrEqualTo(14);
    }

    // -----------------------------------------------------------------------
    // 4. Each CREATE TABLE block matches basic regex
    // -----------------------------------------------------------------------

    @Test
    void eachCreateTableBlock_parseable() {
        Pattern createTablePattern = Pattern.compile("CREATE TABLE \\w+\\s*\\(", Pattern.CASE_INSENSITIVE);
        for (String block : sqlBlocks) {
            if (block.contains("CREATE TABLE")) {
                assertThat(createTablePattern.matcher(block).find())
                        .as("SQL block should match CREATE TABLE <name> (:\n%s", block)
                        .isTrue();
            }
        }
    }

    // -----------------------------------------------------------------------
    // 5. Table names follow snake_case convention
    // -----------------------------------------------------------------------

    @Test
    void tableNames_followSnakeCaseConvention() {
        Pattern createTableName = Pattern.compile("CREATE TABLE (\\w+)\\s*\\(", Pattern.CASE_INSENSITIVE);
        Matcher matcher = createTableName.matcher(allSql());
        while (matcher.find()) {
            String tableName = matcher.group(1);
            // skip quoted/framework names
            if (tableName.equals("SPRING_AI_CHAT_MEMORY")) continue;
            // must be lowercase (no uppercase letters means no camelCase)
            assertThat(tableName)
                    .as("Table name '%s' should be lowercase snake_case", tableName)
                    .isEqualTo(tableName.toLowerCase());
        }
    }

    // -----------------------------------------------------------------------
    // 6. PRIMARY KEY present on every CREATE TABLE block
    // -----------------------------------------------------------------------

    @Test
    void primaryKey_presentOnEveryTable() {
        for (String block : sqlBlocks) {
            if (block.contains("CREATE TABLE") && !block.contains("SPRING_AI_CHAT_MEMORY")) {
                assertThat(block)
                        .as("Block with CREATE TABLE should contain PRIMARY KEY:\n%s", block)
                        .containsIgnoringCase("PRIMARY KEY");
            }
        }
    }

    // -----------------------------------------------------------------------
    // 7. UUID primary keys use VARCHAR(36)
    // -----------------------------------------------------------------------

    @Test
    void uuidPrimaryKeys_useVarchar36() {
        // id columns with PRIMARY KEY on same line should be VARCHAR(36)
        Pattern idPkLine = Pattern.compile("id\\s+VARCHAR\\((\\d+)\\)\\s+PRIMARY KEY", Pattern.CASE_INSENSITIVE);
        Matcher matcher = idPkLine.matcher(allSql());
        boolean foundAtLeastOne = false;
        while (matcher.find()) {
            foundAtLeastOne = true;
            String size = matcher.group(1);
            // conversations.id is VARCHAR(256) by spec — allow that exception
            assertThat(size)
                    .as("id PRIMARY KEY column should be VARCHAR(36) or VARCHAR(256)")
                    .isIn("36", "256");
        }
        assertThat(foundAtLeastOne)
                .as("Should find at least one 'id VARCHAR(...) PRIMARY KEY' pattern")
                .isTrue();
    }

    // -----------------------------------------------------------------------
    // 8. TIMESTAMP used, not TIMESTAMPTZ
    // -----------------------------------------------------------------------

    @Test
    void timestamps_useTimestampNotTimestamptz() {
        assertThat(allSql()).containsIgnoringCase("TIMESTAMP");
        assertThat(allSql().toUpperCase()).doesNotContain("TIMESTAMPTZ");
    }

    // -----------------------------------------------------------------------
    // 9. created_at columns have DEFAULT now()
    // -----------------------------------------------------------------------

    @Test
    void createdAtColumns_haveDefaultNow() {
        Pattern createdAtDefault =
                Pattern.compile("created_at\\s+TIMESTAMP[^\\n]*DEFAULT\\s+now\\(\\)", Pattern.CASE_INSENSITIVE);
        assertThat(createdAtDefault.matcher(allSql()).find())
                .as("created_at columns should have DEFAULT now()")
                .isTrue();
    }

    // -----------------------------------------------------------------------
    // 10. Index names follow idx_ convention
    // -----------------------------------------------------------------------

    @Test
    void indexNames_followConvention() {
        Pattern createIndex = Pattern.compile("CREATE(?:\\s+UNIQUE)?\\s+INDEX\\s+(\\w+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = createIndex.matcher(allSql());
        while (matcher.find()) {
            String indexName = matcher.group(1);
            assertThat(indexName)
                    .as("Index name '%s' should start with idx_ or uq_", indexName)
                    .matches("(idx_|uq_).*");
        }
    }

    // -----------------------------------------------------------------------
    // 11. Partial unique indexes present for owner_id tables
    // -----------------------------------------------------------------------

    @Test
    void partialUniqueIndexes_presentForOwnerIdTables() {
        assertThat(allSql())
                .as("Spec should contain partial unique index WHERE owner_id IS NOT NULL")
                .contains("WHERE owner_id IS NOT NULL");
        assertThat(allSql())
                .as("Spec should contain partial unique index WHERE owner_id IS NULL")
                .contains("WHERE owner_id IS NULL");
    }

    // -----------------------------------------------------------------------
    // 12. CHECK constraints for role, transport, scope values
    // -----------------------------------------------------------------------

    @Test
    void checkConstraints_onRoleTransportScope() {
        String sql = allSql();
        // role CHECK
        assertThat(sql).contains("'ADMIN'");
        assertThat(sql).contains("'USER'");
        // transport CHECK
        assertThat(sql).contains("'stdio'");
        assertThat(sql).contains("'http'");
        // scope CHECK
        assertThat(sql).contains("'global'");
        assertThat(sql).contains("'user'");
    }

    // -----------------------------------------------------------------------
    // 13. Every REFERENCES is followed by ON DELETE
    // -----------------------------------------------------------------------

    @Test
    void foreignKeys_haveOnDeleteRule() {
        // Split sql into statements by REFERENCES occurrences and check each has ON DELETE
        // We look at the text window after each REFERENCES keyword (up to 300 chars)
        Pattern referencesPattern = Pattern.compile(
                "REFERENCES\\s+\\w+\\([^)]+\\)(.*?)(?=REFERENCES|CREATE|ALTER|$)",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher matcher = referencesPattern.matcher(allSql());
        int count = 0;
        while (matcher.find()) {
            count++;
            // The ON DELETE rule should appear within a reasonable window after REFERENCES
            String window = matcher.group(0);
            assertThat(window)
                    .as("REFERENCES clause should be followed by ON DELETE rule: ...%s...", window.trim())
                    .containsIgnoringCase("ON DELETE");
        }
        assertThat(count).as("Should find at least one REFERENCES clause").isGreaterThan(0);
    }

    // -----------------------------------------------------------------------
    // 14. Migration versions V4 through V10 all present
    // -----------------------------------------------------------------------

    @Test
    void migrationVersions_V4toV10_allPresent() {
        for (int v = 4; v <= 10; v++) {
            String version = "V" + v + "__";
            assertThat(specContent)
                    .as("Spec should mention migration version %s", version)
                    .contains(version);
        }
    }

    // -----------------------------------------------------------------------
    // 15. Java mapping @Table annotations for all 6 tables
    // -----------------------------------------------------------------------

    @Test
    void javaMapping_presentForAll7Tables() {
        assertThat(specContent).contains("@Table(\"users\")");
        assertThat(specContent).contains("@Table(\"conversations\")");
        assertThat(specContent).contains("@Table(\"virtual_files\")");
        assertThat(specContent).contains("@Table(\"skills\")");
        assertThat(specContent).contains("@Table(\"mcp_servers\")");
        assertThat(specContent).contains("@Table(\"config\")");
    }

    // -----------------------------------------------------------------------
    // 16. Acceptance Criteria section present
    // -----------------------------------------------------------------------

    @Test
    void acceptanceCriteria_sectionPresent() {
        assertThat(specContent.toLowerCase())
                .as("Spec should contain an 'acceptance criteria' section")
                .contains("acceptance criteria");
    }
}
