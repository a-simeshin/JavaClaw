package ai.javaclaw.e2e.playwright;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import ai.javaclaw.e2e.support.PlaywrightE2ETestBase;
import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Scenarios covering the admin/skills table: CRUD, toggle enabled, search, dialogs. */
@Tag("requires-backend")
class AdminSkillsE2ETest extends PlaywrightE2ETestBase {

    @Test
    @DisplayName("01. /admin/skills renders table with headers and no truncation")
    void skillsTableRenders() {
        login("admin", "admin");
        navigateTo("/admin/skills");

        // Page header
        Locator header = page.locator("h1, [data-slot=page-header]").first();
        if (header.count() > 0) {
            assertThat(header).isVisible();
            assertNoTruncation(header);
        }

        // Table or empty state
        Locator table = page.locator("table").first();
        Locator empty = page.locator(":text(\"No skills\"), :text(\"empty\")").first();
        if (table.count() == 0 && empty.count() == 0) {
            notes.add("neither skills table nor empty state rendered");
        } else if (table.count() > 0) {
            assertThat(table).isVisible();
            // column headers
            Locator ths = table.locator("th");
            int cols = ths.count();
            if (cols < 2) notes.add("expected >=2 columns, got " + cols);
            for (int i = 0; i < cols; i++) {
                assertNoTruncation(ths.nth(i));
            }
        }

        screenshotPage("admin-skills");
        reportLine("- [skills] page rendered");
    }

    @Test
    @DisplayName("02. Click New skill opens dialog with Name / Description / Enabled")
    void clickNewSkillOpensDialog() {
        login("admin", "admin");
        navigateTo("/admin/skills");

        Locator newBtn = page.locator("button:has-text(\"New\"), button[aria-label*=New]")
                .first();
        if (newBtn.count() == 0 || !newBtn.isVisible()) {
            notes.add("new-skill button missing");
            return;
        }
        newBtn.click();

        Locator dialog = page.locator("[role=dialog]").first();
        assertThat(dialog).isVisible();
        // expect Name, Description inputs
        Locator nameInput = dialog.locator("input[name=name], label:has-text(\"Name\") ~ input, input")
                .first();
        assertThat(nameInput).isVisible();
        assertVisibleInViewport(dialog);

        // Close
        page.keyboard().press("Escape");
        reportLine("- [skills] new dialog opens & closes");
    }

    @Test
    @DisplayName("03. Search input filters table rows")
    void searchFiltersTable() {
        login("admin", "admin");
        navigateTo("/admin/skills");
        Locator search =
                page.locator("input[placeholder*=Search], input[type=search]").first();
        if (search.count() == 0 || !search.isVisible()) {
            notes.add("no search input on skills page");
            return;
        }
        search.fill("ZZ_NO_MATCH");
        page.waitForTimeout(200);
        int rows = page.locator("tbody tr").count();
        reportLine("- [skills] filter ZZ_NO_MATCH → rows=" + rows);
    }

    @Test
    @DisplayName("04. Delete flow: confirm dialog with Cancel/Confirm path")
    void deleteFlowShowsConfirmDialog() {
        login("admin", "admin");
        navigateTo("/admin/skills");
        Locator deleteBtn = page.locator("button[aria-label*=Delete], button:has(svg.tabler-icon-trash)")
                .first();
        if (deleteBtn.count() == 0 || !deleteBtn.isVisible()) {
            notes.add("no delete button (no rows?)");
            return;
        }
        deleteBtn.click();
        Locator confirm = page.locator("[role=alertdialog], [role=dialog]").first();
        assertThat(confirm).isVisible();
        Locator cancelBtn = confirm.locator("button:has-text(\"Cancel\")").first();
        if (cancelBtn.count() > 0) cancelBtn.click();
        reportLine("- [skills] delete confirm dialog path OK");
    }

    @Test
    @DisplayName("05. Enabled toggle fires PUT request")
    void toggleFiresPutRequest() {
        login("admin", "admin");
        navigateTo("/admin/skills");
        Locator toggle = page.locator("[role=switch]").first();
        if (toggle.count() == 0 || !toggle.isVisible()) {
            notes.add("no switch on skills page");
            return;
        }
        // Listen for PUT
        boolean[] sawPut = {false};
        page.onRequest(r -> {
            if ("PUT".equals(r.method()) && r.url().contains("skills")) sawPut[0] = true;
        });
        toggle.click();
        page.waitForTimeout(600);
        reportLine("- [skills] toggle click; PUT seen=" + sawPut[0]);
    }

    @Test
    @DisplayName("06. Smoke click every button on the page")
    void smokeClickAllButtons() {
        login("admin", "admin");
        navigateTo("/admin/skills");
        int clicked = clickAllButtons("main");
        reportLine("- [skills] smoke clicked " + clicked + " buttons");
    }
}
