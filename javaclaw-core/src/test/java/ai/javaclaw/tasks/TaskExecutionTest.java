package ai.javaclaw.tasks;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TaskExecutionTest {

    @Test
    void start_createsRunningExecution() {
        TaskExecution exec = TaskExecution.start("task-1", 1, "system prompt", "user prompt");

        assertThat(exec.getId()).isNull();
        assertThat(exec.getTaskId()).isEqualTo("task-1");
        assertThat(exec.getExecutionNumber()).isEqualTo(1);
        assertThat(exec.getStatus()).isEqualTo(TaskExecution.Status.running);
        assertThat(exec.getSystemPrompt()).isEqualTo("system prompt");
        assertThat(exec.getUserPrompt()).isEqualTo("user prompt");
        assertThat(exec.getStartedAt()).isNotNull();
        assertThat(exec.getCreatedAt()).isNotNull();
        assertThat(exec.getCompletedAt()).isNull();
        assertThat(exec.getLlmResponse()).isNull();
        assertThat(exec.getDurationMs()).isNull();
    }

    @Test
    void withCompleted_setsStatusAndResponse() {
        TaskExecution exec = TaskExecution.start("task-1", 1, "sys", "usr");

        TaskExecution completed = exec.withCompleted("LLM said hello", "[{\"name\":\"tool1\"}]", "{\"total\":100}");

        assertThat(completed.getStatus()).isEqualTo(TaskExecution.Status.completed);
        assertThat(completed.getLlmResponse()).isEqualTo("LLM said hello");
        assertThat(completed.getToolCalls()).isEqualTo("[{\"name\":\"tool1\"}]");
        assertThat(completed.getTokenUsage()).isEqualTo("{\"total\":100}");
        assertThat(completed.getCompletedAt()).isNotNull();
        assertThat(completed.getDurationMs()).isNotNull();
        assertThat(completed.getDurationMs()).isGreaterThanOrEqualTo(0);
        assertThat(completed.getErrorMessage()).isNull();
    }

    @Test
    void withFailed_setsErrorDetails() {
        TaskExecution exec = TaskExecution.start("task-2", 3, "sys", "usr");

        TaskExecution failed = exec.withFailed("Connection refused", "java.net.ConnectException\n\tat ...");

        assertThat(failed.getStatus()).isEqualTo(TaskExecution.Status.failed);
        assertThat(failed.getErrorMessage()).isEqualTo("Connection refused");
        assertThat(failed.getErrorTrace()).startsWith("java.net.ConnectException");
        assertThat(failed.getCompletedAt()).isNotNull();
        assertThat(failed.getDurationMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void withCancelled_setsStatusAndDuration() {
        TaskExecution exec = TaskExecution.start("task-3", 1, "sys", "usr");

        TaskExecution cancelled = exec.withCancelled();

        assertThat(cancelled.getStatus()).isEqualTo(TaskExecution.Status.cancelled);
        assertThat(cancelled.getCompletedAt()).isNotNull();
        assertThat(cancelled.getDurationMs()).isGreaterThanOrEqualTo(0);
        assertThat(cancelled.getErrorMessage()).isNull();
    }

    @Test
    void withCompleted_preservesOriginalFields() {
        TaskExecution exec = TaskExecution.start("task-1", 5, "system", "user");

        TaskExecution completed = exec.withCompleted("response", null, null);

        assertThat(completed.getTaskId()).isEqualTo("task-1");
        assertThat(completed.getExecutionNumber()).isEqualTo(5);
        assertThat(completed.getSystemPrompt()).isEqualTo("system");
        assertThat(completed.getUserPrompt()).isEqualTo("user");
        assertThat(completed.getStartedAt()).isEqualTo(exec.getStartedAt());
        assertThat(completed.getCreatedAt()).isEqualTo(exec.getCreatedAt());
    }

    @Test
    void toString_includesKeyInfo() {
        TaskExecution exec = TaskExecution.start("task-42", 2, "sys", "usr");
        String str = exec.toString();

        assertThat(str).contains("task-42");
        assertThat(str).contains("2");
        assertThat(str).contains("running");
    }
}
