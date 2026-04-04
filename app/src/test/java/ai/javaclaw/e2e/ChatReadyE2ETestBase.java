package ai.javaclaw.e2e;

/**
 * Base class for E2E tests that require onboarding to already be completed.
 * The {@code application-e2e.yaml} profile sets {@code agent.onboarding.completed: true}
 * by default, so no additional configuration override is needed here.
 */
abstract class ChatReadyE2ETestBase extends E2ETestBase {}
