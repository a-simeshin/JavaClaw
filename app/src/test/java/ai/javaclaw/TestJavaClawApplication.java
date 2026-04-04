package ai.javaclaw;

import org.springframework.boot.SpringApplication;
import org.testcontainers.utility.TestcontainersConfiguration;

public class TestJavaClawApplication {

    public static void main(String[] args) {
        SpringApplication.from(JavaClawApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
