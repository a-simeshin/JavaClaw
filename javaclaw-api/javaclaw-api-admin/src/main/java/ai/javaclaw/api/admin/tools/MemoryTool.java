package ai.javaclaw.api.admin.tools;

import ai.javaclaw.memory.Memory;
import ai.javaclaw.memory.MemoryService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;

/**
 * Agent tool for long-term memory: store, recall, forget, list, and search facts.
 * Memories are key-value pairs optionally scoped to a user (ownerId) and categorized.
 */
public class MemoryTool {

    private static final Logger logger = LoggerFactory.getLogger(MemoryTool.class);

    private final MemoryService memoryService;

    public MemoryTool(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @Tool(
            description =
                    """
            Stores a fact in long-term memory. If a memory with the same key already exists, it is updated.
            Use this when the user asks you to remember something, or when you learn an important fact
            that should persist across conversations.

            - key: A short, unique identifier for the memory (e.g. 'user-preference-language', 'project-deadline').
            - content: The actual fact or information to remember.
            - category: Optional grouping (e.g. 'preference', 'project', 'personal'). Can be null.

            Returns confirmation with the stored key.
            """)
    public String store(String key, String content, String category) {
        try {
            if (key == null || key.isBlank()) {
                return "Error: key must not be empty.";
            }
            if (content == null || content.isBlank()) {
                return "Error: content must not be empty.";
            }
            Memory saved = memoryService.store(null, key, content, category);
            return String.format("Remembered '%s' (id=%s).", saved.key(), saved.id());
        } catch (Exception e) {
            logger.error("store failed for key={}", key, e);
            return "Error: Could not store memory. " + e.getMessage();
        }
    }

    @Tool(
            description =
                    """
            Recalls a specific memory by its key.
            Use this when the user asks 'do you remember...?' or when you need a previously stored fact.

            - key: The exact key of the memory to recall.

            Returns the stored content, or a not-found message.
            Tries user-scoped memory first, then falls back to global.
            """)
    public String recall(String key) {
        try {
            if (key == null || key.isBlank()) {
                return "Error: key must not be empty.";
            }
            return memoryService
                    .recall(null, key)
                    .map(m -> String.format(
                            "[%s] %s%s",
                            m.key(), m.content(), m.category() != null ? " (category: " + m.category() + ")" : ""))
                    .orElse(String.format("No memory found for key '%s'.", key));
        } catch (Exception e) {
            logger.error("recall failed for key={}", key, e);
            return "Error: Could not recall memory. " + e.getMessage();
        }
    }

    @Tool(
            description =
                    """
            Removes a memory by its key.
            Use this when the user asks to forget something or when a stored fact is no longer relevant.

            - key: The exact key of the memory to remove.

            Returns confirmation, or an error if the memory was not found.
            """)
    public String forget(String key) {
        try {
            if (key == null || key.isBlank()) {
                return "Error: key must not be empty.";
            }
            boolean removed = memoryService.forget(null, key);
            if (removed) {
                return String.format("Forgot '%s'.", key);
            }
            return String.format("No memory found for key '%s'.", key);
        } catch (Exception e) {
            logger.error("forget failed for key={}", key, e);
            return "Error: Could not forget memory. " + e.getMessage();
        }
    }

    @Tool(
            description =
                    """
            Lists all stored memories.
            Use this when the user asks 'what do you remember?' or to review stored facts.

            Returns a formatted list of all memories with key, category, and content preview.
            """)
    public String list() {
        try {
            List<Memory> memories = memoryService.list(null);
            if (memories.isEmpty()) {
                return "No memories stored.";
            }
            StringBuilder sb = new StringBuilder();
            sb.append("Memories (").append(memories.size()).append("):").append(System.lineSeparator());
            for (Memory m : memories) {
                sb.append("- ").append(m.key());
                if (m.category() != null) {
                    sb.append(" [").append(m.category()).append("]");
                }
                sb.append(": ").append(truncate(m.content(), 100)).append(System.lineSeparator());
            }
            return sb.toString();
        } catch (Exception e) {
            logger.error("list failed", e);
            return "Error: Could not list memories. " + e.getMessage();
        }
    }

    @Tool(
            description =
                    """
            Searches memories by keyword. Matches against both key and content (case-insensitive).
            Use this when the user asks about a topic and you want to find relevant stored facts.

            - query: The search term to match against memory keys and content.

            Returns matching memories, or an empty message if nothing found.
            """)
    public String search(String query) {
        try {
            if (query == null || query.isBlank()) {
                return "Error: query must not be empty.";
            }
            List<Memory> results = memoryService.search(null, query);
            if (results.isEmpty()) {
                return String.format("No memories matching '%s'.", query);
            }
            StringBuilder sb = new StringBuilder();
            sb.append("Found ").append(results.size()).append(" memories:").append(System.lineSeparator());
            for (Memory m : results) {
                sb.append("- ").append(m.key());
                if (m.category() != null) {
                    sb.append(" [").append(m.category()).append("]");
                }
                sb.append(": ").append(truncate(m.content(), 100)).append(System.lineSeparator());
            }
            return sb.toString();
        } catch (Exception e) {
            logger.error("search failed for query={}", query, e);
            return "Error: Could not search memories. " + e.getMessage();
        }
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private MemoryService memoryService;

        public Builder memoryService(MemoryService memoryService) {
            this.memoryService = memoryService;
            return this;
        }

        public MemoryTool build() {
            return new MemoryTool(this.memoryService);
        }
    }
}
