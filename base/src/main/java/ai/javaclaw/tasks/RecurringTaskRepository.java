package ai.javaclaw.tasks;

import org.springframework.data.repository.ListCrudRepository;

public interface RecurringTaskRepository extends ListCrudRepository<RecurringTask, String> {
}
