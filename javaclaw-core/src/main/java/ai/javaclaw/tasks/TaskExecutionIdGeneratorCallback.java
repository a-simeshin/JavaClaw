package ai.javaclaw.tasks;

import java.util.UUID;
import org.springframework.data.relational.core.mapping.event.BeforeConvertCallback;
import org.springframework.stereotype.Component;

@Component
class TaskExecutionIdGeneratorCallback implements BeforeConvertCallback<TaskExecution> {

    @Override
    public TaskExecution onBeforeConvert(final TaskExecution execution) {
        if (execution.getId() == null) {
            return new TaskExecution(
                    UUID.randomUUID().toString(),
                    execution.getTaskId(),
                    execution.getExecutionNumber(),
                    execution.getStatus(),
                    execution.getSystemPrompt(),
                    execution.getUserPrompt(),
                    execution.getLlmResponse(),
                    execution.getToolCalls(),
                    execution.getTokenUsage(),
                    execution.getErrorMessage(),
                    execution.getErrorTrace(),
                    execution.getStartedAt(),
                    execution.getCompletedAt(),
                    execution.getDurationMs(),
                    execution.getCreatedAt());
        }
        return execution;
    }
}
