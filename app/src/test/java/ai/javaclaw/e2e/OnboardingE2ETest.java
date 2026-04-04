package ai.javaclaw.e2e;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * E2E test for the full onboarding wizard flow.
 *
 * <p>Currently disabled because:
 * <ol>
 *   <li>The onboarding flow requires selecting a provider and entering credentials via the UI,
 *       but the E2E profile already pre-configures OpenRouter as the provider.</li>
 *   <li>The onboarding steps (provider selection, credential entry, agent config) interact with
 *       Spring AI's dynamic provider registration, which conflicts with the pre-configured
 *       OpenRouter setup needed for other E2E tests.</li>
 *   <li>Running onboarding with {@code agent.onboarding.completed=false} would change the
 *       application state in ways that affect other tests sharing the same Spring context.</li>
 * </ol>
 *
 * <p>To re-enable: give this test its own Spring context (separate {@code @SpringBootTest} config)
 * with {@code agent.onboarding.completed=false} and implement proper provider credential entry
 * matching the actual onboarding step templates.
 */
@Disabled("Onboarding E2E requires a dedicated Spring context with onboarding.completed=false; "
        + "the shared E2E profile pre-configures OpenRouter, making the onboarding wizard redundant.")
class OnboardingE2ETest extends E2ETestBase {

    @Test
    void completeOnboardingFlow() {
        // Placeholder -- see class Javadoc for why this is disabled.
    }
}
