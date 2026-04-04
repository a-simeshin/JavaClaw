package ai.javaclaw.tasks;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TaskTest {

    @Test
    void newTaskCreatesWithCorrectDefaults() {
        Task task = Task.newTask("send-email", "Send weekly email digest");

        assertThat(task.getId()).isNull();
        assertThat(task.getName()).isEqualTo("send-email");
        assertThat(task.getDescription()).isEqualTo("Send weekly email digest");
        assertThat(task.getStatus()).isEqualTo(Task.Status.todo);
        assertThat(task.getCreatedAt()).isNotNull();
        assertThat(task.getUpdatedAt()).isNotNull();
        assertThat(task.getFeedback()).isNull();
        assertThat(task.getSourceChannelName()).isNull();
    }

    @Test
    void withStatusReturnsCopyWithNewStatus() {
        Task original = Task.newTask("task-1", "description");

        Task updated = original.withStatus(Task.Status.in_progress);

        assertThat(updated.getStatus()).isEqualTo(Task.Status.in_progress);
        assertThat(updated.getName()).isEqualTo(original.getName());
        assertThat(updated.getDescription()).isEqualTo(original.getDescription());
        assertThat(original.getStatus()).isEqualTo(Task.Status.todo);
    }

    @Test
    void withFeedbackReturnsCopyWithFeedback() {
        Task original = Task.newTask("task-1", "description");

        Task updated = original.withFeedback("Looks good");

        assertThat(updated.getFeedback()).isEqualTo("Looks good");
        assertThat(updated.getName()).isEqualTo(original.getName());
        assertThat(original.getFeedback()).isNull();
    }

    @Test
    void withSourceChannelNameReturnsCopyWithChannelName() {
        Task original = Task.newTask("task-1", "description");

        Task updated = original.withSourceChannelName("telegram");

        assertThat(updated.getSourceChannelName()).isEqualTo("telegram");
        assertThat(updated.getName()).isEqualTo(original.getName());
        assertThat(original.getSourceChannelName()).isNull();
    }

    @Test
    void toStringContainsName() {
        Task task = Task.newTask("deploy-app", "Deploy the application");

        assertThat(task.toString()).contains("deploy-app");
    }
}
