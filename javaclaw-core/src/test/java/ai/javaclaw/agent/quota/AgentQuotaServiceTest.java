package ai.javaclaw.agent.quota;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.javaclaw.persistence.api.AgentQuotaQueryRepository;
import ai.javaclaw.tasks.RateLimitExceededException;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentQuotaServiceTest {

    private static final String USER_ID = "user-1";
    private static final int DEFAULT_LIMIT = 100;

    @Mock
    private AgentQuotaRepository repository;

    @Mock
    private AgentQuotaQueryRepository queryRepository;

    private AgentQuotaService service;

    @BeforeEach
    void setUp() {
        service = new AgentQuotaService(repository, queryRepository, DEFAULT_LIMIT, true);
    }

    @Test
    void checkAndIncrementPassesWhenUnderLimit() {
        AgentQuota quota = new AgentQuota(1L, USER_ID, 100, 50, LocalDate.now());
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.of(quota));

        assertThatCode(() -> service.checkAndIncrement(USER_ID)).doesNotThrowAnyException();
        verify(queryRepository).incrementDailyUsed(USER_ID);
    }

    @Test
    void checkAndIncrementThrowsWhenAtLimit() {
        AgentQuota quota = new AgentQuota(1L, USER_ID, 100, 100, LocalDate.now());
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.of(quota));

        assertThatThrownBy(() -> service.checkAndIncrement(USER_ID))
                .isInstanceOf(RateLimitExceededException.class)
                .satisfies(ex -> {
                    RateLimitExceededException rle = (RateLimitExceededException) ex;
                    assertThat(rle.getLimitType()).isEqualTo("daily_agent_quota");
                    assertThat(rle.getCurrentCount()).isEqualTo(100);
                    assertThat(rle.getMaxAllowed()).isEqualTo(100);
                });
        verify(queryRepository, never()).incrementDailyUsed(any());
    }

    @Test
    void checkAndIncrementResetsWhenDateChanged() {
        AgentQuota quota = new AgentQuota(1L, USER_ID, 100, 99, LocalDate.now().minusDays(1));
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.of(quota));

        assertThatCode(() -> service.checkAndIncrement(USER_ID)).doesNotThrowAnyException();
        verify(queryRepository).resetDaily(USER_ID);
        verify(queryRepository).incrementDailyUsed(USER_ID);
    }

    @Test
    void checkAndIncrementCreatesDefaultQuotaWhenNoneExists() {
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        AgentQuota saved = AgentQuota.createDefault(USER_ID, DEFAULT_LIMIT);
        when(repository.save(any(AgentQuota.class))).thenReturn(saved);

        assertThatCode(() -> service.checkAndIncrement(USER_ID)).doesNotThrowAnyException();

        ArgumentCaptor<AgentQuota> captor = ArgumentCaptor.forClass(AgentQuota.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().dailyLimit()).isEqualTo(DEFAULT_LIMIT);
        verify(queryRepository).incrementDailyUsed(USER_ID);
    }

    @Test
    void checkAndIncrementSkipsWhenDisabled() {
        AgentQuotaService disabledService = new AgentQuotaService(repository, queryRepository, DEFAULT_LIMIT, false);

        assertThatCode(() -> disabledService.checkAndIncrement(USER_ID)).doesNotThrowAnyException();
        verify(repository, never()).findByUserId(any());
        verify(queryRepository, never()).incrementDailyUsed(any());
    }

    @Test
    void checkAndIncrementSkipsWhenNullUserId() {
        assertThatCode(() -> service.checkAndIncrement(null)).doesNotThrowAnyException();
        verify(repository, never()).findByUserId(any());
    }

    @Test
    void getQuotaReturnsQuotaWithResetIfDateChanged() {
        AgentQuota stale = new AgentQuota(1L, USER_ID, 100, 80, LocalDate.now().minusDays(1));
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.of(stale));

        AgentQuota result = service.getQuota(USER_ID);
        assertThat(result).isNotNull();
        assertThat(result.dailyUsed()).isZero();
        assertThat(result.resetDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void getQuotaReturnsCurrentQuotaIfSameDay() {
        AgentQuota current = new AgentQuota(1L, USER_ID, 100, 42, LocalDate.now());
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.of(current));

        AgentQuota result = service.getQuota(USER_ID);
        assertThat(result).isNotNull();
        assertThat(result.dailyUsed()).isEqualTo(42);
    }

    @Test
    void getQuotaReturnsNullWhenNotTracked() {
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThat(service.getQuota(USER_ID)).isNull();
    }

    @Test
    void setDailyLimitUpdatesLimit() {
        AgentQuota existing = new AgentQuota(1L, USER_ID, 100, 10, LocalDate.now());
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.of(existing));

        service.setDailyLimit(USER_ID, 200);
        verify(queryRepository).updateDailyLimit(USER_ID, 200);
    }

    @Test
    void setDailyLimitCreatesQuotaIfNotExists() {
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(repository.save(any(AgentQuota.class))).thenReturn(AgentQuota.createDefault(USER_ID, DEFAULT_LIMIT));

        service.setDailyLimit(USER_ID, 50);
        verify(repository).save(any(AgentQuota.class));
        verify(queryRepository).updateDailyLimit(USER_ID, 50);
    }

    @Test
    void gettersReturnConfiguredValues() {
        assertThat(service.getDefaultDailyLimit()).isEqualTo(DEFAULT_LIMIT);
        assertThat(service.isEnabled()).isTrue();
    }
}
