package ai.javaclaw.tasks;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJdbcTest
@Testcontainers
@ActiveProfiles("test")
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import({TaskIdGeneratorCallback.class})
class TaskRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    TaskRepository taskRepository;

    @Test
    void saveAndFindByIdRoundtrip() {
        Task task = Task.newTask("roundtrip-test", "Test save and retrieve");

        Task saved = taskRepository.save(task);

        assertThat(saved.getId()).isNotNull();
        Optional<Task> found = taskRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("roundtrip-test");
        assertThat(found.get().getDescription()).isEqualTo("Test save and retrieve");
        assertThat(found.get().getStatus()).isEqualTo(Task.Status.todo);
    }

    @Test
    void findByCreatedAtBetweenAndStatusReturnsFilteredResults() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant yesterday = now.minus(1, ChronoUnit.DAYS);
        Instant tomorrow = now.plus(1, ChronoUnit.DAYS);

        Task todoTask = taskRepository.save(Task.newTask("todo-task", "A todo task"));
        Task completedTask = taskRepository.save(
                Task.newTask("completed-task", "A completed task").withStatus(Task.Status.completed));

        List<Task> todos = taskRepository.findByCreatedAtBetweenAndStatus(yesterday, tomorrow, Task.Status.todo);

        assertThat(todos).extracting(Task::getName).contains("todo-task");
        assertThat(todos).extracting(Task::getName).doesNotContain("completed-task");
    }

    @Test
    void findByCreatedAtBetweenReturnsAllStatusesInRange() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant yesterday = now.minus(1, ChronoUnit.DAYS);
        Instant tomorrow = now.plus(1, ChronoUnit.DAYS);

        taskRepository.save(Task.newTask("task-a", "Task A"));
        taskRepository.save(Task.newTask("task-b", "Task B").withStatus(Task.Status.in_progress));

        List<Task> all = taskRepository.findByCreatedAtBetween(yesterday, tomorrow);

        assertThat(all).extracting(Task::getName).contains("task-a", "task-b");
    }

    @Test
    void savePreservesSourceChannelName() {
        Task task = Task.newTask("channel-test", "Test channel").withSourceChannelName("telegram");

        Task saved = taskRepository.save(task);
        Optional<Task> found = taskRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getSourceChannelName()).isEqualTo("telegram");
    }
}
