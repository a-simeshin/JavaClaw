package ai.javaclaw.api.chat.controller;

import ai.javaclaw.api.chat.controller.dto.TaskDto;
import ai.javaclaw.tasks.Task;
import ai.javaclaw.tasks.TaskManager;
import ai.javaclaw.tasks.TaskRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskRepository taskRepository;
    private final TaskManager taskManager;

    @GetMapping
    public List<TaskDto> list(
            @RequestParam(required = false) final String userId, @RequestParam(required = false) final String status) {
        final List<Task> tasks;
        if (userId != null && status != null) {
            tasks = taskRepository.findByUserIdAndStatus(userId, Task.Status.valueOf(status));
        } else if (userId != null) {
            tasks = taskRepository.findByUserId(userId);
        } else if (status != null) {
            tasks = taskRepository.findByStatus(Task.Status.valueOf(status));
        } else {
            tasks = taskRepository.findAll();
        }
        final List<TaskDto> dtos = new ArrayList<>(tasks.size());
        for (Task task : tasks) {
            dtos.add(TaskDto.from(task));
        }
        return dtos;
    }

    @GetMapping("/{id}")
    public TaskDto get(@PathVariable final String id) {
        final Task task =
                taskRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Task not found: " + id));
        return TaskDto.from(task);
    }

    @PostMapping("/{id}/cancel")
    public TaskDto cancel(@PathVariable final String id) {
        taskManager.cancel(id);
        final Task task =
                taskRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Task not found: " + id));
        return TaskDto.from(task);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable final String id) {
        taskRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/children")
    public List<TaskDto> children(@PathVariable final String id) {
        final List<Task> children = taskRepository.findByParentTaskId(id);
        final List<TaskDto> dtos = new ArrayList<>(children.size());
        for (Task child : children) {
            dtos.add(TaskDto.from(child));
        }
        return dtos;
    }
}
