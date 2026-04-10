package ai.javaclaw.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ai.javaclaw.agent.audit.DeliveryAuditLog;
import ai.javaclaw.agent.audit.DeliveryAuditLogRepository;
import ai.javaclaw.ai.memory.AppendableChatMemoryRepository;
import ai.javaclaw.channels.Channel;
import ai.javaclaw.channels.ChannelContextService;
import ai.javaclaw.channels.ChannelRegistry;
import ai.javaclaw.channels.RoutingContext;
import ai.javaclaw.tasks.NotifyPolicy;
import ai.javaclaw.tasks.Task;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;

class DeliveryServiceTest {

    private ChannelContextService channelContextService;
    private ChannelRegistry channelRegistry;
    private NotificationTransport notificationTransport;
    private DeliveryQueueRepository deliveryQueueRepository;
    private DeliveryAuditLogRepository deliveryAuditLogRepository;
    private AppendableChatMemoryRepository chatMemoryRepository;
    private DeliveryService deliveryService;

    @BeforeEach
    void setUp() {
        channelContextService = mock(ChannelContextService.class);
        channelRegistry = mock(ChannelRegistry.class);
        notificationTransport = mock(NotificationTransport.class);
        deliveryQueueRepository = mock(DeliveryQueueRepository.class);
        deliveryAuditLogRepository = mock(DeliveryAuditLogRepository.class);
        chatMemoryRepository = mock(AppendableChatMemoryRepository.class);
        deliveryService = new DeliveryService(
                channelContextService,
                channelRegistry,
                notificationTransport,
                deliveryQueueRepository,
                deliveryAuditLogRepository,
                chatMemoryRepository);
    }

    private Task taskWith(final NotifyPolicy policy, final Task.Status status) {
        return taskWith(policy, status, "conv-1", null);
    }

    private Task taskWith(
            final NotifyPolicy policy,
            final Task.Status status,
            final String conversationId,
            final String sourceChannel) {
        return new Task(
                "task-1",
                "Test Task",
                Instant.now(),
                Instant.now(),
                status,
                "desc",
                null,
                sourceChannel,
                conversationId,
                null,
                policy,
                null,
                null,
                false,
                null,
                null,
                null,
                0);
    }

    @Nested
    @DisplayName("NotifyPolicy filtering (T18-T24)")
    class NotifyPolicyTests {

        @Test
        @DisplayName("T18: SILENT policy — nothing delivered")
        void silentPolicySkipsDelivery() {
            final Task task = taskWith(NotifyPolicy.silent, Task.Status.completed);

            deliveryService.deliver(task, "Result");

            verifyNoInteractions(notificationTransport, deliveryQueueRepository);
            verify(chatMemoryRepository, never()).appendAll(anyString(), any());
        }

        @Test
        @DisplayName("T19: DONE_ONLY + completed — delivers")
        void doneOnlyWithCompletedDelivers() {
            final Task task = taskWith(NotifyPolicy.done_only, Task.Status.completed);
            when(channelContextService.getContext("conv-1"))
                    .thenReturn(Optional.of(new RoutingContext("WebChatChannel", Map.of())));

            deliveryService.deliver(task, "Done!");

            verify(notificationTransport).broadcast("conv-1", "Done!");
        }

        @Test
        @DisplayName("T20: DONE_ONLY + in_progress — skips")
        void doneOnlyWithInProgressSkips() {
            final Task task = taskWith(NotifyPolicy.done_only, Task.Status.in_progress);

            deliveryService.deliver(task, "Progress...");

            verifyNoInteractions(notificationTransport, deliveryQueueRepository);
        }

        @Test
        @DisplayName("T21: ON_ERROR + failed — delivers")
        void onErrorWithFailedDelivers() {
            final Task task = taskWith(NotifyPolicy.on_error, Task.Status.failed);
            when(channelContextService.getContext("conv-1"))
                    .thenReturn(Optional.of(new RoutingContext("WebChatChannel", Map.of())));

            deliveryService.deliver(task, "Error!");

            verify(notificationTransport).broadcast("conv-1", "Error!");
        }

        @Test
        @DisplayName("T22: ON_ERROR + completed — skips")
        void onErrorWithCompletedSkips() {
            final Task task = taskWith(NotifyPolicy.on_error, Task.Status.completed);

            deliveryService.deliver(task, "Done!");

            verifyNoInteractions(notificationTransport, deliveryQueueRepository);
        }

