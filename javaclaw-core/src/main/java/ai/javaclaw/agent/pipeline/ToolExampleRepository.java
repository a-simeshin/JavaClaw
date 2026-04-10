package ai.javaclaw.agent.pipeline;

import java.util.List;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

/**
 * Repository for few-shot tool examples used to improve tool-calling accuracy.
 */
public interface ToolExampleRepository extends ListCrudRepository<ToolExample, String> {

    /**
     * Finds all global examples (owner_id IS NULL) ordered by tool name and example order.
     */
    @Query("SELECT * FROM tool_examples WHERE owner_id IS NULL ORDER BY tool_name, example_order")
    List<ToolExample> findAllGlobal();

    /**
     * Finds global examples for a specific tool, ordered by example_order.
     */
    @Query("SELECT * FROM tool_examples WHERE tool_name = :toolName AND owner_id IS NULL ORDER BY example_order")
    List<ToolExample> findGlobalByToolName(@Param("toolName") String toolName);

    /**
     * Finds all examples visible to a user: global + per-user, ordered by tool name and example order.
     */
    @Query("SELECT * FROM tool_examples WHERE owner_id IS NULL OR owner_id = :userId ORDER BY tool_name, example_order")
    List<ToolExample> findAllForUser(@Param("userId") String userId);
}
