package ai.javaclaw.agent.event;

import java.time.Instant;
import java.util.Map;

public record AgentEvent(EventKind kind, Instant timestamp, EventMeta meta, Map<String, Object> payload) {

    public record EventMeta(String turnId, String sessionKey, String parentTurnId, String taskId) {
        public static EventMeta ofSession(String sessionKey) {
            return new EventMeta(null, sessionKey, null, null);
        }

        public static EventMeta ofTask(String sessionKey, String taskId) {
            return new EventMeta(null, sessionKey, null, taskId);
        }
    }

    public static AgentEvent of(EventKind kind, EventMeta meta, Map<String, Object> payload) {
        return new AgentEvent(kind, Instant.now(), meta, payload);
    }

    public static AgentEvent of(EventKind kind, EventMeta meta) {
        return new AgentEvent(kind, Instant.now(), meta, Map.of());
    }
}
