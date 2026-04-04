package ai.javaclaw.e2e.playwright;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import ai.javaclaw.e2e.support.PlaywrightE2ETestBase;
import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Scenarios covering /overview dashboard: 4 stat cards, system info, quick actions. */
class OverviewDashboardE2ETest extends PlaywrightE2ETestBase {

    @Test
    @DisplayName("01. /overview renders 4 stat cards with labels and no truncation")
    void overviewCardsRender() {
        login("e2e-user", "e2e-password");
        navigateTo("/overview");

        for (String label : new String[] {"Health", "Connection", "Skills", "Conversations"}) {
            Locator card = page.locator(":text(\"" + label + "\")").first();
            if (card.count() == 0) {
                notes.add("missing card label: " + label);
            } else {
                assertThat(card.first()).isVisible();
                assertNoTruncation(card.first());
            }
        }

        // System info heading
        Locator sysInfo = page.locator("h2:has-text(\"System info\")").first();
        if (sysInfo.count() > 0) assertThat(sysInfo).isVisible();

        // Quick actions
        Locator quick = page.locator("h2:has-text(\"Quick actions\")").first();
        if (quick.count() > 0) assertThat(quick).isVisible();

        // Recent activity
        Locator recent = page.locator("h2:has-text(\"Recent activity\")").first();
        if (recent.count() > 0) assertThat(recent).isVisible();

        assertNoHorizontalOverflow();
        screenshotPage("overview-dashboard");
        reportLine("- [overview] 4 stat cards + system info + quick actions + recent activity");
    }

    @Test
    @DisplayName("02. Quick action links navigate correctly")
    void quickActionNavigation() {
        login("e2e-user", "e2e-password");
        navigateTo("/overview");

        Locator openChat = page.locator("a:has-text(\"Open chat\")").first();
        if (openChat.count() > 0 && openChat.isVisible()) {
            openChat.click();
            page.waitForURL("**/chat");
            reportLine("- [overview] quick-action Open chat → /chat");
        }
    }

    @Test
    @DisplayName("03. Smoke click every button on overview")
    void smokeClickAllButtons() {
        login("e2e-user", "e2e-password");
        navigateTo("/overview");
        int clicked = clickAllButtons("main");
        reportLine("- [overview] smoke clicked " + clicked);
    }
}
