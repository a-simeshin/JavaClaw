package ai.javaclaw.e2e.playwright;

import static org.assertj.core.api.Assertions.assertThat;

import ai.javaclaw.e2e.support.IntegrationTestBase;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies the default workspace AGENT.md was sanitized — no literal placeholder
 * strings like {@code <your full name>} or {@code {ENVIRONMENT_INFO}} leak into
 * the system prompt source (bug #27).
 *
 * <p>Asserts content of every AGENT.md under the repo workspace/ directory (and the
 * classpath test workspace) so any future un-sanitized addition is caught at build time.
 */
class AgentMdDefaultE2ETest extends IntegrationTestBase {

    @Test
    @DisplayName("AGENT.md files contain no literal <your full name> or {ENVIRONMENT_INFO} placeholders")
    void freshStartAgentMdHasNoLiteralPlaceholders() throws IOException {
        Path root = Paths.get("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("javaclaw-api"))) {
            root = root.getParent();
        }
        assertThat(root).isNotNull();

        Path workspace = root.resolve("workspace");
        assertThat(Files.exists(workspace))
                .as("workspace/ exists at %s", workspace)
                .isTrue();

        int checked = 0;
        try (Stream<Path> files = Files.walk(workspace)) {
            for (Path p : (Iterable<Path>) files::iterator) {
                if (Files.isRegularFile(p) && p.getFileName().toString().equals("AGENT.md")) {
                    String content = Files.readString(p);
                    assertThat(content)
                            .as("AGENT.md at %s", p)
                            .doesNotContain("<your full name>")
                            .doesNotContain("{ENVIRONMENT_INFO}");
                    checked++;
                }
            }
        }
        assertThat(checked).as("at least one AGENT.md was checked").isGreaterThanOrEqualTo(1);
    }
}
