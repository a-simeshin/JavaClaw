package ai.javaclaw.api.chat.a2a;

import java.util.List;

/**
 * A2A Agent Card — describes agent capabilities for discovery.
 * Served at {@code /.well-known/agent.json}.
 *
 * @see <a href="https://google.github.io/A2A/">Google A2A Protocol</a>
 */
public record A2aAgentCard(
        String name,
        String description,
        String url,
        String version,
        A2aCapabilities capabilities,
        List<A2aSkill> skills) {

    public record A2aCapabilities(boolean streaming, boolean pushNotifications, boolean stateTransitionHistory) {}

    public record A2aSkill(String id, String name, String description, List<String> tags) {}
}
