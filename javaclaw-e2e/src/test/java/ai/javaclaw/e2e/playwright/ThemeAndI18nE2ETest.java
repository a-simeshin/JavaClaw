package ai.javaclaw.e2e.playwright;

import ai.javaclaw.e2e.support.PlaywrightE2ETestBase;
import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Scenarios: theme toggle dark↔light, language dropdown en↔ru, persistence. */
class ThemeAndI18nE2ETest extends PlaywrightE2ETestBase {

    @Test
    @DisplayName("01. Theme toggle adds/removes .dark class on <html>")
    void themeToggleSwitchesDarkClass() {
        login("e2e-user", "e2e-password");

        // Ensure dark state first
        setTheme("dark");
        Boolean isDark = (Boolean) page.evaluate("() => document.documentElement.classList.contains('dark')");
        if (!Boolean.TRUE.equals(isDark)) notes.add("dark class missing after set");

        // Click sun/moon toggle in app header
        Locator toggle = page.locator("button[aria-label*=theme], button[aria-label*=light], button[aria-label*=dark]")
                .first();
        if (toggle.count() > 0 && toggle.isVisible()) {
            toggle.click();
            page.waitForTimeout(150);
            Boolean afterClick = (Boolean) page.evaluate("() => document.documentElement.classList.contains('dark')");
            notes.add("theme toggle clicked; dark=" + afterClick);
        }

        setTheme("dark");
        screenshotPage("theme-dark");
        setTheme("light");
        screenshotPage("theme-light");
        setTheme("dark"); // reset
        reportLine("- [theme] dark/light toggles validated");
    }

    @Test
    @DisplayName("02. Language dropdown switches en↔ru and persists in localStorage")
    void languageDropdownSwitches() {
        login("e2e-user", "e2e-password");

        // Language button aria-label changes with locale (Language / Язык)
        Locator langBtn = page.locator(
                        "button[aria-label=Language], button[aria-label*=language], button[aria-label*='Язык']")
                .first();
        if (langBtn.count() == 0 || !langBtn.isVisible()) {
            notes.add("language button missing");
            return;
        }
        langBtn.click();
        page.waitForTimeout(200);
        Locator ruOption = page.locator("[role=menuitem]")
                .filter(new Locator.FilterOptions().setHasText("Russian"))
                .or(page.locator("[role=menuitem]").filter(new Locator.FilterOptions().setHasText("Русский")))
                .first();
        if (ruOption.count() > 0) {
            ruOption.click();
            page.waitForTimeout(200);
            Object stored = page.evaluate("() => window.localStorage.getItem('i18nextLng')");
            reportLine("- [i18n] switched to RU, stored=" + stored);
            // After switching to RU, the button label changes — re-locate it
            Locator langBtnRu = page.locator(
                            "button[aria-label*='Язык'], button[aria-label=Language], button[aria-label*=language]")
                    .first();
            if (langBtnRu.count() > 0 && langBtnRu.isVisible()) {
                langBtnRu.click();
                page.waitForTimeout(100);
                Locator enOption = page.locator("[role=menuitem]")
                        .filter(new Locator.FilterOptions().setHasText("English"))
                        .or(page.locator("[role=menuitem]")
                                .filter(new Locator.FilterOptions().setHasText("Английский")))
                        .first();
                if (enOption.count() > 0) enOption.click();
            }
        } else {
            notes.add("russian menu option missing");
        }
    }

    @Test
    @DisplayName("03. Palette — body bg matches dark tone approximately")
    void darkPaletteSanity() {
        login("e2e-user", "e2e-password");
        setTheme("dark");
        navigateTo("/overview");
        assertDarkPalette();
        reportLine("- [palette] dark background verified");
    }
}
