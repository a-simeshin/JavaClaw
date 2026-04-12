package ai.javaclaw.persistence.api;

import ai.javaclaw.memory.Memory;
import java.util.List;

/**
 * Dialect-sensitive text-search queries against the {@code memories} table.
 *
 * <p>Postgres uses {@code ILIKE}; SQLite uses {@code LIKE ... COLLATE NOCASE}.
 */
public interface MemoryQueryRepository {

    /**
     * Case-insensitive substring search over {@code key} and {@code content} for a given
     * owner. Results are ordered newest-first.
     */
    List<Memory> searchForOwner(String ownerId, String query);

    /** Case-insensitive substring search over the global (ownerless) memories. */
    List<Memory> searchGlobal(String query);
}
