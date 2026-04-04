package ai.javaclaw.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import ai.javaclaw.tasks.Task;
import ai.javaclaw.tasks.TaskManager;
import ai.javaclaw.tasks.TaskRepository;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Live integration tests for {@link TaskManager} task creation and
 * persistence in a real PostgreSQL database.
 */
class TaskLiveTest extends LiveTestBase {

    @Autowired
    TaskManager taskManager;

    @Autowired
    TaskRepository taskRepository;

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void taskCreatedAndPersistedInDB() {
        String taskName = "live-test-task-" + System.nanoTime();

        taskManager.create(taskName, "Test task description");

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            var tasks = taskRepository.findAll().stream()
                    .filter(t -> t.getName().equals(taskName))
                    .toList();
            assertThat(tasks).hasSize(1);
            assertThat(tasks.getFirst().getDescription()).isEqualTo("Test task description");
            assertThat(tasks.getFirst().getStatus()).isEqualTo(Task.Status.todo);
        });
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void sourceChannelNamePersisted() {
        String taskName = "live-test-channel-" + System.nanoTime();

        taskManager.create(taskName, "Channel test", "test-channel");

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            var tasks = taskRepository.findAll().stream()
                    .filter(t -> t.getName().equals(taskName))
                    .toList();
            assertThat(tasks).hasSize(1);
            assertThat(tasks.getFirst().getSourceChannelName()).isEqualTo("test-channel");
        });
    }
}
