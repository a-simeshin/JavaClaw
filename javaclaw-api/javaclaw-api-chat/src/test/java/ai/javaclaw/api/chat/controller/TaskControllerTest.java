package ai.javaclaw.api.chat.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import ai.javaclaw.api.chat.error.ChatApiExceptionHandler;
import ai.javaclaw.tasks.NotifyPolicy;
import ai.javaclaw.tasks.Task;
import ai.javaclaw.tasks.TaskManager;
import ai.javaclaw.tasks.TaskRepository;
import ai.javaclaw.tasks.TaskRuntime;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

class TaskControllerTest {

    private TaskRepository taskRepository;
    private TaskManager taskManager;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        taskRepository = mock(TaskRepository.class);
        taskManager = mock(TaskManager.class);
        mockMvc = standaloneSetup(new TaskController(taskRepository, taskManager))
                .setControllerAdvice(new ChatApiExceptionHandler())
                .build();
    }

    @Test
    void listReturnsAllTasks() throws Exception {
        Task task = testTask("t1", "Test task", Task.Status.todo);
        when(taskRepository.findAll()).thenReturn(List.of(task));

        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("t1"))
                .andExpect(jsonPath("$[0].name").value("Test task"))
                .andExpect(jsonPath("$[0].status").value("todo"));
    }

    @Test
    void listFiltersByUserId() throws Exception {
        Task task = testTask("t2", "User task", Task.Status.in_progress);
        when(taskRepository.findByUserId("user-1")).thenReturn(List.of(task));

        mockMvc.perform(get("/api/tasks?userId=user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("t2"));
    }

    @Test
    void listFiltersByStatus() throws Exception {
        Task task = testTask("t3", "Completed task", Task.Status.completed);
        when(taskRepository.findByStatus(Task.Status.completed)).thenReturn(List.of(task));

        mockMvc.perform(get("/api/tasks?status=completed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("t3"))
                .andExpect(jsonPath("$[0].status").value("completed"));
    }

    @Test
    void listFiltersByUserIdAndStatus() throws Exception {
        Task task = testTask("t4", "Filtered task", Task.Status.failed);
        when(taskRepository.findByUserIdAndStatus("user-1", Task.Status.failed)).thenReturn(List.of(task));

        mockMvc.perform(get("/api/tasks?userId=user-1&status=failed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("t4"));
    }

    @Test
    void listWithInvalidStatusReturns400() throws Exception {
        mockMvc.perform(get("/api/tasks?status=invalid")).andExpect(status().isBadRequest());
    }

    @Test
    void getReturnsTaskById() throws Exception {
        Task task = testTask("t5", "Detail task", Task.Status.todo);
        when(taskRepository.findById("t5")).thenReturn(Optional.of(task));

        mockMvc.perform(get("/api/tasks/t5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("t5"))
                .andExpect(jsonPath("$.name").value("Detail task"))
                .andExpect(jsonPath("$.description").value("desc"))
                .andExpect(jsonPath("$.userId").value("user-1"))
                .andExpect(jsonPath("$.notifyPolicy").value("done_only"))
                .andExpect(jsonPath("$.runtimeType").value("async"));
    }

    @Test
    void getReturns404WhenNotFound() throws Exception {
        when(taskRepository.findById("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/tasks/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));
    }

    @Test
    void cancelCallsTaskManagerAndReturnsUpdatedTask() throws Exception {
        Task cancelled = testTask("t6", "Cancel me", Task.Status.cancelled);
        when(taskRepository.findById("t6")).thenReturn(Optional.of(cancelled));

        mockMvc.perform(post("/api/tasks/t6/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("t6"))
                .andExpect(jsonPath("$.status").value("cancelled"));

        verify(taskManager).cancel("t6");
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/tasks/t7")).andExpect(status().isNoContent());

        verify(taskRepository).deleteById("t7");
    }

    @Test
    void childrenReturnsChildTasks() throws Exception {
        Task child1 = testTask("c1", "Child 1", Task.Status.completed);
        Task child2 = testTask("c2", "Child 2", Task.Status.in_progress);
        when(taskRepository.findByParentTaskId("parent-1")).thenReturn(List.of(child1, child2));

        mockMvc.perform(get("/api/tasks/parent-1/children"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("c1"))
                .andExpect(jsonPath("$[1].id").value("c2"));
    }

    private static Task testTask(String id, String name, Task.Status status) {
        Instant now = Instant.parse("2026-04-10T10:00:00Z");
        return new Task(
                id,
                name,
                now,
                now,
                status,
                "desc",
                null,
                null,
                "conv-1",
                null,
                NotifyPolicy.done_only,
                TaskRuntime.async,
                300,
                false,
                "user-1",
                status == Task.Status.failed ? now : null,
                status == Task.Status.cancelled ? now : null,
                0);
    }
}
