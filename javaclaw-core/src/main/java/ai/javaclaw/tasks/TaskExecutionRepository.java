package ai.javaclaw.tasks;

import java.util.List;
import org.springframework.data.repository.ListCrudRepository;

public interface TaskExecutionRepository extends ListCrudRepository<TaskExecution, String> {

    List<TaskExecution> findByTaskIdOrderByExecutionNumberDesc(String taskId);

    List<TaskExecution> findByTaskIdAndStatus(String taskId, TaskExecution.Status status);
}
