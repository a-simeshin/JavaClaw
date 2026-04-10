package ai.javaclaw.tasks;

import java.time.Instant;
import java.util.List;
import org.springframework.data.repository.ListCrudRepository;

public interface TaskRepository extends ListCrudRepository<Task, String> {

    List<Task> findByCreatedAtBetweenAndStatus(Instant from, Instant to, Task.Status status);

    List<Task> findByCreatedAtBetween(Instant from, Instant to);

    List<Task> findByStatus(Task.Status status);

    long countByUserIdAndStatus(String userId, Task.Status status);

    long countByUserIdAndCreatedAtAfter(String userId, Instant after);

    List<Task> findByParentTaskId(String parentTaskId);
}
