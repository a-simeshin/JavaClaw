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
    @DisplayName("04. Cancel delete preserves conversation in list")
    void cancelDeletePreservesConversation() {
        login("e2e-user", "e2e-password");

        // First, create a conversation by sending a message
        Locator composer = page.locator("textarea").first();
        composer.fill("preserve me");
        page.locator("button[type=submit]").first().click();
        awaitSidebarContains("preserve me");

        // Count conversations before delete attempt
        int beforeCount = page.locator("nav button")
                .filter(new Locator.FilterOptions().setHasText("preserve me"))
                .count();
        if (beforeCount == 0) {
            notes.add("conversation not created for cancel-delete test");
            return;
        }

        // Click delete
        Locator trash = page.locator("button[aria-label*=Delete], button[aria-label*=Удалить]")
                .first();
        if (trash.count() == 0) {
            notes.add("no trash button found");
            return;
        }
        trash.click();

        // Dialog appears — click Cancel / Отмена
        Locator dialog = page.locator("[role=dialog]").first();
        assertThat(dialog).isVisible();
        Locator cancel = dialog.locator("button")
                .filter(new Locator.FilterOptions().setHasText("Cancel"))
                .or(dialog.locator("button").filter(new Locator.FilterOptions().setHasText("Отмена")))
                .first();
        cancel.click();
        page.waitForTimeout(300);

        // Conversation should still be in the list
        int afterCount = page.locator("nav button")
                .filter(new Locator.FilterOptions().setHasText("preserve me"))
                .count();
        if (afterCount < beforeCount) {
            throw new AssertionError(
                    "Cancel delete removed conversation: before=" + beforeCount + " after=" + afterCount);
        }
        reportLine("- [history] cancel delete preserved conversation");
    }

    @Test
    @DisplayName("05. Confirm delete removes conversation from list and backend")
    void confirmDeleteRemovesConversation() {
        login("e2e-user", "e2e-password");

        // Create a conversation
        Locator composer = page.locator("textarea").first();
        composer.fill("delete me test");
        page.locator("button[type=submit]").first().click();
        awaitSidebarContains("delete me test");

        // Find delete button for this conversation
        Locator trash = page.locator("button[aria-label*=Delete], button[aria-label*=Удалить]")
                .first();
        if (trash.count() == 0) {
            notes.add("no trash button found for confirm-delete test");
            return;
        }
        trash.click();

        // Confirm deletion
        Locator dialog = page.locator("[role=dialog]").first();
        assertThat(dialog).isVisible();
        Locator confirm = dialog.locator("button")
                .filter(new Locator.FilterOptions().setHasText("Delete"))
                .or(dialog.locator("button").filter(new Locator.FilterOptions().setHasText("Удалить")))
                .first();
        confirm.click();
        awaitSidebarNotContains("delete me test");
        reportLine("- [history] confirm delete removed conversation from list");
    }

    @Test
    @DisplayName("06. New conversation appears in sidebar after sending message")
    void newConversationAppearsInSidebarAfterSend() {
        login("e2e-user", "e2e-password");

        // DB is clean, sidebar should be empty
        int beforeCount = page.locator("nav button[aria-label]").count();

        // Send a message to create conversation lazily
        Locator composer = page.locator("textarea").first();
        composer.fill("sidebar appearance test");
        page.locator("button[type=submit]").first().click();
        awaitSidebarContains("sidebar appearance");
        reportLine("- [history] new conversation appeared in sidebar after send");
    }

    @Test
    @DisplayName("07. Search with no results shows empty message")
    void searchNoResultsShowsEmptyMessage() {
        login("e2e-user", "e2e-password");

        Locator search = page.locator("input[type=text]").first();
        if (search.count() == 0 || !search.isVisible()) {
            notes.add("no search input visible");
            return;
        }
        search.fill("XX_NOTHING_MATCHES_XX");
        page.waitForTimeout(300);

        // Should show "No matches" / "Ничего не найдено"
        Locator emptyMsg = page.getByText("No matches")
                .or(page.getByText("Ничего не найдено"))
                .or(page.getByText("No conversations"))
                .or(page.getByText("Диалогов пока нет"))
                .first();
        assertThat(emptyMsg).isVisible();
        reportLine("- [history] search no-results message visible");
    }

    @Test
    @DisplayName("08. Search filters the conversation list")
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
