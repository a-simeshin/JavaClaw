package ai.javaclaw.api.chat.a2a;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import ai.javaclaw.api.chat.a2a.A2aJsonRpc.*;
import ai.javaclaw.tasks.Task;
import ai.javaclaw.tasks.TaskRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("A2aService unit tests")
class A2aServiceTest {

    @Mock
    TaskRepository taskRepository;

    @InjectMocks
    A2aService a2aService;

    @Test
    @DisplayName("dispatch returns error for null request")
    void nullRequest() {
        JsonRpcResponse resp = a2aService.dispatch(null, null);
        assertThat(resp.error()).isNotNull();
        assertThat(resp.error().code()).isEqualTo(A2aJsonRpc.INVALID_REQUEST);
    }

    @Test
    @DisplayName("dispatch returns error for wrong jsonrpc version")
    void wrongVersion() {
        var req = new JsonRpcRequest("1.0", "tasks/get", "1", Map.of("id", "x"));
        JsonRpcResponse resp = a2aService.dispatch(req, null);
        assertThat(resp.error()).isNotNull();
        assertThat(resp.error().code()).isEqualTo(A2aJsonRpc.INVALID_REQUEST);
    }

    @Test
    @DisplayName("dispatch returns METHOD_NOT_FOUND for unknown method")
    void unknownMethod() {
        var req = new JsonRpcRequest("2.0", "unknown/method", "1", Map.of());
        JsonRpcResponse resp = a2aService.dispatch(req, null);
        assertThat(resp.error()).isNotNull();
        assertThat(resp.error().code()).isEqualTo(A2aJsonRpc.METHOD_NOT_FOUND);
    }

    @Test
    @DisplayName("tasks/send creates task and returns submitted")
    void tasksSendSuccess() {
        Task saved = Task.newTask("a2a-task", "Hello agent").withVersion(0);
        when(taskRepository.save(any(Task.class))).thenReturn(saved);

        var req = new JsonRpcRequest(
                "2.0",
                "tasks/send",
                "r1",
                Map.of(
                        "message",
                        Map.of("role", "user", "parts", List.of(Map.of("type", "text", "text", "Hello agent")))));

        JsonRpcResponse resp = a2aService.dispatch(req, "user-123");
        assertThat(resp.error()).isNull();
        assertThat(resp.result()).isInstanceOf(A2aTask.class);
        A2aTask task = (A2aTask) resp.result();
        assertThat(task.status()).isEqualTo(TaskState.submitted);
        assertThat(task.history()).hasSize(1);
        assertThat(task.history().get(0).role()).isEqualTo("user");
    }

    @Test
    @DisplayName("tasks/send returns error when no message provided")
    void tasksSendNoMessage() {
        var req = new JsonRpcRequest("2.0", "tasks/send", "r2", Map.of());
        JsonRpcResponse resp = a2aService.dispatch(req, null);
        assertThat(resp.error()).isNotNull();
        assertThat(resp.error().code()).isEqualTo(A2aJsonRpc.INVALID_PARAMS);
    }

    @Test
    @DisplayName("tasks/send returns error when message has no text parts")
    void tasksSendEmptyParts() {
        var req = new JsonRpcRequest(
                "2.0", "tasks/send", "r3", Map.of("message", Map.of("role", "user", "parts", List.of())));
        JsonRpcResponse resp = a2aService.dispatch(req, null);
        assertThat(resp.error()).isNotNull();
        assertThat(resp.error().code()).isEqualTo(A2aJsonRpc.INVALID_PARAMS);
    }

    @Test
    @DisplayName("tasks/get returns task when found")
    void tasksGetFound() {
        Task task = Task.newTask("test", "Description");
        when(taskRepository.findById("task-1")).thenReturn(Optional.of(task));

        var req = new JsonRpcRequest("2.0", "tasks/get", "g1", Map.of("id", "task-1"));
        JsonRpcResponse resp = a2aService.dispatch(req, null);
        assertThat(resp.error()).isNull();
        assertThat(resp.result()).isInstanceOf(A2aTask.class);
    }

    @Test
    @DisplayName("tasks/get returns TASK_NOT_FOUND when missing")
    void tasksGetNotFound() {
        when(taskRepository.findById("missing")).thenReturn(Optional.empty());

        var req = new JsonRpcRequest("2.0", "tasks/get", "g2", Map.of("id", "missing"));
        JsonRpcResponse resp = a2aService.dispatch(req, null);
        assertThat(resp.error()).isNotNull();
        assertThat(resp.error().code()).isEqualTo(A2aJsonRpc.TASK_NOT_FOUND);
    }

    @Test
    @DisplayName("tasks/cancel cancels a todo task")
    void tasksCancelSuccess() {
        Task task = Task.newTask("test", "Desc");
        when(taskRepository.findById("t1")).thenReturn(Optional.of(task));
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        var req = new JsonRpcRequest("2.0", "tasks/cancel", "c1", Map.of("id", "t1"));
        JsonRpcResponse resp = a2aService.dispatch(req, null);
        assertThat(resp.error()).isNull();
        A2aTask result = (A2aTask) resp.result();
        assertThat(result.status()).isEqualTo(TaskState.canceled);
    }

    @Test
    @DisplayName("tasks/cancel returns error for completed task")
    void tasksCancelTerminal() {
        Task task = Task.newTask("test", "Desc").withStatus(Task.Status.completed);
        when(taskRepository.findById("t2")).thenReturn(Optional.of(task));

        var req = new JsonRpcRequest("2.0", "tasks/cancel", "c2", Map.of("id", "t2"));
        JsonRpcResponse resp = a2aService.dispatch(req, null);
        assertThat(resp.error()).isNotNull();
        assertThat(resp.error().code()).isEqualTo(A2aJsonRpc.TASK_NOT_CANCELABLE);
    }

    @Test
    @DisplayName("mapStatus maps all Task.Status values to A2A TaskState")
    void mapStatusCoversAll() {
        assertThat(a2aService.mapStatus(Task.Status.todo)).isEqualTo(TaskState.submitted);
        assertThat(a2aService.mapStatus(Task.Status.in_progress)).isEqualTo(TaskState.working);
        assertThat(a2aService.mapStatus(Task.Status.completed)).isEqualTo(TaskState.completed);
        assertThat(a2aService.mapStatus(Task.Status.failed)).isEqualTo(TaskState.failed);
        assertThat(a2aService.mapStatus(Task.Status.cancelled)).isEqualTo(TaskState.canceled);
        assertThat(a2aService.mapStatus(Task.Status.awaiting_human_input)).isEqualTo(TaskState.input_required);
    }
}
