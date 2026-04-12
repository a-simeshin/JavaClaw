package ai.javaclaw.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

/**
 * Tests that Docker deployment configuration is correct.
 *
 * <p>Validates that Dockerfile exists with proper multi-stage build,
 * docker-compose.yml defines app+postgres services, and application
 * config supports environment variable overrides for containerized deployment.
 */
class DockerConfigIntegrationTest extends IntegrationTestBase {

    private static final Path PROJECT_ROOT =
            Path.of(System.getProperty("user.dir")).getParent();

    @Autowired
    private Environment environment;

    @Test
    @DisplayName("Dockerfile exists with multi-stage build (frontend, backend, runtime)")
    void dockerfileExistsWithMultiStageBuild() throws IOException {
        Path dockerfile = PROJECT_ROOT.resolve("Dockerfile");
        assertThat(dockerfile).exists();

        String content = Files.readString(dockerfile);
        assertThat(content).contains("FROM node:"); // frontend stage
        assertThat(content).contains("FROM maven:"); // backend stage
        assertThat(content).contains("FROM eclipse-temurin:"); // runtime stage
        assertThat(content).contains("pnpm build"); // frontend build
        assertThat(content).contains("mvn package"); // backend build
        assertThat(content).contains("EXPOSE 8080");
    }

    @Test
    @DisplayName("docker-compose.yml defines app and postgres services with healthchecks")
    void dockerComposeDefinesServices() throws IOException {
        Path compose = PROJECT_ROOT.resolve("docker-compose.yml");
        assertThat(compose).exists();

        String content = Files.readString(compose);
        assertThat(content).contains("app:");
        assertThat(content).contains("postgres:");
        assertThat(content).contains("healthcheck:");
        assertThat(content).contains("DB_HOST: postgres");
        assertThat(content).contains("depends_on:");
    }

    @Test
    @DisplayName(".dockerignore exists and excludes build artifacts")
    void dockerignoreExists() throws IOException {
        Path dockerignore = PROJECT_ROOT.resolve(".dockerignore");
        assertThat(dockerignore).exists();

        String content = Files.readString(dockerignore);
        assertThat(content).contains("**/target/");
        assertThat(content).contains("**/node_modules/");
    }

    @Test
    @DisplayName("DB connection is configured (env vars in prod, testcontainers in test)")
    void dbConfigIsPresent() {
        String url = environment.getProperty("spring.datasource.url");
        assertThat(url).isNotNull();
        if (USE_SQLITE) {
            assertThat(url).containsIgnoringCase("sqlite");
        } else {
            assertThat(url).containsIgnoringCase("postgresql");
        }
    }

    @Test
    @DisplayName("Actuator liveness endpoint is available for Docker healthcheck")
    void actuatorLivenessAvailable() {
        String probesEnabled = environment.getProperty("management.endpoint.health.probes.enabled");
        assertThat(probesEnabled).isEqualTo("true");
    }
}
