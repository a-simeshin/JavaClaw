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
    private final Instant createdAt;

    public RecurringTask(
            String id, String name, String description, String cronExpression, String jobId, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.cronExpression = cronExpression;
        this.jobId = jobId;
        this.createdAt = createdAt;
    }

    public static RecurringTask newRecurringTask(String name, String description, String cronExpression) {
        return new RecurringTask(null, name, description, cronExpression, null, Instant.now());
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public RecurringTask withJobId(String jobId) {
        return new RecurringTask(id, name, description, cronExpression, jobId, createdAt);
    }

    @Override
    public String toString() {
        return "Recurring Task '" + name + "'";
    }
}
