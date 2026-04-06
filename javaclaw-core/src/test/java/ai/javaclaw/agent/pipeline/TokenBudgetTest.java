package ai.javaclaw.agent.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TokenBudget}.
 *
 * <p>Verifies the {@code availableForHistory()} computation, non-negativity guarantee,
 * {@code withSystemTokens()} derivation, and zero-budget edge case.
 */
class TokenBudgetTest {

    @Test
    @DisplayName("availableForHistory(): returns max - response - tools - system")
    void availableForHistory_returnsCorrectValue() {
        final TokenBudget budget = new TokenBudget(200_000, 8_000, 4_000, 1_000);

        final int available = budget.availableForHistory();

        assertThat(available).isEqualTo(200_000 - 8_000 - 4_000 - 1_000);
    }

    @Test
    @DisplayName("availableForHistory(): never returns negative when reservations exceed max")
    void availableForHistory_neverNegative() {
        // All reservations sum to more than max
        final TokenBudget budget = new TokenBudget(100, 60, 60, 60);

        final int available = budget.availableForHistory();

        assertThat(available).isZero();
    }

    @Test
    @DisplayName("withSystemTokens(): creates new record with updated system reservation")
    void withSystemTokens_createsNewRecordWithCorrectValue() {
        final TokenBudget original = new TokenBudget(200_000, 8_000, 4_000, 0);

        final TokenBudget updated = original.withSystemTokens(2_500);

        assertThat(updated.reservedForSystem()).isEqualTo(2_500);
        assertThat(updated.maxContextTokens()).isEqualTo(200_000);
        assertThat(updated.reservedForResponse()).isEqualTo(8_000);
        assertThat(updated.reservedForTools()).isEqualTo(4_000);
        // original must not be mutated
        assertThat(original.reservedForSystem()).isZero();
    }

    @Test
    @DisplayName("availableForHistory(): zero budget returns 0")
    void availableForHistory_zeroBudget_returnsZero() {
        final TokenBudget budget = new TokenBudget(0, 0, 0, 0);

        final int available = budget.availableForHistory();

        assertThat(available).isZero();
    }
}
