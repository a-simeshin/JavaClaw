package ai.javaclaw.provider.openai.reasoning;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("javaclaw.reasoning")
public record ReasoningProperties(
        boolean enabled, String effort, boolean exclude, Integer maxTokens, String modelPattern) {
    /**
     * Default regex matches reasoning-capable models only.
     * Excludes Claude Haiku (does not support reasoning) via negative lookahead.
     * Supports: minimax*, deepseek*, o1/o3/o4*, gpt-5*, claude-3.7+/opus-4+/sonnet-4+.
     */
    private static final String DEFAULT_MODEL_PATTERN =
            "^(?!.*haiku).*(minimax|deepseek|o1|o3|o4|gpt-5|claude-(3\\.7|opus-4|sonnet-4|opus-5|sonnet-5)).*";

    public ReasoningProperties {
        if (effort == null) effort = "medium";
        if (modelPattern == null) modelPattern = DEFAULT_MODEL_PATTERN;
    }

    public static ReasoningProperties defaults() {
        return new ReasoningProperties(true, "medium", false, null, DEFAULT_MODEL_PATTERN);
    }
}
