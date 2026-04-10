package ai.javaclaw.api.chat.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import ai.javaclaw.agent.audit.DeliveryAuditLog;
import ai.javaclaw.agent.audit.DeliveryAuditLogRepository;
import ai.javaclaw.agent.audit.TaskAuditLog;
import ai.javaclaw.agent.audit.TaskAuditLogRepository;
import ai.javaclaw.tasks.TaskExecution;
import ai.javaclaw.tasks.TaskExecutionRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

class TaskAuditControllerTest {

    private TaskAuditLogRepository taskAuditLogRepository;
    private DeliveryAuditLogRepository deliveryAuditLogRepository;
    private TaskExecutionRepository taskExecutionRepository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        taskAuditLogRepository = mock(TaskAuditLogRepository.class);
        deliveryAuditLogRepository = mock(DeliveryAuditLogRepository.class);
        taskExecutionRepository = mock(TaskExecutionRepository.class);
        mockMvc = standaloneSetup(new TaskAuditController(
                        taskAuditLogRepository, deliveryAuditLogRepository, taskExecutionRepository))
                .build();
    }

    @Test
    void auditReturnsTaskAuditLogs() throws Exception {
        Instant now = Instant.parse("2026-04-10T10:00:00Z");
        TaskAuditLog log = TaskAuditLog.taskEvent("task-1", "exec-1", TaskAuditLog.EVENT_CREATED);
        when(taskAuditLogRepository.findByTaskIdOrderByCreatedAtAsc("task-1")).thenReturn(List.of(log));

        mockMvc.perform(get("/api/tasks/task-1/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].taskId").value("task-1"))
                .andExpect(jsonPath("$[0].eventType").value("created"));
    }

    @Test
    void auditReturnsEmptyListWhenNoLogs() throws Exception {
        when(taskAuditLogRepository.findByTaskIdOrderByCreatedAtAsc("unknown")).thenReturn(List.of());

        mockMvc.perform(get("/api/tasks/unknown/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void executionsReturnsTaskExecutions() throws Exception {
        TaskExecution exec = TaskExecution.start("task-1", 1, "system prompt", "user prompt");
        when(taskExecutionRepository.findByTaskIdOrderByExecutionNumberDesc("task-1"))
                .thenReturn(List.of(exec));

        mockMvc.perform(get("/api/tasks/task-1/executions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].taskId").value("task-1"))
                .andExpect(jsonPath("$[0].executionNumber").value(1));
    }

    @Test
    void deliveriesReturnsDeliveryAuditLogs() throws Exception {
        DeliveryAuditLog log = DeliveryAuditLog.delivered("task-1", "conv-1", "webchat", "Task done", 1, 50L);
        when(deliveryAuditLogRepository.findByTaskIdOrderByCreatedAtAsc("task-1"))
                .thenReturn(List.of(log));

        mockMvc.perform(get("/api/tasks/task-1/deliveries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].taskId").value("task-1"))
                .andExpect(jsonPath("$[0].status").value("delivered"));
    }
}
