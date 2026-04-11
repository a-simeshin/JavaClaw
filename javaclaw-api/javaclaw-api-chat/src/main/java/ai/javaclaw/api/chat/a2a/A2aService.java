package ai.javaclaw.api.chat.a2a;

import ai.javaclaw.api.chat.a2a.A2aJsonRpc.*;
import ai.javaclaw.tasks.Task;
import ai.javaclaw.tasks.TaskRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * A2A protocol service — bridges Google A2A JSON-RPC methods to internal task infrastructure.
 *
 * <p>Supported methods:
 * <ul>
 *   <li>{@code tasks/send} — create and enqueue a task</li>
 *   <li>{@code tasks/get} — retrieve task status and history</li>
 *   <li>{@code tasks/cancel} — cancel a running task</li>
 * </ul>
 */
@Service
public class A2aService {

    private static final Logger log = LoggerFactory.getLogger(A2aService.class);

    private final TaskRepository taskRepository;

    public A2aService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public JsonRpcResponse dispatch(JsonRpcRequest request, @Nullable String userId) {
        if (request == null || request.jsonrpc() == null || request.method() == null) {
            return JsonRpcResponse.error(null, A2aJsonRpc.INVALID_REQUEST, "Invalid JSON-RPC request");
        }
        if (!"2.0".equals(request.jsonrpc())) {
            return JsonRpcResponse.error(request.id(), A2aJsonRpc.INVALID_REQUEST, "Only JSON-RPC 2.0 is supported");
        }

        return switch (request.method()) {
            case "tasks/send" -> handleTasksSend(request, userId);
            case "tasks/get" -> handleTasksGet(request);
            case "tasks/cancel" -> handleTasksCancel(request);
            default ->
                JsonRpcResponse.error(
                        request.id(), A2aJsonRpc.METHOD_NOT_FOUND, "Method not found: " + request.method());
        };
    }

    private JsonRpcResponse handleTasksSend(JsonRpcRequest request, @Nullable String userId) {
        Map<String, Object> params = request.params();
        if (params == null) {
            return JsonRpcResponse.error(request.id(), A2aJsonRpc.INVALID_PARAMS, "params required");
        }

        Object messageObj = params.get("message");
        if (!(messageObj instanceof Map<?, ?> messageMap)) {
            return JsonRpcResponse.error(request.id(), A2aJsonRpc.INVALID_PARAMS, "params.message required");
        }

        String textContent = extractTextFromMessage(messageMap);
        if (textContent == null || textContent.isBlank()) {
            return JsonRpcResponse.error(
                    request.id(), A2aJsonRpc.INVALID_PARAMS, "params.message must contain at least one text part");
        }

        String taskName = "a2a-task";
        if (params.get("id") instanceof String customId) {
            taskName = "a2a-" + customId;
        }

        Task task = Task.newTask(taskName, textContent).withUserId(userId);
        task = taskRepository.save(task);

        log.info("A2A tasks/send: created task '{}' ({})", task.getName(), task.getId());

        A2aMessage userMessage = new A2aMessage("user", List.of(A2aPart.text(textContent)));
        A2aTask a2aTask = new A2aTask(task.getId(), TaskState.submitted, List.of(userMessage), List.of());

        return JsonRpcResponse.success(request.id(), a2aTask);
    }

    private JsonRpcResponse handleTasksGet(JsonRpcRequest request) {
        Map<String, Object> params = request.params();
        if (params == null || !(params.get("id") instanceof String taskId)) {
            return JsonRpcResponse.error(request.id(), A2aJsonRpc.INVALID_PARAMS, "params.id required");
        }

        Optional<Task> taskOpt = taskRepository.findById(taskId);
        if (taskOpt.isEmpty()) {
            return JsonRpcResponse.error(request.id(), A2aJsonRpc.TASK_NOT_FOUND, "Task not found: " + taskId);
        }

        Task task = taskOpt.get();
        A2aTask a2aTask = mapToA2aTask(task);
        return JsonRpcResponse.success(request.id(), a2aTask);
    }

    private JsonRpcResponse handleTasksCancel(JsonRpcRequest request) {
        Map<String, Object> params = request.params();
        if (params == null || !(params.get("id") instanceof String taskId)) {
            return JsonRpcResponse.error(request.id(), A2aJsonRpc.INVALID_PARAMS, "params.id required");
        }

        Optional<Task> taskOpt = taskRepository.findById(taskId);
        if (taskOpt.isEmpty()) {
            return JsonRpcResponse.error(request.id(), A2aJsonRpc.TASK_NOT_FOUND, "Task not found: " + taskId);
        }

        Task task = taskOpt.get();
        TaskState currentState = mapStatus(task.getStatus());
        if (currentState == TaskState.completed
                || currentState == TaskState.failed
                || currentState == TaskState.canceled) {
            return JsonRpcResponse.error(
                    request.id(), A2aJsonRpc.TASK_NOT_CANCELABLE, "Task in terminal state: " + currentState);
        }

        Task cancelled = taskRepository.save(task.withStatus(Task.Status.cancelled));
        log.info("A2A tasks/cancel: cancelled task '{}'", cancelled.getId());
        return JsonRpcResponse.success(request.id(), mapToA2aTask(cancelled));
    }

    A2aTask mapToA2aTask(Task task) {
        TaskState state = mapStatus(task.getStatus());

        List<A2aMessage> history = new ArrayList<>();
        if (task.getDescription() != null && !task.getDescription().isBlank()) {
            history.add(new A2aMessage("user", List.of(A2aPart.text(task.getDescription()))));
        }

        List<A2aArtifact> artifacts = new ArrayList<>();
        if (task.getFeedback() != null && !task.getFeedback().isBlank()) {
            artifacts.add(new A2aArtifact("result", List.of(A2aPart.text(task.getFeedback()))));
        }

        return new A2aTask(task.getId(), state, history, artifacts);
    }

    TaskState mapStatus(Task.Status status) {
        return switch (status) {
            case todo -> TaskState.submitted;
            case in_progress -> TaskState.working;
            case completed -> TaskState.completed;
            case failed -> TaskState.failed;
            case cancelled -> TaskState.canceled;
            case awaiting_human_input -> TaskState.input_required;
        };
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private String extractTextFromMessage(Map<?, ?> messageMap) {
        Object partsObj = messageMap.get("parts");
        if (!(partsObj instanceof List<?> parts)) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (Object part : parts) {
            if (part instanceof Map<?, ?> partMap && "text".equals(partMap.get("type"))) {
                Object text = partMap.get("text");
                if (text instanceof String s) {
                    if (!sb.isEmpty()) sb.append("\n");
                    sb.append(s);
                }
            }
        }
        return sb.isEmpty() ? null : sb.toString();
    }
}
