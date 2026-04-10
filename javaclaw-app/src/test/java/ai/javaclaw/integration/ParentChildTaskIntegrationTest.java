package ai.javaclaw.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import ai.javaclaw.agent.Agent;
import ai.javaclaw.agent.audit.TaskAuditLog;
import ai.javaclaw.agent.audit.TaskAuditLogRepository;
import ai.javaclaw.tasks.Task;
import ai.javaclaw.tasks.TaskExecution;
import ai.javaclaw.tasks.TaskExecutionRepository;
import ai.javaclaw.tasks.TaskHandler;
import ai.javaclaw.tasks.TaskHandler.TaskResult;
import ai.javaclaw.tasks.TaskManager;
import ai.javaclaw.tasks.TaskRepository;
import ai.javaclaw.tasks.TaskRuntime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.convention.TestBean;

/**
 * Integration tests for parent-child task hierarchy with real PostgreSQL.
 *
 * <p>Covers:
 * <ul>
 *   <li>T49: parent task spawns children &rarr; both execute &rarr; results collected &rarr; parent completes</li>
 * </ul>
 */
class ParentChildTaskIntegrationTest extends IntegrationTestBase {

    @TestBean
    Agent agent;

    static Agent agent() {
        return Mockito.mock(Agent.class);
    }

    @Autowired
    TaskRepository taskRepository;

    @Autowired
    TaskExecutionRepository taskExecutionRepository;

    @Autowired
    TaskAuditLogRepository taskAuditLogRepository;

    @Autowired
    TaskManager taskManager;

    @Autowired
    TaskHandler taskHandler;

    @BeforeEach
    void cleanAll() {
        taskAuditLogRepository.deleteAll();
        taskExecutionRepository.deleteAll();
        taskRepository.deleteAll();
    }

    private Task saveTask(String name, String description, String conversationId, String userId) {
        return taskRepository.save(Task.newTask(name, description)
                .withConversationId(conversationId)
                .withUserId(userId));
    }

    // ──────────────────────────────────────────────────────────────────
    // T49: Parent spawns children → both execute → results collected
    // ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("T49 — Parent-child task spawning and execution")
    class ParentChildSpawning {

        @Test
        @DisplayName("child task inherits conversationId and userId from parent")
        void childInheritsParentFields() {
            Task parent = saveTask("parent-task", "Analyze competitors", "conv-t49-1", "user-42");

            Task child = taskManager.spawn(parent.getId(), "child-1", "Analyze competitor A");

            assertThat(child.getParentTaskId()).isEqualTo(parent.getId());
            assertThat(child.getConversationId()).isEqualTo("conv-t49-1");
            assertThat(child.getUserId()).isEqualTo("user-42");
            assertThat(child.getStatus()).isEqualTo(Task.Status.todo);
            assertThat(child.getRuntimeType()).isEqualTo(TaskRuntime.async);
        }

        @Test
        @DisplayName("spawn creates audit entry for child task")
        void spawnCreatesAuditEntry() {
            Task parent = saveTask("parent-audit", "Parent for audit", "conv-t49-2", "user-42");

            Task child = taskManager.spawn(parent.getId(), "child-audit", "Child for audit test");

            List<TaskAuditLog> auditLogs = taskAuditLogRepository.findByTaskIdOrderByCreatedAtAsc(child.getId());
            assertThat(auditLogs).isNotEmpty();
            assertThat(auditLogs.stream().map(TaskAuditLog::eventType).toList()).contains("created");
        }

        @Test
        @DisplayName("parent can retrieve child tasks via getChildTasks")
        void parentRetrievesChildren() {
            Task parent = saveTask("parent-multi", "Analyze 3 competitors", "conv-t49-3", "user-42");

            Task child1 = taskManager.spawn(parent.getId(), "child-a", "Competitor A");
            Task child2 = taskManager.spawn(parent.getId(), "child-b", "Competitor B");

            List<Task> children = taskManager.getChildTasks(parent.getId());
            assertThat(children).hasSize(2);
            assertThat(children.stream().map(Task::getId).toList())
                    .containsExactlyInAnyOrder(child1.getId(), child2.getId());
        }

