package ai.javaclaw.channels;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class ChannelContextServiceTest {

    @Mock
    private ConversationChannelContextRepository repository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private ChannelContextService service;

    @BeforeEach
    void setUp() {
        service = new ChannelContextService(
                repository, jdbcTemplate, JsonMapper.builder().build());
    }

    @Test
    void saveContextExecutesUpsertSql() {
        service.saveContext("telegram-42", "TelegramChannel", Map.of("chatId", "42"));

        verify(jdbcTemplate)
                .update(anyString(), eq("telegram-42"), eq("TelegramChannel"), anyString(), any(Timestamp.class));
    }

    @Test
    void saveContextHandlesNullRoutingData() {
        service.saveContext("telegram-42", "TelegramChannel", null);

        verify(jdbcTemplate)
                .update(anyString(), eq("telegram-42"), eq("TelegramChannel"), eq("{}"), any(Timestamp.class));
    }

    @Test
    void getContextReturnsRoutingContextWhenFound() {
        final ConversationChannelContext entity =
                new ConversationChannelContext("telegram-42", "TelegramChannel", "{\"chatId\":\"42\"}", Instant.now());
        when(repository.findById("telegram-42")).thenReturn(Optional.of(entity));

        final Optional<RoutingContext> result = service.getContext("telegram-42");

        assertThat(result).isPresent();
        assertThat(result.get().channelName()).isEqualTo("TelegramChannel");
        assertThat(result.get().get("chatId")).isEqualTo("42");
    }

    @Test
    void getContextReturnsEmptyWhenNotFound() {
        when(repository.findById("unknown")).thenReturn(Optional.empty());

        assertThat(service.getContext("unknown")).isEmpty();
    }

    @Test
    void getContextReturnsEmptyForNullConversationId() {
        assertThat(service.getContext(null)).isEmpty();
    }

    @Test
    void getContextDeserialisesEmptyRoutingData() {
        final ConversationChannelContext entity =
                new ConversationChannelContext("web-123", "Web Chat Channel", "{}", Instant.now());
        when(repository.findById("web-123")).thenReturn(Optional.of(entity));

        final Optional<RoutingContext> result = service.getContext("web-123");

        assertThat(result).isPresent();
        assertThat(result.get().data()).isEmpty();
    }
}