        @Test
        @DisplayName("T23: ON_SUCCESS + completed — delivers")
        void onSuccessWithCompletedDelivers() {
            final Task task = taskWith(NotifyPolicy.on_success, Task.Status.completed);
            when(channelContextService.getContext("conv-1"))
                    .thenReturn(Optional.of(new RoutingContext("WebChatChannel", Map.of())));

            deliveryService.deliver(task, "Success!");

            verify(notificationTransport).broadcast("conv-1", "Success!");
        }

        @Test
        @DisplayName("T24: STATE_CHANGES — delivers on every status change")
        void stateChangesDeliversAlways() {
            when(channelContextService.getContext("conv-1"))
                    .thenReturn(Optional.of(new RoutingContext("WebChatChannel", Map.of())));

            deliveryService.deliver(taskWith(NotifyPolicy.state_changes, Task.Status.in_progress), "Started");
            deliveryService.deliver(taskWith(NotifyPolicy.state_changes, Task.Status.completed), "Done");

            verify(notificationTransport).broadcast("conv-1", "Started");
            verify(notificationTransport).broadcast("conv-1", "Done");
        }

        @Test
        @DisplayName("null policy defaults to DONE_ONLY behavior")
        void nullPolicyDefaultsToDoneOnly() {
            assertThat(deliveryService.shouldNotify(null, Task.Status.completed))
                    .isTrue();
            assertThat(deliveryService.shouldNotify(null, Task.Status.failed)).isTrue();
            assertThat(deliveryService.shouldNotify(null, Task.Status.in_progress))
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("Channel routing (T25-T26)")
    class ChannelRoutingTests {

        @Test
        @DisplayName("T25: Web Chat — broadcasts via NotificationTransport")
        void webChatUsesNotificationTransport() {
            final Task task = taskWith(NotifyPolicy.done_only, Task.Status.completed);
            when(channelContextService.getContext("conv-1"))
                    .thenReturn(Optional.of(new RoutingContext("WebChatChannel", Map.of())));

            deliveryService.deliver(task, "Result");

            verify(notificationTransport).broadcast("conv-1", "Result");
            verify(deliveryQueueRepository, never()).save(any(DeliveryQueue.class));
        }

        @Test
        @DisplayName("T26: Telegram — inserts into delivery_queue + sends via channel")
        void telegramUsesDeliveryQueue() {
            final Task task = taskWith(NotifyPolicy.done_only, Task.Status.completed);
            final RoutingContext routingCtx = new RoutingContext("TelegramChannel", Map.of("chatId", "12345"));
            when(channelContextService.getContext("conv-1")).thenReturn(Optional.of(routingCtx));
            final Channel telegramChannel = mock(Channel.class);
            when(channelRegistry.getChannel("TelegramChannel")).thenReturn(telegramChannel);
            when(deliveryQueueRepository.save(any(DeliveryQueue.class))).thenAnswer(inv -> inv.getArgument(0));

            deliveryService.deliver(task, "Notification");

            verify(deliveryQueueRepository, times(2)).save(any(DeliveryQueue.class));
            verify(telegramChannel).sendMessage(routingCtx, "Notification");
            verify(notificationTransport, never()).broadcast(anyString(), anyString());
        }

        @Test
        @DisplayName("No routing context — falls back to task sourceChannelName")
        void noRoutingContextFallsBackToSourceChannel() {
            final Task task = taskWith(NotifyPolicy.done_only, Task.Status.completed, "conv-1", "WebChatChannel");
            when(channelContextService.getContext("conv-1")).thenReturn(Optional.empty());

            deliveryService.deliver(task, "Fallback");

            verify(notificationTransport).broadcast("conv-1", "Fallback");
        }

        @Test
        @DisplayName("No routing context and no sourceChannel — defaults to WebChatChannel")
        void noContextNoSourceDefaultsToWebChat() {
            final Task task = taskWith(NotifyPolicy.done_only, Task.Status.completed, "conv-1", null);
            when(channelContextService.getContext("conv-1")).thenReturn(Optional.empty());

            deliveryService.deliver(task, "Default");

            verify(notificationTransport).broadcast("conv-1", "Default");
        }
    }

    @Nested
    @DisplayName("Chat memory persistence (T29)")
    class ChatMemoryTests {

        @Test
        @DisplayName("T29: delivers notification and persists to chat memory")
        void persistsNotificationToChatMemory() {
            final Task task = taskWith(NotifyPolicy.done_only, Task.Status.completed);
            when(channelContextService.getContext("conv-1"))
                    .thenReturn(Optional.of(new RoutingContext("WebChatChannel", Map.of())));

            deliveryService.deliver(task, "Task done!");

            @SuppressWarnings("unchecked")
            final ArgumentCaptor<List<Message>> captor = ArgumentCaptor.forClass(List.class);
            verify(chatMemoryRepository).appendAll(eq("conv-1"), captor.capture());
            final List<Message> messages = captor.getValue();
            assertThat(messages).hasSize(1);
            assertThat(messages.get(0)).isInstanceOf(AssistantMessage.class);
            assertThat(messages.get(0).getText()).isEqualTo("Task done!");
        }

        @Test
        @DisplayName("Chat memory failure does not break delivery")
        void chatMemoryFailureDoesNotBreakDelivery() {
            final Task task = taskWith(NotifyPolicy.done_only, Task.Status.completed);
            when(channelContextService.getContext("conv-1"))
                    .thenReturn(Optional.of(new RoutingContext("WebChatChannel", Map.of())));
            doThrow(new RuntimeException("DB error")).when(chatMemoryRepository).appendAll(anyString(), any());

            deliveryService.deliver(task, "Result");

            verify(notificationTransport).broadcast("conv-1", "Result");
        }
    }

    @Nested
    @DisplayName("Delivery audit logging (T30)")
    class AuditTests {

        @Test
        @DisplayName("T30: successful delivery — saves delivered audit log")
        void successfulDeliverySavesAuditLog() {
            final Task task = taskWith(NotifyPolicy.done_only, Task.Status.completed);
            when(channelContextService.getContext("conv-1"))
                    .thenReturn(Optional.of(new RoutingContext("WebChatChannel", Map.of())));

            deliveryService.deliver(task, "Done!");

            final ArgumentCaptor<DeliveryAuditLog> captor = ArgumentCaptor.forClass(DeliveryAuditLog.class);
            verify(deliveryAuditLogRepository).save(captor.capture());
            final DeliveryAuditLog log = captor.getValue();
            assertThat(log.taskId()).isEqualTo("task-1");
            assertThat(log.conversationId()).isEqualTo("conv-1");
            assertThat(log.channelName()).isEqualTo("WebChatChannel");
            assertThat(log.status()).isEqualTo(DeliveryAuditLog.STATUS_DELIVERED);
            assertThat(log.errorMessage()).isNull();
        }

        @Test
        @DisplayName("Delivery failure — saves failed audit log with error")
        void deliveryFailureSavesFailedAuditLog() {
            final Task task = taskWith(NotifyPolicy.done_only, Task.Status.completed);
            final RoutingContext routingCtx = new RoutingContext("TelegramChannel", Map.of("chatId", "123"));
            when(channelContextService.getContext("conv-1")).thenReturn(Optional.of(routingCtx));
            final Channel channel = mock(Channel.class);
            when(channelRegistry.getChannel("TelegramChannel")).thenReturn(channel);
            when(deliveryQueueRepository.save(any(DeliveryQueue.class))).thenAnswer(inv -> inv.getArgument(0));
            doThrow(new RuntimeException("Network error")).when(channel).sendMessage(any(), anyString());

            deliveryService.deliver(task, "Msg");

            final ArgumentCaptor<DeliveryAuditLog> captor = ArgumentCaptor.forClass(DeliveryAuditLog.class);
            verify(deliveryAuditLogRepository).save(captor.capture());
            assertThat(captor.getValue().status()).isEqualTo(DeliveryAuditLog.STATUS_FAILED);
            assertThat(captor.getValue().errorMessage()).isEqualTo("Network error");
        }
    }

    @Test
    @DisplayName("No conversationId — skips delivery gracefully")
    void noConversationIdSkips() {
        final Task task = taskWith(NotifyPolicy.done_only, Task.Status.completed, null, null);

        deliveryService.deliver(task, "Result");

        verifyNoInteractions(notificationTransport, deliveryQueueRepository, chatMemoryRepository);
    }
}
