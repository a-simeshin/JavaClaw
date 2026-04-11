package ai.javaclaw.tasks;

import java.util.UUID;
import org.springframework.data.relational.core.mapping.event.BeforeConvertCallback;
import org.springframework.stereotype.Component;

@Component
class RecurringTaskIdGeneratorCallback implements BeforeConvertCallback<RecurringTask> {

    @Override
    public RecurringTask onBeforeConvert(RecurringTask task) {
        if (task.getId() == null) {
            return new RecurringTask(
                    UUID.randomUUID().toString(),
                    task.getName(),
                    task.getDescription(),
                    task.getCronExpression(),
                    task.getJobId(),
                    task.getConversationId(),
                    task.isActive(),
                    task.getCreatedAt());
        }
        return task;
    }
}
