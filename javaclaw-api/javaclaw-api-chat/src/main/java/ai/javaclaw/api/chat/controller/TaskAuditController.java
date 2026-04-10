package ai.javaclaw.api.chat.controller;

import ai.javaclaw.agent.audit.DeliveryAuditLog;
import ai.javaclaw.agent.audit.DeliveryAuditLogRepository;
import ai.javaclaw.agent.audit.TaskAuditLog;
import ai.javaclaw.agent.audit.TaskAuditLogRepository;
import ai.javaclaw.tasks.TaskExecution;
import ai.javaclaw.tasks.TaskExecutionRepository;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
@RequestMapping("/api/tasks/{taskId}")
public class TaskAuditController {

    private final TaskAuditLogRepository taskAuditLogRepository;
    private final DeliveryAuditLogRepository deliveryAuditLogRepository;
    private final TaskExecutionRepository taskExecutionRepository;

    @GetMapping("/audit")
    public List<TaskAuditLog> audit(@PathVariable final String taskId) {
        return taskAuditLogRepository.findByTaskIdOrderByCreatedAtAsc(taskId);
    }

    @GetMapping("/executions")
    public List<TaskExecution> executions(@PathVariable final String taskId) {
        return taskExecutionRepository.findByTaskIdOrderByExecutionNumberDesc(taskId);
    }

    @GetMapping("/deliveries")
    public List<DeliveryAuditLog> deliveries(@PathVariable final String taskId) {
        return deliveryAuditLogRepository.findByTaskIdOrderByCreatedAtAsc(taskId);
    }
}
