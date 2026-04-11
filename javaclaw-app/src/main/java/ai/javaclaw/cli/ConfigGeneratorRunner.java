package ai.javaclaw.cli;

import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Spring Boot ApplicationRunner that generates deployment config files
 * when --generate-config argument is passed.
 *
 * Usage: java -jar javaclaw.jar --generate-config [--config-dir=/path/to/dir] [--force]
 */
@Component
public class ConfigGeneratorRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ConfigGeneratorRunner.class);

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption("generate-config") && !args.getNonOptionArgs().contains("--generate-config")) {
            return;
        }

        Path outputDir = Path.of(".");
        if (args.containsOption("config-dir")) {
            List<String> values = args.getOptionValues("config-dir");
            if (values != null && !values.isEmpty()) {
                outputDir = Path.of(values.getFirst());
            }
        }

        boolean force = args.containsOption("force");

        log.info("Generating configuration files in: {}", outputDir.toAbsolutePath());
        var generator = new ConfigGenerator(outputDir, force);
        List<String> generated = generator.generateAll();

        if (generated.isEmpty()) {
            log.warn("No files generated (all already exist). Use --force to overwrite.");
        } else {
            log.info("Generated {} file(s): {}", generated.size(), String.join(", ", generated));
        }
    }
}
