package ai.javaclaw.tasks;

import java.util.UUID;
import org.springframework.data.relational.core.mapping.event.BeforeConvertCallback;
import org.springframework.stereotype.Component;

@Component
class TaskIdGeneratorCallback implements BeforeConvertCallback<Task> {

    @Override
    public Task onBeforeConvert(Task task) {
        if (task.getId() == null) {
            return new Task(
                    UUID.randomUUID().toString(),
                    task.getName(),
                    task.getCreatedAt(),
                    task.getUpdatedAt(),
                    task.getStatus(),
                    task.getDescription(),
                    task.getFeedback(),
                    task.getSourceChannelName());
        }
        return task;
    }
}
