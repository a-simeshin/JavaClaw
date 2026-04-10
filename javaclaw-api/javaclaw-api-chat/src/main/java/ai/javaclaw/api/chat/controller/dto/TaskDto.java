package ai.javaclaw.api.chat.controller.dto;

import ai.javaclaw.tasks.Task;
import java.time.Instant;

public record TaskDto(
        String id,
        String name,
        String description,
        String status,
        String feedback,
        String conversationId,
        String parentTaskId,
        String notifyPolicy,
        String runtimeType,
        Integer timeoutSeconds,
        Boolean carryOverContext,
        String userId,
        Instant createdAt,
        Instant updatedAt,
        Instant failedAt,
        Instant cancelledAt) {

    public static TaskDto from(Task task) {
        return new TaskDto(
                task.getId(),
                task.getName(),
                task.getDescription(),
                task.getStatus() != null ? task.getStatus().name() : null,
                task.getFeedback(),
                task.getConversationId(),
                task.getParentTaskId(),
                task.getNotifyPolicy() != null ? task.getNotifyPolicy().name() : null,
                task.getRuntimeType() != null ? task.getRuntimeType().name() : null,
                task.getTimeoutSeconds(),
                task.getCarryOverContext(),
                task.getUserId(),
                task.getCreatedAt(),
                task.getUpdatedAt(),
                task.getFailedAt(),
                task.getCancelledAt());
    }
}
