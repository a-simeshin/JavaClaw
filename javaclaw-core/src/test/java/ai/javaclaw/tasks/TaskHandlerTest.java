package ai.javaclaw.tasks;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.javaclaw.agent.Agent;
import ai.javaclaw.channels.Channel;
import ai.javaclaw.channels.ChannelContextService;
import ai.javaclaw.channels.ChannelRegistry;
import ai.javaclaw.channels.RoutingContext;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskHandlerTest {

    @Mock
    private Agent agent;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private ChannelRegistry channelRegistry;

    @Mock
    private ChannelContextService channelContextService;

    @Mock
    private Channel channel;

    private TaskHandler taskHandler;

    @BeforeEach
    void setUp() {
        taskHandler = new TaskHandler(agent, taskRepository, channelRegistry, channelContextService);
    }

    @Test
    void notifiesUserViaRoutingContextWhenConversationIdPresent() {
        final Task task = taskWithConversationId("conv-42");
        final RoutingContext ctx = new RoutingContext("TelegramChannel", Map.of("chatId", "42"));
        when(taskRepository.findById("task-1")).thenReturn(Optional.of(task));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(agent.prompt(anyString(), anyString(), any()))
                .thenReturn(new TaskHandler.TaskResult(Task.Status.completed, "Done!"));
        when(channelContextService.getContext("conv-42")).thenReturn(Optional.of(ctx));
        when(channelRegistry.getChannel("TelegramChannel")).thenReturn(channel);

        taskHandler.executeTask("task-1");

        verify(channel).sendMessage(eq(ctx), argThat(msg -> msg.contains("completed") || msg.contains("Done!")));
    }

    @Test
    void fallsBackToSourceChannelNameWhenNoConversationId() {
        final Task task = taskWithSourceChannelName("TelegramChannel");
        when(taskRepository.findById("task-2")).thenReturn(Optional.of(task));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(agent.prompt(anyString(), anyString(), any()))
                .thenReturn(new TaskHandler.TaskResult(Task.Status.completed, "Done!"));
        when(channelContextService.getContext(null)).thenReturn(Optional.empty());
        when(channelRegistry.getChannel("TelegramChannel")).thenReturn(channel);

        taskHandler.executeTask("task-2");

        verify(channel).sendMessage(any(RoutingContext.class), anyString());
    }

    @Test
    void doesNotSendWhenNoChannelFound() {
        final Task task = taskWithConversationId("conv-99");
        when(taskRepository.findById("task-3")).thenReturn(Optional.of(task));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(agent.prompt(anyString(), anyString(), any()))
                .thenReturn(new TaskHandler.TaskResult(Task.Status.completed, "Done!"));
        when(channelContextService.getContext("conv-99")).thenReturn(Optional.empty());
        when(channelRegistry.getChannel(null)).thenReturn(null);

        taskHandler.executeTask("task-3");

        verify(channel, never()).sendMessage(any(RoutingContext.class), anyString());
    }

    @Test
    void doesNotNotifyForInProgressStatus() {
        final Task task = taskWithConversationId("conv-42");
        when(taskRepository.findById("task-4")).thenReturn(Optional.of(task));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(agent.prompt(anyString(), anyString(), any()))
                .thenReturn(new TaskHandler.TaskResult(Task.Status.in_progress, "Still working"));

        taskHandler.executeTask("task-4");

        verify(channel, never()).sendMessage(any(RoutingContext.class), anyString());
    }

    private Task taskWithConversationId(final String conversationId) {
        return new Task(
                "task-1",
                "test-task",
                Instant.now(),
                Instant.now(),
                Task.Status.todo,
                "Do something",
                null,
                null,
                conversationId);
    }

    private Task taskWithSourceChannelName(final String sourceChannelName) {
        return new Task(
                "task-2",
                "test-task",
                Instant.now(),
                Instant.now(),
                Task.Status.todo,
                "Do something",
                null,
                sourceChannelName,
                null);
    }
}
