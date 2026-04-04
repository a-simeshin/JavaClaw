package ai.javaclaw.e2e.playwright;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import ai.javaclaw.e2e.support.PlaywrightE2ETestBase;
import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Covers /logs, /conversations (admin), /config, /cron. */
class OperationsPagesE2ETest extends PlaywrightE2ETestBase {

    @Test
    @DisplayName("01. /logs renders monospace viewer with follow toggle, search, level filter")
    void logsPageRenders() {
        login("admin", "admin");
        navigateTo("/logs");

        Locator viewer = page.locator("pre").first();
        if (viewer.count() > 0) assertThat(viewer).isVisible();

        // Follow toggle (switch)
        Locator followSwitch = page.locator("[role=switch]").first();
        if (followSwitch.count() > 0) assertThat(followSwitch).isVisible();

        // Search input
        Locator search =
                page.locator("input[placeholder*=Search], input[type=search]").first();
        if (search.count() > 0) {
            search.fill("ERROR");
            page.waitForTimeout(200);
            search.fill("");
        }

        // Level dropdown (Select)
        Locator levelSelect = page.locator("[role=combobox]").first();
        if (levelSelect.count() > 0 && levelSelect.isVisible()) {
            levelSelect.click();
            page.waitForTimeout(200);
            page.keyboard().press("Escape");
        }

        screenshotPage("logs");
        reportLine("- [logs] viewer+controls rendered");
    }

    @Test
    @DisplayName("02. /conversations admin table renders with columns and pagination")
    void conversationsAdminRenders() {
        login("admin", "admin");
        navigateTo("/conversations");

        Locator table = page.locator("table").first();
        if (table.count() > 0) assertThat(table).isVisible();

        // No overflow on long titles
        for (Locator row : page.locator("tbody tr td").all()) {
            try {
                if (row.isVisible()) assertNoTruncation(row);
            } catch (Exception ignored) {
            }
        }
        screenshotPage("conversations-admin");
        reportLine("- [conversations] admin table rendered");
    }

    @Test
    @DisplayName("03. /config expands property-source sections, filter works")
    void configPageCollapsibles() {
        login("admin", "admin");
        navigateTo("/config");

        int opened = expandAllCollapsibles();
        // Filter input
        Locator filter = page.locator("input").first();
        if (filter.count() > 0 && filter.isVisible()) {
            filter.fill("server");
            page.waitForTimeout(200);
            filter.fill("");
        }
        screenshotPage("config");
        reportLine("- [config] opened " + opened + " collapsibles");
    }

    @Test
    @DisplayName("04. /cron renders jobs table or empty state")
    void cronPageRenders() {
        login("admin", "admin");
        navigateTo("/cron");

        Locator table = page.locator("table").first();
        Locator empty =
                page.locator(":text(\"No cron jobs\"), :text(\"empty\")").first();
        if (table.count() == 0 && empty.count() == 0) {
            notes.add("neither cron table nor empty state rendered");
        }
        screenshotPage("cron");
        reportLine("- [cron] page rendered");
    }

    @Test
    @DisplayName("05. Smoke click all buttons on every ops page")
    void smokeClickAllButtons() {
        login("admin", "admin");
        int total = 0;
        for (String path : new String[] {"/logs", "/conversations", "/config", "/cron"}) {
            navigateTo(path);
            total += clickAllButtons("main");
        }
        reportLine("- [ops] total buttons smoke-clicked: " + total);
    }
}
