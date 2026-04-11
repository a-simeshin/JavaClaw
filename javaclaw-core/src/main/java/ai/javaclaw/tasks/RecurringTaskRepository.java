package ai.javaclaw.tasks;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;

public interface RecurringTaskRepository extends ListCrudRepository<RecurringTask, String> {

    Optional<RecurringTask> findByName(String name);

    List<RecurringTask> findByActive(boolean active);
}
