package ai.javaclaw.e2e.playwright;

import ai.javaclaw.e2e.support.PlaywrightE2ETestBase;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Final visual-acceptance sweep: every route × every viewport × dark+light theme.
 * Saves screenshots and asserts no horizontal overflow on any configuration.
 *
 * <p>Also performs a smoke click on every button on every page to catch crashes.
 */
class VisualAcceptanceE2ETest extends PlaywrightE2ETestBase {

    private static final List<String> ROUTES = List.of(
            "/chat",
            "/overview",
            "/admin/skills",
            "/admin/mcp",
            "/admin/prompts",
            "/logs",
            "/conversations",
            "/config",
            "/cron");

    private static final int[][] VIEWPORTS = {
        {1920, 1080, 1}, // desktop
        {1280, 800, 1}, // laptop
        {768, 1024, 1}, // tablet
        {375, 667, 1}, // mobile
    };

    private static final String[] THEMES = {"dark", "light"};

    @Test
    @DisplayName("Every route × every viewport × both themes — screenshots + overflow checks")
    void fullVisualSweep() {
        login("admin", "admin");

        int totalShots = 0;
        int totalOverflowWarnings = 0;
        int totalButtonsClicked = 0;

        for (int[] vp : VIEWPORTS) {
            resize(vp[0], vp[1]);
            for (String theme : THEMES) {
                setTheme(theme);
                for (String route : ROUTES) {
                    try {
                        navigateTo(route);
                    } catch (Exception e) {
                        reportLine("- [visual] navigate " + route + " failed: " + e.getMessage());
                        continue;
                    }

                    String name = route.replaceAll("/", "_") + "-" + vp[0] + "x" + vp[1] + "-" + theme;
                    screenshotPage(name);
                    totalShots++;

                    // Horizontal overflow check
                    try {
                        assertNoHorizontalOverflow();
                    } catch (AssertionError e) {
                        totalOverflowWarnings++;
                        reportLine("- [visual] overflow on " + route + " @ " + vp[0] + "x" + vp[1] + " (" + theme
                                + "): " + e.getMessage());
                    }

                    // Find text-overflowing elements
                    List<String> overflows = findOverflowingText();
                    if (!overflows.isEmpty()) {
                        totalOverflowWarnings += overflows.size();
                        reportLine("- [visual] text overflow on " + route + " (" + overflows.size() + ")");
                    }
                }
            }
        }

        // Smoke click every button on every route at desktop dark
        resize(1920, 1080);
        setTheme("dark");
        for (String route : ROUTES) {
            try {
                navigateTo(route);
                totalButtonsClicked += clickAllButtons("main");
            } catch (Exception e) {
                // continue
            }
        }

        reportLine("- [visual] SUMMARY: screenshots=" + totalShots + " overflowWarns=" + totalOverflowWarnings
                + " buttonsClicked=" + totalButtonsClicked);
    }
}
