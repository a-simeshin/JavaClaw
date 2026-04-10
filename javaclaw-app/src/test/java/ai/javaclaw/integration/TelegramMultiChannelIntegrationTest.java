package ai.javaclaw.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.javaclaw.agent.Agent;
import ai.javaclaw.channels.Channel;
import ai.javaclaw.channels.ChannelContextService;
import ai.javaclaw.channels.ChannelRegistry;
import ai.javaclaw.channels.RoutingContext;
import ai.javaclaw.delivery.DeliveryService;
import ai.javaclaw.delivery.NotificationTransport;
import ai.javaclaw.tasks.Task;
import ai.javaclaw.tasks.TaskHandler;
import ai.javaclaw.tasks.TaskHandler.TaskResult;
import ai.javaclaw.tasks.TaskRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.convention.TestBean;

/**
 * Integration tests for per-channel routing and multi-channel isolation.
 *
 * <p>Covers:
 * <ul>
 *   <li>E26: Task from Telegram channel → notification routed back to Telegram (chatId preserved)</li>
 *   <li>E27: Task from Web Chat → notification delivered via SSE, NOT Telegram</li>
 *   <li>E28: Single user with Telegram + Web Chat conversations → separate conversation histories</li>
 * </ul>
 *
 * <p>Uses a mock {@link Channel} registered under the name {@code TelegramChannel} in the real
 * {@link ChannelRegistry}. This avoids pulling in the real Telegram Long-Polling infrastructure
 * while still exercising the full {@link DeliveryService} routing path.
 */
class TelegramMultiChannelIntegrationTest extends IntegrationTestBase {

    // ── Mocked Agent (replaces real LLM calls) ───────────────────────────────

    @TestBean
    Agent agent;

    static Agent agent() {
        return Mockito.mock(Agent.class);
    }

    // ── Spring beans ─────────────────────────────────────────────────────────

    @Autowired
    TaskRepository taskRepository;

    @Autowired
    TaskHandler taskHandler;

    @Autowired
    DeliveryService deliveryService;

    @Autowired
    ChannelContextService channelContextService;

    @Autowired
    ChannelRegistry channelRegistry;

    @Autowired
    NotificationTransport notificationTransport;

    @Autowired
    JdbcTemplate jdbcTemplate;

    // ── Per-test mock TelegramChannel ─────────────────────────────────────────

    /**
     * Mock channel registered under the name {@code TelegramChannel} so that
     * {@link ChannelRegistry#getChannel(String)} returns it during delivery.
     */
    private Channel mockTelegramChannel;

    @BeforeEach
    void setUp() {
        // Create a fresh mock for each test
        mockTelegramChannel = mock(Channel.class);
        when(mockTelegramChannel.getName()).thenReturn("TelegramChannel");
        channelRegistry.registerChannel(mockTelegramChannel);
    }

