package ai.javaclaw.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.javaclaw.agent.audit.DeliveryAuditLog;
import ai.javaclaw.agent.audit.DeliveryAuditLogRepository;
import ai.javaclaw.channels.Channel;
import ai.javaclaw.channels.ChannelContextService;
import ai.javaclaw.channels.ChannelRegistry;
import ai.javaclaw.channels.RoutingContext;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DeliveryRecoveryTest {

    private DeliveryQueueRepository deliveryQueueRepository;
    private ChannelRegistry channelRegistry;
    private ChannelContextService channelContextService;
    private DeliveryAuditLogRepository deliveryAuditLogRepository;
    private DeliveryRecovery deliveryRecovery;

    @BeforeEach
    void setUp() {
        deliveryQueueRepository = mock(DeliveryQueueRepository.class);
        channelRegistry = mock(ChannelRegistry.class);
        channelContextService = mock(ChannelContextService.class);
        deliveryAuditLogRepository = mock(DeliveryAuditLogRepository.class);
        deliveryRecovery = new DeliveryRecovery(
                deliveryQueueRepository, channelRegistry, channelContextService, deliveryAuditLogRepository);
    }

    @Test
    @DisplayName("T54-a: no pending deliveries — recovery completes without action")
    void noPendingDeliveriesNoAction() {
        when(deliveryQueueRepository.findByStatusAndNextRetryAtBefore(any(), any()))
                .thenReturn(Collections.emptyList());
        when(deliveryQueueRepository.findByStatus(DeliveryQueue.Status.pending)).thenReturn(Collections.emptyList());

        deliveryRecovery.recoverPendingDeliveries();

        verify(channelRegistry, never()).getChannel(any());
    }

    @Test
    @DisplayName("T54-b: pending delivery recovered and delivered successfully on startup")
    void pendingDeliveryRecoveredAndDelivered() {
        final DeliveryQueue delivery = DeliveryQueue.create("task-1", "conv-1", "TelegramChannel", "Hello!");
        final RoutingContext routingContext = new RoutingContext("TelegramChannel", Map.of());
        final Channel channel = mock(Channel.class);

        when(deliveryQueueRepository.findByStatusAndNextRetryAtBefore(any(), any()))
                .thenReturn(Collections.emptyList());
        when(deliveryQueueRepository.findByStatus(DeliveryQueue.Status.pending)).thenReturn(List.of(delivery));
        when(channelContextService.getContext("conv-1")).thenReturn(Optional.of(routingContext));
        when(channelRegistry.getChannel("TelegramChannel")).thenReturn(channel);

        deliveryRecovery.recoverPendingDeliveries();

        // Claimed + delivered = 2 saves
        verify(deliveryQueueRepository, times(2)).save(any(DeliveryQueue.class));
        verify(channel).sendMessage(routingContext, "Hello!");
        verify(deliveryAuditLogRepository).save(any(DeliveryAuditLog.class));
    }

    @Test
    @DisplayName("T54-c: retry-ready delivery (nextRetryAt in past) recovered on startup")
    void retryReadyDeliveryRecovered() {
        // Simulate a delivery that failed once and is now ready for retry
        final DeliveryQueue failed = DeliveryQueue.create("task-2", "conv-2", "DiscordChannel", "Retry me");
        final DeliveryQueue retryReady = failed.withFailed("first error");
        // retryReady has status=pending, nextRetryAt in future — but we mock it as returned by the query

        final RoutingContext routingContext = new RoutingContext("DiscordChannel", Map.of());
        final Channel channel = mock(Channel.class);

        when(deliveryQueueRepository.findByStatusAndNextRetryAtBefore(any(), any()))
                .thenReturn(List.of(retryReady));
        when(deliveryQueueRepository.findByStatus(DeliveryQueue.Status.pending)).thenReturn(List.of(retryReady));
        when(channelContextService.getContext("conv-2")).thenReturn(Optional.of(routingContext));
        when(channelRegistry.getChannel("DiscordChannel")).thenReturn(channel);

        deliveryRecovery.recoverPendingDeliveries();

        verify(channel).sendMessage(routingContext, "Retry me");
        verify(deliveryQueueRepository, times(2)).save(any(DeliveryQueue.class));
    }

    @Test
    @DisplayName("T54-d: delivery fails during recovery — marked as failed, doesn't break others")
    void deliveryFailsDuringRecoveryDoesNotBreakOthers() {
        final DeliveryQueue delivery1 = DeliveryQueue.create("task-1", "conv-1", "TelegramChannel", "Msg 1");
        final DeliveryQueue delivery2 = DeliveryQueue.create("task-2", "conv-2", "TelegramChannel", "Msg 2");

        final RoutingContext routingContext1 = new RoutingContext("TelegramChannel", Map.of());
        final RoutingContext routingContext2 = new RoutingContext("TelegramChannel", Map.of());
        final Channel channel = mock(Channel.class);

        when(deliveryQueueRepository.findByStatusAndNextRetryAtBefore(any(), any()))
                .thenReturn(Collections.emptyList());
        when(deliveryQueueRepository.findByStatus(DeliveryQueue.Status.pending))
                .thenReturn(List.of(delivery1, delivery2));
        when(channelContextService.getContext("conv-1")).thenReturn(Optional.of(routingContext1));
        when(channelContextService.getContext("conv-2")).thenReturn(Optional.of(routingContext2));
        when(channelRegistry.getChannel("TelegramChannel")).thenReturn(channel);

        // First delivery fails, second succeeds
        doThrow(new RuntimeException("Network error")).when(channel).sendMessage(routingContext1, "Msg 1");

        deliveryRecovery.recoverPendingDeliveries();

        // Both deliveries attempted — 2 claims + 2 results = 4 saves
        verify(deliveryQueueRepository, times(4)).save(any(DeliveryQueue.class));
        // Second delivery still sent
        verify(channel).sendMessage(routingContext2, "Msg 2");
    }

    @Test
    @DisplayName("T54-e: no routing context — delivery fails gracefully")
    void noRoutingContextFailsGracefully() {
        final DeliveryQueue delivery = DeliveryQueue.create("task-1", "conv-1", "TelegramChannel", "Hello!");

        when(deliveryQueueRepository.findByStatusAndNextRetryAtBefore(any(), any()))
                .thenReturn(Collections.emptyList());
        when(deliveryQueueRepository.findByStatus(DeliveryQueue.Status.pending)).thenReturn(List.of(delivery));
        when(channelContextService.getContext("conv-1")).thenReturn(Optional.empty());

        deliveryRecovery.recoverPendingDeliveries();

        // Claimed + failed = 2 saves
        verify(deliveryQueueRepository, times(2)).save(any(DeliveryQueue.class));
        verify(channelRegistry, never()).getChannel(any());
    }

    @Test
    @DisplayName("delivery is claimed with podId before processing")
    void deliveryClaimedWithPodIdBeforeProcessing() {
        final DeliveryQueue delivery = DeliveryQueue.create("task-1", "conv-1", "TelegramChannel", "Hello!");
        final RoutingContext routingContext = new RoutingContext("TelegramChannel", Map.of());
        final Channel channel = mock(Channel.class);

        when(deliveryQueueRepository.findByStatusAndNextRetryAtBefore(any(), any()))
                .thenReturn(Collections.emptyList());
        when(deliveryQueueRepository.findByStatus(DeliveryQueue.Status.pending)).thenReturn(List.of(delivery));
        when(channelContextService.getContext("conv-1")).thenReturn(Optional.of(routingContext));
        when(channelRegistry.getChannel("TelegramChannel")).thenReturn(channel);

        deliveryRecovery.recoverPendingDeliveries();

        final ArgumentCaptor<DeliveryQueue> captor = ArgumentCaptor.forClass(DeliveryQueue.class);
        verify(deliveryQueueRepository, times(2)).save(captor.capture());

        // First save: claimed with processing status
        final DeliveryQueue claimed = captor.getAllValues().get(0);
        assertThat(claimed.getStatus()).isEqualTo(DeliveryQueue.Status.processing);
        assertThat(claimed.getClaimedBy()).isEqualTo(deliveryRecovery.getPodId());
        assertThat(claimed.getClaimedAt()).isNotNull();

        // Second save: delivered
        final DeliveryQueue delivered = captor.getAllValues().get(1);
        assertThat(delivered.getStatus()).isEqualTo(DeliveryQueue.Status.delivered);
    }

    @Test
    @DisplayName("successful delivery logs delivered audit entry")
    void successfulDeliveryLogsDeliveredAudit() {
        final DeliveryQueue delivery = DeliveryQueue.create("task-1", "conv-1", "TelegramChannel", "Hello!");
        final RoutingContext routingContext = new RoutingContext("TelegramChannel", Map.of());
        final Channel channel = mock(Channel.class);

        when(deliveryQueueRepository.findByStatusAndNextRetryAtBefore(any(), any()))
                .thenReturn(Collections.emptyList());
        when(deliveryQueueRepository.findByStatus(DeliveryQueue.Status.pending)).thenReturn(List.of(delivery));
        when(channelContextService.getContext("conv-1")).thenReturn(Optional.of(routingContext));
        when(channelRegistry.getChannel("TelegramChannel")).thenReturn(channel);

        deliveryRecovery.recoverPendingDeliveries();

        final ArgumentCaptor<DeliveryAuditLog> auditCaptor = ArgumentCaptor.forClass(DeliveryAuditLog.class);
        verify(deliveryAuditLogRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().status()).isEqualTo("delivered");
    }

    @Test
    @DisplayName("failed delivery logs failed audit entry")
    void failedDeliveryLogsFailedAudit() {
        final DeliveryQueue delivery = DeliveryQueue.create("task-1", "conv-1", "TelegramChannel", "Hello!");

        when(deliveryQueueRepository.findByStatusAndNextRetryAtBefore(any(), any()))
                .thenReturn(Collections.emptyList());
        when(deliveryQueueRepository.findByStatus(DeliveryQueue.Status.pending)).thenReturn(List.of(delivery));
        when(channelContextService.getContext("conv-1")).thenReturn(Optional.empty());

        deliveryRecovery.recoverPendingDeliveries();

        final ArgumentCaptor<DeliveryAuditLog> auditCaptor = ArgumentCaptor.forClass(DeliveryAuditLog.class);
        verify(deliveryAuditLogRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().status()).isEqualTo("failed");
    }

    @Test
    @DisplayName("podId is generated and unique")
    void podIdIsGenerated() {
        assertThat(deliveryRecovery.getPodId()).startsWith("pod-");
        assertThat(deliveryRecovery.getPodId()).hasSize(12); // "pod-" + 8 chars
    }

    @Test
    @DisplayName("audit logging failure doesn't break delivery processing")
    void auditFailureDoesNotBreakDelivery() {
        final DeliveryQueue delivery = DeliveryQueue.create("task-1", "conv-1", "TelegramChannel", "Hello!");
        final RoutingContext routingContext = new RoutingContext("TelegramChannel", Map.of());
        final Channel channel = mock(Channel.class);

        when(deliveryQueueRepository.findByStatusAndNextRetryAtBefore(any(), any()))
                .thenReturn(Collections.emptyList());
        when(deliveryQueueRepository.findByStatus(DeliveryQueue.Status.pending)).thenReturn(List.of(delivery));
        when(channelContextService.getContext("conv-1")).thenReturn(Optional.of(routingContext));
        when(channelRegistry.getChannel("TelegramChannel")).thenReturn(channel);
        when(deliveryAuditLogRepository.save(any())).thenThrow(new RuntimeException("DB error"));

        deliveryRecovery.recoverPendingDeliveries();

        // Delivery still completed despite audit failure
        verify(channel).sendMessage(routingContext, "Hello!");
        verify(deliveryQueueRepository, times(2)).save(any(DeliveryQueue.class));
    }
}
