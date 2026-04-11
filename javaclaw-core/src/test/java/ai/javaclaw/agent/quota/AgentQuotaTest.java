package ai.javaclaw.agent.quota;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class AgentQuotaTest {

    private static final String USER_ID = "user-1";

    @Test
    void createDefaultSetsCorrectValues() {
        AgentQuota quota = AgentQuota.createDefault(USER_ID, 100);
        assertThat(quota.id()).isNull();
        assertThat(quota.userId()).isEqualTo(USER_ID);
        assertThat(quota.dailyLimit()).isEqualTo(100);
        assertThat(quota.dailyUsed()).isZero();
        assertThat(quota.resetDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void isExceededReturnsTrueWhenAtLimit() {
        AgentQuota quota = new AgentQuota(1L, USER_ID, 10, 10, LocalDate.now());
        assertThat(quota.isExceeded()).isTrue();
    }

    @Test
    void isExceededReturnsTrueWhenOverLimit() {
        AgentQuota quota = new AgentQuota(1L, USER_ID, 10, 15, LocalDate.now());
        assertThat(quota.isExceeded()).isTrue();
    }

    @Test
    void isExceededReturnsFalseWhenUnderLimit() {
        AgentQuota quota = new AgentQuota(1L, USER_ID, 10, 9, LocalDate.now());
        assertThat(quota.isExceeded()).isFalse();
    }

    @Test
    void withIncrementedUsageIncrementsBy1() {
        AgentQuota quota = new AgentQuota(1L, USER_ID, 100, 5, LocalDate.now());
        AgentQuota incremented = quota.withIncrementedUsage();
        assertThat(incremented.dailyUsed()).isEqualTo(6);
        assertThat(incremented.id()).isEqualTo(1L);
        assertThat(incremented.dailyLimit()).isEqualTo(100);
    }

    @Test
    void withDailyResetResetsUsedAndUpdatesDate() {
        AgentQuota quota = new AgentQuota(1L, USER_ID, 100, 50, LocalDate.now().minusDays(1));
        AgentQuota reset = quota.withDailyReset();
        assertThat(reset.dailyUsed()).isZero();
        assertThat(reset.resetDate()).isEqualTo(LocalDate.now());
        assertThat(reset.dailyLimit()).isEqualTo(100);
    }
}
