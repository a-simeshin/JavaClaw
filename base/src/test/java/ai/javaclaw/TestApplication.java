package ai.javaclaw;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal Spring Boot application class for slice tests (e.g. {@code @DataJdbcTest})
 * in the {@code base} module which has no production {@code @SpringBootApplication}.
 */
@SpringBootApplication
class TestApplication {
}
