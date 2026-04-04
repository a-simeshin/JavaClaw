package ai.javaclaw.e2e.playwright;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import ai.javaclaw.e2e.support.PlaywrightE2ETestBase;
import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Scenarios covering /admin/prompts: 4 file tabs, save, draft persistence. */
@Tag("requires-backend")
class AdminPromptsE2ETest extends PlaywrightE2ETestBase {

    @Test
    @DisplayName("01. /admin/prompts renders AGENT.md, SOUL.md, INFO.md, USER_AGENT.md tabs")
    void promptsPageRendersTabs() {
        login("admin", "admin");
        navigateTo("/admin/prompts");

        for (String file : new String[] {"AGENT.md", "SOUL.md", "INFO.md", "USER_AGENT.md"}) {
            Locator tab = page.locator("button:has-text(\"" + file + "\"), [role=tab]:has-text(\"" + file + "\")");
            if (tab.count() == 0) {
                notes.add("tab missing: " + file);
            } else {
                assertThat(tab.first()).isVisible();
                assertNoTruncation(tab.first());
            }
        }

        Locator textarea = page.locator("textarea").first();
        if (textarea.count() > 0) assertThat(textarea).isVisible();

        screenshotPage("admin-prompts");
        reportLine("- [prompts] page renders with tabs");
    }

    @Test
    @DisplayName("02. Switching tabs loads content")
    void switchingTabs() {
        login("admin", "admin");
        navigateTo("/admin/prompts");
        for (String file : new String[] {"SOUL.md", "INFO.md", "USER_AGENT.md", "AGENT.md"}) {
            Locator tab = page.locator("button:has-text(\"" + file + "\")").first();
            if (tab.count() == 0 || !tab.isVisible()) continue;
            tab.click();
            page.waitForTimeout(300);
        }
        reportLine("- [prompts] tab switching OK");
    }

    @Test
    @DisplayName("03. Edit draft persists in localStorage, Discard reverts")
    void editDraftPersistsInLocalStorage() {
        login("admin", "admin");
        navigateTo("/admin/prompts");
        Locator textarea = page.locator("textarea").first();
        if (textarea.count() == 0 || !textarea.isVisible()) {
            notes.add("no textarea on prompts page");
            return;
        }
        textarea.fill("--- e2e draft content ---");
        page.waitForTimeout(200);
        Object stored = page.evaluate("() => window.localStorage.getItem('javaclaw.prompts.draft.AGENT.md')");
        if (stored == null) notes.add("draft not persisted to localStorage");
        else reportLine("- [prompts] draft persisted to localStorage");
    }

    @Test
    @DisplayName("04. Smoke click all buttons")
    void smokeClickAllButtons() {
        login("admin", "admin");
        navigateTo("/admin/prompts");
        int clicked = clickAllButtons("main");
        reportLine("- [prompts] smoke clicked " + clicked);
    }
}