        @Test
        @DisplayName("spawned children execute independently and produce task_executions")
        void childrenExecuteAndProduceExecutions() {
            Task parent = saveTask("parent-exec", "Analyze competitors", "conv-t49-4", "user-42");

            Task child1 = taskManager.spawn(parent.getId(), "child-exec-1", "Analyze A");
            Task child2 = taskManager.spawn(parent.getId(), "child-exec-2", "Analyze B");

            when(agent.prompt(anyString(), anyString(), eq(TaskResult.class)))
                    .thenReturn(new TaskResult(Task.Status.completed, "Result for A"))
                    .thenReturn(new TaskResult(Task.Status.completed, "Result for B"));

            taskHandler.executeTask(child1.getId());
            taskHandler.executeTask(child2.getId());

            // Both children should be completed
            Task updatedChild1 = taskRepository.findById(child1.getId()).orElseThrow();
            Task updatedChild2 = taskRepository.findById(child2.getId()).orElseThrow();
            assertThat(updatedChild1.getStatus()).isEqualTo(Task.Status.completed);
            assertThat(updatedChild2.getStatus()).isEqualTo(Task.Status.completed);
            assertThat(updatedChild1.getFeedback()).isEqualTo("Result for A");
            assertThat(updatedChild2.getFeedback()).isEqualTo("Result for B");

            // Each child has its own task_execution
            List<TaskExecution> exec1 = taskExecutionRepository.findByTaskIdOrderByExecutionNumberDesc(child1.getId());
            List<TaskExecution> exec2 = taskExecutionRepository.findByTaskIdOrderByExecutionNumberDesc(child2.getId());
            assertThat(exec1).hasSize(1);
            assertThat(exec2).hasSize(1);
            assertThat(exec1.getFirst().getStatus()).isEqualTo(TaskExecution.Status.completed);
            assertThat(exec2.getFirst().getStatus()).isEqualTo(TaskExecution.Status.completed);
        }

        @Test
        @DisplayName("parent executes after children and can access child results via task feedback")
        void parentExecutesAfterChildrenComplete() {
            Task parent = saveTask("parent-after", "Summarize competitor analysis", "conv-t49-5", "user-42");

            Task child1 = taskManager.spawn(parent.getId(), "child-res-1", "Analyze A");
            Task child2 = taskManager.spawn(parent.getId(), "child-res-2", "Analyze B");

            // Execute children first
            when(agent.prompt(anyString(), anyString(), eq(TaskResult.class)))
                    .thenReturn(new TaskResult(Task.Status.completed, "A is strong in pricing"))
                    .thenReturn(new TaskResult(Task.Status.completed, "B is strong in UX"));

            taskHandler.executeTask(child1.getId());
            taskHandler.executeTask(child2.getId());

            // Verify child results are stored and retrievable
            List<Task> children = taskManager.getChildTasks(parent.getId());
            assertThat(children).hasSize(2);
            List<String> feedbacks = children.stream().map(Task::getFeedback).toList();
            assertThat(feedbacks).containsExactlyInAnyOrder("A is strong in pricing", "B is strong in UX");

            // Now execute parent
            Mockito.reset(agent);
            when(agent.prompt(anyString(), anyString(), eq(TaskResult.class)))
                    .thenReturn(new TaskResult(Task.Status.completed, "Summary: A=pricing, B=UX"));

            taskHandler.executeTask(parent.getId());

            Task updatedParent = taskRepository.findById(parent.getId()).orElseThrow();
            assertThat(updatedParent.getStatus()).isEqualTo(Task.Status.completed);
            assertThat(updatedParent.getFeedback()).isEqualTo("Summary: A=pricing, B=UX");
        }
    }

    @Nested
    @DisplayName("T49 — Depth limit enforcement")
    class DepthLimitEnforcement {

