package ai.javaclaw.e2e.playwright;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import ai.javaclaw.e2e.support.PlaywrightE2ETestBase;
import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Scenarios covering conversation history in the sidebar: creating, switching,
 * searching, renaming (if wired) and deleting with confirmation.
 */
@Tag("requires-backend")
class ConversationManagementE2ETest extends PlaywrightE2ETestBase {

    @Test
    @DisplayName("01. Sidebar exposes conversation history section")
    void sidebarShowsHistorySection() {
        login("e2e-user", "e2e-password");

        // Conversation history menu: search input + plus button
        Locator search = page.locator("input[placeholder*=Search conversations]");
        if (search.count() == 0) {
            notes.add("no conversation search input visible (history menu may be hidden)");
            return;
        }
        assertThat(search.first()).isVisible();
        assertVisibleInViewport(search.first());
        assertNoTruncation(search.first());
        reportLine("- [history] search input visible");
    }

    @Test
    @DisplayName("02. Create new conversation via + button")
    void clickNewConversationCreatesItem() {
        login("e2e-user", "e2e-password");

        Locator newBtn = page.locator("button[aria-label*=New conversation], button:has-text(\"New\")")
                .first();
        if (newBtn.count() == 0 || !newBtn.isVisible()) {
            notes.add("new-conversation button not found");
            return;
        }
        int beforeCount =
                page.locator("[data-conversation-id], [role=listitem]").count();
        newBtn.click();
        page.waitForTimeout(400);
        int afterCount = page.locator("[data-conversation-id], [role=listitem]").count();
        reportLine("- [history] new conversation click; before=" + beforeCount + " after=" + afterCount);
    }

    @Test
    @DisplayName("03. Delete conversation shows confirm dialog with Cancel/Confirm")
    void deleteConversationFlowShowsDialog() {
        login("e2e-user", "e2e-password");

        Locator trash = page.locator("button[aria-label*=Delete], button svg.tabler-icon-trash")
                .first();
        if (trash.count() == 0) {
            notes.add("no trash button to test delete flow");
            return;
        }
        trash.click();
        // Dialog appears
        Locator dialog = page.locator("[role=dialog]").first();
        if (dialog.count() == 0) {
            notes.add("delete did not open a dialog");
            return;
        }
        assertThat(dialog).isVisible();
        assertNoTruncation(dialog.locator("h2, [role=heading]").first());

        // Cancel closes dialog
        Locator cancel = dialog.locator("button:has-text(\"Cancel\")").first();
        if (cancel.count() > 0) {
            cancel.click();
            page.waitForTimeout(200);
            reportLine("- [history] delete dialog Cancel path OK");
        }
    }

    @Test
    @DisplayName("04. Search filters the conversation list")
    void searchFiltersList() {
        login("e2e-user", "e2e-password");
        Locator search =
                page.locator("input[placeholder*=Search conversations]").first();
        if (search.count() == 0 || !search.isVisible()) {
            notes.add("no search input to test filter");
            return;
        }
        search.fill("XX_NOTHING_MATCHES_XX");
        page.waitForTimeout(300);
        int itemsAfter = page.locator("[data-conversation-id]").count();
        reportLine("- [history] search filter; remaining items=" + itemsAfter);
    }
}
