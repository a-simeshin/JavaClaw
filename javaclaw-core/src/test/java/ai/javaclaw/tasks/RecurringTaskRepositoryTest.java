package ai.javaclaw.tasks;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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
@Import({RecurringTaskIdGeneratorCallback.class})
class RecurringTaskRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    RecurringTaskRepository recurringTaskRepository;

    @Test
    void saveAndFindAll() {
        RecurringTask task = RecurringTask.newRecurringTask("daily-check", "Check inbox daily", "0 9 * * *");

        RecurringTask saved = recurringTaskRepository.save(task);

        assertThat(saved.getId()).isNotNull();
        List<RecurringTask> all = recurringTaskRepository.findAll();
        assertThat(all).extracting(RecurringTask::getName).contains("daily-check");
    }

    @Test
    void deleteByIdRemovesTask() {
        RecurringTask saved = recurringTaskRepository.save(
                RecurringTask.newRecurringTask("to-delete", "Will be deleted", "0 0 * * *"));

        recurringTaskRepository.deleteById(saved.getId());

        assertThat(recurringTaskRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    void savePreservesAllFields() {
        RecurringTask task = RecurringTask.newRecurringTask("weekly-report", "Generate report", "0 0 * * MON");

        RecurringTask saved = recurringTaskRepository.save(task);
        RecurringTask found = recurringTaskRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getName()).isEqualTo("weekly-report");
        assertThat(found.getDescription()).isEqualTo("Generate report");
        assertThat(found.getCronExpression()).isEqualTo("0 0 * * MON");
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void savePreservesConversationId() {
        RecurringTask task =
                RecurringTask.newRecurringTask("conv-task", "Task with conversation", "0 12 * * *", "test-conv-123");

        RecurringTask saved = recurringTaskRepository.save(task);
        RecurringTask found = recurringTaskRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getConversationId()).isEqualTo("test-conv-123");
    }

    @Test
    void saveWithNullConversationIdWorks() {
        RecurringTask task = RecurringTask.newRecurringTask("no-conv-task", "Task without conversation", "0 8 * * *");

        RecurringTask saved = recurringTaskRepository.save(task);
        RecurringTask found = recurringTaskRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getConversationId()).isNull();
    }
}
