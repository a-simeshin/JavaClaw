package ai.javaclaw.tasks;

import java.time.Instant;
import java.util.List;
import org.springframework.data.repository.ListCrudRepository;

public interface TaskRepository extends ListCrudRepository<Task, String> {

    List<Task> findByCreatedAtBetweenAndStatus(Instant from, Instant to, Task.Status status);

    List<Task> findByCreatedAtBetween(Instant from, Instant to);

    long countByUserIdAndStatus(String userId, Task.Status status);

    long countByUserIdAndCreatedAtAfter(String userId, Instant after);
}