        @Test
        @DisplayName("depth limit exceeded throws IllegalStateException")
        void depthLimitExceeded() {
            // MAX_TASK_DEPTH = 3: calculateDepth checks parent's depth
            // root(depth 0) → level1(depth 1) → level2(depth 2) → level3(depth 3)
            // spawning under level3 where depth=3 >= MAX_TASK_DEPTH → throws
            Task root = saveTask("root", "Root task", "conv-depth-1", "user-42");
            Task level1 = taskManager.spawn(root.getId(), "level-1", "Child");
            Task level2 = taskManager.spawn(level1.getId(), "level-2", "Grandchild");
            Task level3 = taskManager.spawn(level2.getId(), "level-3", "Great-grandchild");

            assertThatThrownBy(() -> taskManager.spawn(level3.getId(), "level-4", "Too deep"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Maximum task depth");
        }

        @Test
        @DisplayName("spawning at max allowed depth succeeds")
        void spawningAtMaxDepthSucceeds() {
            Task root = saveTask("root-ok", "Root", "conv-depth-2", "user-42");
            Task level1 = taskManager.spawn(root.getId(), "level1-ok", "Child");

            // level1 is at depth 1 — spawning under it makes depth 2 which is < MAX_TASK_DEPTH(3)
            Task level2 = taskManager.spawn(level1.getId(), "level2-ok", "Grandchild");
            assertThat(level2.getParentTaskId()).isEqualTo(level1.getId());
        }

        @Test
        @DisplayName("spawning with non-existent parent throws IllegalArgumentException")
        void nonExistentParentThrows() {
            assertThatThrownBy(() -> taskManager.spawn("non-existent-id", "orphan", "No parent"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Parent task not found");
        }
    }

    @Nested
    @DisplayName("T49 — Child task failure isolation")
    class ChildFailureIsolation {

        @Test
        @DisplayName("child failure does not affect sibling execution")
        void childFailureDoesNotAffectSibling() {
            Task parent = saveTask("parent-iso", "Parent for isolation", "conv-iso-1", "user-42");

            Task child1 = taskManager.spawn(parent.getId(), "failing-child", "Will fail");
            Task child2 = taskManager.spawn(parent.getId(), "ok-child", "Will succeed");

            // First child fails, second succeeds
            when(agent.prompt(anyString(), anyString(), eq(TaskResult.class)))
                    .thenThrow(new RuntimeException("LLM error for child 1"))
                    .thenReturn(new TaskResult(Task.Status.completed, "Child 2 ok"));

            try {
                taskHandler.executeTask(child1.getId());
            } catch (Exception ignored) {
            }
            taskHandler.executeTask(child2.getId());

            Task updatedChild1 = taskRepository.findById(child1.getId()).orElseThrow();
            Task updatedChild2 = taskRepository.findById(child2.getId()).orElseThrow();

            assertThat(updatedChild1.getStatus()).isEqualTo(Task.Status.failed);
            assertThat(updatedChild2.getStatus()).isEqualTo(Task.Status.completed);
            assertThat(updatedChild2.getFeedback()).isEqualTo("Child 2 ok");
        }

        @Test
        @DisplayName("child task execution does not pollute parent task_executions")
        void childExecutionDoesNotPolluteParent() {
            Task parent = saveTask("parent-clean", "Parent stays clean", "conv-iso-2", "user-42");
            Task child = taskManager.spawn(parent.getId(), "child-clean", "Child runs");

            when(agent.prompt(anyString(), anyString(), eq(TaskResult.class)))
                    .thenReturn(new TaskResult(Task.Status.completed, "Child done"));

            taskHandler.executeTask(child.getId());

            // Parent should have NO task_executions
            List<TaskExecution> parentExecs =
                    taskExecutionRepository.findByTaskIdOrderByExecutionNumberDesc(parent.getId());
            assertThat(parentExecs).isEmpty();

            // Child should have exactly 1
            List<TaskExecution> childExecs =
                    taskExecutionRepository.findByTaskIdOrderByExecutionNumberDesc(child.getId());
            assertThat(childExecs).hasSize(1);
        }
    }
}
