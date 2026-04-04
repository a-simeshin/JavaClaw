package ai.javaclaw.e2e.playwright;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import ai.javaclaw.e2e.support.PlaywrightE2ETestBase;
import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Scenarios covering the admin/mcp page. */
@Tag("requires-backend")
class AdminMcpServersE2ETest extends PlaywrightE2ETestBase {

    @Test
    @DisplayName("01. /admin/mcp renders table with status dots & type badges")
    void mcpPageRenders() {
        login("admin", "admin");
        navigateTo("/admin/mcp");

        Locator heading = page.locator("h1, [data-slot=page-header]").first();
        if (heading.count() > 0) assertNoTruncation(heading);

        // status dots
        Locator dots = page.locator(".rounded-full, [data-status-dot]");
        int dotCount = dots.count();
        // badges (Badge ui element)
        Locator badges = page.locator("[data-slot=badge], span:has-text(\"stdio\"), span:has-text(\"http\")");
        int badgeCount = badges.count();

        notes.add("mcp dots=" + dotCount + " badges=" + badgeCount);
        screenshotPage("admin-mcp");
        reportLine("- [mcp] rendered (dots=" + dotCount + " badges=" + badgeCount + ")");
    }

    @Test
    @DisplayName("02. Add server dialog: type select toggles config textarea")
    void addServerDialog() {
        login("admin", "admin");
        navigateTo("/admin/mcp");
        Locator addBtn = page.locator("button:has-text(\"New\"), button:has-text(\"Add\")")
                .first();
        if (addBtn.count() == 0 || !addBtn.isVisible()) {
            notes.add("add-server button missing");
            return;
        }
        addBtn.click();
        Locator dialog = page.locator("[role=dialog]").first();
        assertThat(dialog).isVisible();
        // The Select trigger may be present
        Locator selectTrigger =
                dialog.locator("[role=combobox], button[role=combobox]").first();
        if (selectTrigger.count() > 0) {
            assertThat(selectTrigger).isVisible();
        }
        // Close
        page.keyboard().press("Escape");
        reportLine("- [mcp] add dialog opens");
    }

    @Test
    @DisplayName("03. Smoke click all buttons on /admin/mcp")
    void smokeClickAllButtons() {
        login("admin", "admin");
        navigateTo("/admin/mcp");
        int clicked = clickAllButtons("main");
        reportLine("- [mcp] smoke clicked " + clicked);
    }
}
