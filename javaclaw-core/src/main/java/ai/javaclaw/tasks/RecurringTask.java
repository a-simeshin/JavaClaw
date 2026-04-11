package ai.javaclaw.tasks;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("recurring_tasks")
public class RecurringTask {

    @Id
    private final String id;

    private final String name;
    private final String description;
    private final String cronExpression;
    private final String jobId;
    /** Идентификатор беседы, к которой привязана задача (может быть null) */
    private final String conversationId;

    private final boolean active;

    private final Instant createdAt;

    public RecurringTask(
            String id,
            String name,
            String description,
            String cronExpression,
            String jobId,
            String conversationId,
            boolean active,
            Instant createdAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.cronExpression = cronExpression;
        this.jobId = jobId;
        this.conversationId = conversationId;
        this.active = active;
        this.createdAt = createdAt;
    }

    /** Создаёт новую задачу без привязки к беседе (обратная совместимость). */
    public static RecurringTask newRecurringTask(String name, String description, String cronExpression) {
        return new RecurringTask(null, name, description, cronExpression, null, null, true, Instant.now());
    }

    /** Создаёт новую задачу с привязкой к беседе. */
    public static RecurringTask newRecurringTask(
            String name, String description, String cronExpression, String conversationId) {
        return new RecurringTask(null, name, description, cronExpression, null, conversationId, true, Instant.now());
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getCronExpression() {
        return cronExpression;
    }

    public String getJobId() {
        return jobId;
    }

    /** Возвращает идентификатор беседы, к которой привязана задача. */
    public String getConversationId() {
        return conversationId;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Возвращает копию задачи с указанным jobId, сохраняя все остальные поля. */
    public RecurringTask withJobId(String jobId) {
        return new RecurringTask(id, name, description, cronExpression, jobId, conversationId, active, createdAt);
    }

    /** Возвращает копию задачи с указанным conversationId, сохраняя все остальные поля. */
    public RecurringTask withConversationId(String conversationId) {
        return new RecurringTask(id, name, description, cronExpression, jobId, conversationId, active, createdAt);
    }

    /** Возвращает копию задачи с указанным active-флагом. */
    public RecurringTask withActive(boolean active) {
        return new RecurringTask(id, name, description, cronExpression, jobId, conversationId, active, createdAt);
    }

    /** Возвращает копию задачи с обновлённым cron expression. */
    public RecurringTask withCronExpression(String cronExpression) {
        return new RecurringTask(id, name, description, cronExpression, jobId, conversationId, active, createdAt);
    }

    @Override
    public String toString() {
        return "Recurring Task '" + name + "'";
    }
}
