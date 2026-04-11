package ai.javaclaw.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigGeneratorTest {

    @TempDir
    Path tempDir;

    ConfigGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new ConfigGenerator(tempDir, false);
    }

    @Nested
    class GenerateAll {

        @Test
        void generatesThreeFiles() {
            List<String> generated = generator.generateAll();
            assertThat(generated).containsExactly("application.yaml", "docker-compose.yml", ".env.example");
            assertThat(tempDir.resolve("application.yaml")).exists();
            assertThat(tempDir.resolve("docker-compose.yml")).exists();
            assertThat(tempDir.resolve(".env.example")).exists();
        }

        @Test
        void doesNotOverwriteExistingWithoutForce() throws IOException {
            Files.writeString(tempDir.resolve("application.yaml"), "existing");

            List<String> generated = generator.generateAll();

            assertThat(generated).containsExactly("docker-compose.yml", ".env.example");
            assertThat(Files.readString(tempDir.resolve("application.yaml"))).isEqualTo("existing");
        }

        @Test
        void overwritesExistingWithForce() throws IOException {
            Files.writeString(tempDir.resolve("application.yaml"), "existing");
            var forceGenerator = new ConfigGenerator(tempDir, true);

            List<String> generated = forceGenerator.generateAll();

            assertThat(generated).contains("application.yaml");
            assertThat(Files.readString(tempDir.resolve("application.yaml")))
                    .contains("JavaClaw Application Configuration");
        }
    }

    @Nested
    class ApplicationYaml {

        @Test
        void containsRequiredSections() {
            String yaml = generator.generateApplicationYaml();

            assertThat(yaml).contains("server:");
            assertThat(yaml).contains("spring:");
            assertThat(yaml).contains("datasource:");
            assertThat(yaml).contains("flyway:");
            assertThat(yaml).contains("javaclaw:");
            assertThat(yaml).contains("management:");
            assertThat(yaml).contains("jobrunr:");
        }

        @Test
        void containsEnvVarPlaceholders() {
            String yaml = generator.generateApplicationYaml();

            assertThat(yaml).contains("${DB_HOST:localhost}");
            assertThat(yaml).contains("${DB_PORT:5432}");
            assertThat(yaml).contains("${DB_NAME:javaclaw}");
            assertThat(yaml).contains("${OPENROUTER_API_KEY:}");
            assertThat(yaml).contains("${ADMIN_PASSWORD:admin}");
        }

        @Test
        void containsSecurityConfig() {
            String yaml = generator.generateApplicationYaml();

            assertThat(yaml).contains("security:");
            assertThat(yaml).contains("username: admin");
            assertThat(yaml).contains("role: ADMIN");
            assertThat(yaml).contains("tool-deny:");
        }

        @Test
        void containsGracefulShutdown() {
            String yaml = generator.generateApplicationYaml();

            assertThat(yaml).contains("shutdown: graceful");
            assertThat(yaml).contains("timeout-per-shutdown-phase: 30s");
        }
    }

    @Nested
    class DockerCompose {

        @Test
        void containsAppAndPostgresServices() {
            String compose = generator.generateDockerCompose();

            assertThat(compose).contains("services:");
            assertThat(compose).contains("app:");
            assertThat(compose).contains("postgres:");
        }

        @Test
        void appDependsOnPostgres() {
            String compose = generator.generateDockerCompose();

            assertThat(compose).contains("depends_on:");
            assertThat(compose).contains("condition: service_healthy");
        }

        @Test
        void containsHealthchecks() {
            String compose = generator.generateDockerCompose();

            assertThat(compose).contains("healthcheck:");
            assertThat(compose).contains("actuator/health/liveness");
            assertThat(compose).contains("pg_isready");
        }

        @Test
        void containsVolumeForPersistence() {
            String compose = generator.generateDockerCompose();

            assertThat(compose).contains("volumes:");
            assertThat(compose).contains("pgdata:");
        }

        @Test
        void containsAllEnvVars() {
            String compose = generator.generateDockerCompose();

            assertThat(compose).contains("OPENROUTER_API_KEY");
            assertThat(compose).contains("DB_PASSWORD");
            assertThat(compose).contains("ADMIN_PASSWORD");
            assertThat(compose).contains("CHAT_MODEL");
        }
    }

    @Nested
    class EnvExample {

        @Test
        void containsSectionHeaders() {
            String env = generator.generateEnvExample();

            assertThat(env).contains("# === Required ===");
            assertThat(env).contains("# === Database ===");
            assertThat(env).contains("# === Server ===");
            assertThat(env).contains("# === Auth ===");
            assertThat(env).contains("# === AI Model ===");
            assertThat(env).contains("# === Fallback ===");
        }

        @Test
        void containsAllConfigurableVars() {
            String env = generator.generateEnvExample();

            assertThat(env).contains("OPENROUTER_API_KEY=");
            assertThat(env).contains("DB_HOST=");
            assertThat(env).contains("DB_PASSWORD=");
            assertThat(env).contains("ADMIN_PASSWORD=");
            assertThat(env).contains("CHAT_MODEL=");
            assertThat(env).contains("THINKING_ENABLED=");
        }
    }

    @Nested
    class EnvironmentVariables {

        @Test
        void returnsAllExpectedVars() {
            Map<String, String> vars = generator.getEnvironmentVariables();

            assertThat(vars)
                    .containsKeys(
                            "PORT",
                            "DB_HOST",
                            "DB_PORT",
                            "DB_NAME",
                            "DB_USERNAME",
                            "DB_PASSWORD",
                            "OPENROUTER_API_KEY",
                            "CHAT_MODEL",
                            "ADMIN_PASSWORD",
                            "THINKING_ENABLED");
        }

        @Test
        void varsAreSortedAlphabetically() {
            Map<String, String> vars = generator.getEnvironmentVariables();
            List<String> keys = List.copyOf(vars.keySet());

            assertThat(keys).isSorted();
        }
    }

    @Nested
    class WriteFile {

        @Test
        void createsParentDirectories() {
            Path nested = tempDir.resolve("sub/dir");
            var nestedGenerator = new ConfigGenerator(nested, false);

            nestedGenerator.generateAll();

            assertThat(nested.resolve("application.yaml")).exists();
        }
    }
}
