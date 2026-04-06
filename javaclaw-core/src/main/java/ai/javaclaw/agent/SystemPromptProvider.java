package ai.javaclaw.agent;

import ai.javaclaw.files.VirtualFileRepository;
import ai.javaclaw.tools.AgentEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

/**
 * Assembles the agent system prompt from workspace files stored in {@code virtual_files} table
 * (owner_id = NULL). Reads AGENT.md, SOUL.md, and INFO.md in order; missing files are silently
 * skipped. The environment block from {@link AgentEnvironment} is appended at the end.
 */
@Component
public class SystemPromptProvider {

    private static final Logger log = LoggerFactory.getLogger(SystemPromptProvider.class);

    static final String[] SYSTEM_FILES = {"AGENT.md", "SOUL.md", "INFO.md"};

    private final VirtualFileRepository repository;

    public SystemPromptProvider(final VirtualFileRepository repository) {
        Assert.notNull(repository, "repository must not be null");
        this.repository = repository;
    }

    /**
     * Loads the system prompt. Returns an empty string when no workspace files exist and
     * never throws — failures are logged and silently skipped.
     */
    public String load() {
        final StringBuilder sb = new StringBuilder();

        for (final String fileName : SYSTEM_FILES) {
            final String content = readFile(fileName);
            if (content != null && !content.isBlank()) {
                if (!sb.isEmpty()) {
                    sb.append("\n\n");
                }
                sb.append(content.strip());
            }
        }

        final String envInfo = AgentEnvironment.info().toString();
        if (!envInfo.isBlank()) {
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            sb.append("# Environment\n").append(envInfo);
        }

        return sb.toString();
    }

    /**
     * Loads identity sections: AGENT.md + SOUL.md concatenated.
     *
     * @return combined identity text, empty string if no files found
     */
    public String loadIdentity() {
        final StringBuilder sb = new StringBuilder();
        for (final String fileName : new String[] {"AGENT.md", "SOUL.md"}) {
            final String content = readFile(fileName);
            if (content != null && !content.isBlank()) {
                if (!sb.isEmpty()) {
                    sb.append("\n\n");
                }
                sb.append(content.strip());
            }
        }
        return sb.toString();
    }

    /**
     * Loads context sections: INFO.md only.
     *
     * @return INFO.md content, empty string if file not found
     */
    public String loadContext() {
        final String content = readFile("INFO.md");
        return (content != null && !content.isBlank()) ? content.strip() : "";
    }

    private String readFile(final String path) {
        try {
            return repository
                    .findByOwnerIdIsNullAndPath(path)
                    .map(f -> f.content())
                    .orElse(null);
        } catch (final Exception e) {
            log.warn("SystemPromptProvider: could not read '{}' — {}", path, e.getMessage());
            return null;
        }
    }
}
