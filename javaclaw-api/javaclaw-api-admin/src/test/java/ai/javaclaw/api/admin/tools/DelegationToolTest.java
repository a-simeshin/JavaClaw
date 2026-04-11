package ai.javaclaw.api.admin.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import ai.javaclaw.tasks.Task;
import ai.javaclaw.tasks.TaskManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DelegationToolTest {

    private TaskManager taskManager;
    private DelegationTool tool;

    @BeforeEach
    void setUp() {
        taskManager = mock(TaskManager.class);
        tool = DelegationTool.builder().taskManager(taskManager).build();
    }

    // --- delegateTask ---

    @Test
    void delegateTask_success() {
        Task child = Task.newTask("sub-research", "Research competitors").withParentTaskId("parent-1");
        when(taskManager.spawn("parent-1", "sub-research", "Research competitors"))
                .thenReturn(child);

        String result = tool.delegateTask("parent-1", "sub-research", "Research competitors");

        assertThat(result).contains("sub-research").contains("delegated successfully");
        verify(taskManager).spawn("parent-1", "sub-research", "Research competitors");
    }

    @Test
    void delegateTask_blankParentTaskId() {
        String result = tool.delegateTask("", "name", "desc");
        assertThat(result).startsWith("Error: parentTaskId must not be blank");
        verifyNoInteractions(taskManager);
    }

    @Test
    void delegateTask_nullName() {
        String result = tool.delegateTask("parent-1", null, "desc");
        assertThat(result).startsWith("Error: name must not be blank");
        verifyNoInteractions(taskManager);
    }

    @Test
    void delegateTask_blankDescription() {
        String result = tool.delegateTask("parent-1", "name", "  ");
        assertThat(result).startsWith("Error: description must not be blank");
        verifyNoInteractions(taskManager);
    }

    @Test
    void delegateTask_parentNotFound() {
        when(taskManager.spawn(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalArgumentException("Parent task not found: bad-id"));

        String result = tool.delegateTask("bad-id", "name", "desc");
        assertThat(result).contains("Error:").contains("Parent task not found");
    }

    @Test
    void delegateTask_depthExceeded() {
        when(taskManager.spawn(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("Maximum task depth of 3 exceeded"));

        String result = tool.delegateTask("deep-parent", "name", "desc");
        assertThat(result).contains("Error:").contains("Maximum task depth");
    }

    // --- checkDelegatedTask ---

    @Test
    void checkDelegatedTask_success() {
        Task task = Task.newTask("sub-research", "Research competitors")
                .withStatus(Task.Status.completed)
                .withFeedback("Found 5 competitors")
                .withParentTaskId("parent-1");
        when(taskManager.getTask("task-1")).thenReturn(task);

        String result = tool.checkDelegatedTask("task-1");

        assertThat(result).contains("sub-research");
        assertThat(result).contains("completed");
        assertThat(result).contains("Found 5 competitors");
        assertThat(result).contains("parent-1");
    }

    @Test
    void checkDelegatedTask_inProgress() {
        Task task = Task.newTask("running-task", "Still working").withStatus(Task.Status.in_progress);
        when(taskManager.getTask("task-2")).thenReturn(task);

        String result = tool.checkDelegatedTask("task-2");

        assertThat(result).contains("in_progress");
        assertThat(result).doesNotContain("Result:");
    }

    @Test
    void checkDelegatedTask_blankId() {
        String result = tool.checkDelegatedTask("");
        assertThat(result).startsWith("Error: taskId must not be blank");
        verifyNoInteractions(taskManager);
    }

    @Test
    void checkDelegatedTask_notFound() {
        when(taskManager.getTask("missing")).thenThrow(new IllegalArgumentException("Task not found: missing"));

        String result = tool.checkDelegatedTask("missing");
        assertThat(result).contains("Error:").contains("Task not found");
    }

    // --- listDelegatedTasks ---

    @Test
    void listDelegatedTasks_withChildren() {
        Task child1 = Task.newTask("child-1", "First sub")
                .withStatus(Task.Status.completed)
                .withFeedback("Done");
        Task child2 = Task.newTask("child-2", "Second sub").withStatus(Task.Status.in_progress);
        when(taskManager.getChildTasks("parent-1")).thenReturn(List.of(child1, child2));

        String result = tool.listDelegatedTasks("parent-1");

        assertThat(result).contains("2");
        assertThat(result).contains("child-1").contains("completed").contains("Done");
        assertThat(result).contains("child-2").contains("in_progress");
    }

    @Test
    void listDelegatedTasks_empty() {
        when(taskManager.getChildTasks("parent-1")).thenReturn(List.of());

        String result = tool.listDelegatedTasks("parent-1");
        assertThat(result).contains("No delegated sub-tasks found");
    }

    @Test
    void listDelegatedTasks_blankParentId() {
        String result = tool.listDelegatedTasks("  ");
        assertThat(result).startsWith("Error: parentTaskId must not be blank");
        verifyNoInteractions(taskManager);
    }

    // --- cancelDelegatedTask ---

    @Test
    void cancelDelegatedTask_success() {
        String result = tool.cancelDelegatedTask("task-1");

        assertThat(result).contains("cancelled");
        verify(taskManager).cancel("task-1");
    }

    @Test
    void cancelDelegatedTask_blankId() {
        String result = tool.cancelDelegatedTask(null);
        assertThat(result).startsWith("Error: taskId must not be blank");
        verifyNoInteractions(taskManager);
    }

    @Test
    void cancelDelegatedTask_notFound() {
        doThrow(new IllegalArgumentException("Task not found: bad-id"))
                .when(taskManager)
                .cancel("bad-id");

        String result = tool.cancelDelegatedTask("bad-id");
        assertThat(result).contains("Error:").contains("Task not found");
    }

    // --- Builder ---

    @Test
    void builder_nullTaskManager_throws() {
        assertThatThrownBy(() -> DelegationTool.builder().build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TaskManager is required");
    }
}