    @AfterEach
    void tearDown() {
        // Unregister to avoid cross-test pollution
        channelRegistry.unregisterChannel(mockTelegramChannel);
        // Clean DB state
        jdbcTemplate.execute("DELETE FROM task_executions");
        jdbcTemplate.execute("DELETE FROM task_audit_log");
        jdbcTemplate.execute("DELETE FROM tasks");
        jdbcTemplate.execute("DELETE FROM conversation_channel_context");
        jdbcTemplate.execute("DELETE FROM delivery_queue");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // E26 — Task from Telegram: notification routed back to Telegram
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("E26: task created from Telegram conversation — completion notification routed to Telegram chatId 123")
    void e26_taskFromTelegram_notificationRoutedToTelegram() {
        // Given: a conversation that originated from Telegram with chatId=123
        final String conversationId = "telegram-123";
        final String chatId = "123";
        channelContextService.saveContext(conversationId, "TelegramChannel", Map.of("chatId", chatId));

        // And: a task linked to that conversation
        final Task task = taskRepository.save(Task.newTask("e26-task", "Remind about meeting")
                .withConversationId(conversationId)
                .withUserId("user-tg-1"));

        when(agent.prompt(anyString(), anyString(), eq(TaskResult.class)))
                .thenReturn(new TaskResult(Task.Status.completed, "Meeting reminder sent!"));

        // When: task executes
        taskHandler.executeTask(task.getId());

        // Then: the task completes
        final Task updated = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(Task.Status.completed);

        // And: the notification was sent to Telegram via the mock channel
        final ArgumentCaptor<RoutingContext> routingCaptor = ArgumentCaptor.forClass(RoutingContext.class);
        final ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockTelegramChannel).sendMessage(routingCaptor.capture(), messageCaptor.capture());

        final RoutingContext capturedRouting = routingCaptor.getValue();
        assertThat(capturedRouting.channelName()).isEqualTo("TelegramChannel");
        assertThat(capturedRouting.get("chatId")).isEqualTo(chatId);
        assertThat(messageCaptor.getValue()).isNotBlank();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // E27 — Task from Web Chat: notification NOT sent to Telegram
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("E27: task created from Web Chat — completion notification goes via SSE, NOT Telegram")
    void e27_taskFromWebChat_notificationNotSentToTelegram() {
        // Given: a conversation that originated from Web Chat (no Telegram context)
        final String conversationId = "webchat-user-42";
        channelContextService.saveContext(conversationId, "WebChatChannel", Map.of("conversationId", conversationId));

        // And: a task linked to that web-chat conversation
        final Task task = taskRepository.save(Task.newTask("e27-task", "Summarise the report")
                .withConversationId(conversationId)
                .withUserId("user-web-1"));

        when(agent.prompt(anyString(), anyString(), eq(TaskResult.class)))
                .thenReturn(new TaskResult(Task.Status.completed, "Report summarised!"));

        // When: task executes
        taskHandler.executeTask(task.getId());

        // Then: the task completes
        final Task updated = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(Task.Status.completed);

        // And: the mock TelegramChannel was never touched
        verify(mockTelegramChannel, never()).sendMessage(any(RoutingContext.class), anyString());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // E28 — Per-channel peer mode: same user, separate histories
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("E28: single user with Telegram + Web Chat conversations — two distinct conversation_ids in DB")
    void e28_perChannelPeerMode_separateHistories() {
        // Given: the same user interacts via two different channels
        final String userId = "user-dual-99";
        final String telegramConvId = "telegram-999";
        final String webChatConvId = "webchat-user-dual-99";

        // Register both channels' routing contexts (as if the user chatted on both)
        channelContextService.saveContext(telegramConvId, "TelegramChannel", Map.of("chatId", "999"));
        channelContextService.saveContext(webChatConvId, "WebChatChannel", Map.of("conversationId", webChatConvId));

        // When: tasks are created for each channel
        when(agent.prompt(anyString(), anyString(), eq(TaskResult.class)))
                .thenReturn(new TaskResult(Task.Status.completed, "Done"));

        final Task tgTask = taskRepository.save(Task.newTask("e28-tg-task", "Telegram task")
                .withConversationId(telegramConvId)
                .withUserId(userId));
        taskHandler.executeTask(tgTask.getId());

        final Task webTask = taskRepository.save(Task.newTask("e28-web-task", "Web chat task")
                .withConversationId(webChatConvId)
                .withUserId(userId));
        taskHandler.executeTask(webTask.getId());

        // Then: two distinct conversation_channel_context rows exist in the DB
        final List<String> convIds = jdbcTemplate.queryForList(
                """
                SELECT conversation_id
                FROM conversation_channel_context
                WHERE conversation_id IN (?, ?)
                ORDER BY conversation_id
                """,
                String.class,
                telegramConvId,
                webChatConvId);

        assertThat(convIds).hasSize(2);
        assertThat(convIds).contains(telegramConvId, webChatConvId);

        // And: the two conversations are associated with different channels
        final String tgChannel = jdbcTemplate.queryForObject(
                "SELECT channel_name FROM conversation_channel_context WHERE conversation_id = ?",
                String.class,
                telegramConvId);
        final String webChannel = jdbcTemplate.queryForObject(
                "SELECT channel_name FROM conversation_channel_context WHERE conversation_id = ?",
                String.class,
                webChatConvId);

        assertThat(tgChannel).isEqualTo("TelegramChannel");
        assertThat(webChannel).isEqualTo("WebChatChannel");

        // And: both tasks belong to the same userId but have different conversationIds
        final Task updatedTg = taskRepository.findById(tgTask.getId()).orElseThrow();
        final Task updatedWeb = taskRepository.findById(webTask.getId()).orElseThrow();
        assertThat(updatedTg.getUserId()).isEqualTo(userId);
        assertThat(updatedWeb.getUserId()).isEqualTo(userId);
        assertThat(updatedTg.getConversationId()).isNotEqualTo(updatedWeb.getConversationId());
    }
}
