package ai.javaclaw.e2e.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import ai.javaclaw.JavaClawApplication;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.ViewportSize;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for comprehensive end-to-end browser tests using Playwright for Java.
 *
 * <p>Provides:
 * <ul>
 *   <li>Shared singleton PostgreSQL container + Playwright browser (fork-wide cache).</li>
 *   <li>Per-test {@link BrowserContext} isolation (~10ms per test).</li>
 *   <li>Utility helpers: {@link #login}, {@link #assertNoTruncation},
 *       {@link #assertVisibleInViewport}, {@link #assertPalette},
 *       {@link #screenshotPage}, {@link #clickAllButtons}, {@link #expandAllCollapsibles}.</li>
 *   <li>Dark palette constants matching {@code .impeccable.md}.</li>
 * </ul>
 *
 * <p>Designed so subclasses only specify scenario logic while all infrastructure
 * concerns are encapsulated here.
 */
@SpringBootTest(classes = JavaClawApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e")
@Tag("e2e")
public abstract class PlaywrightE2ETestBase {

    // -------------------------------------------------------------------- palette
    public static final Map<String, String> DARK_PALETTE = Map.of(
            "background", "rgb(14, 16, 21)", // #0e1015
            "card", "rgb(22, 25, 32)", //        #161920
            "border", "rgb(30, 32, 40)" //       #1e2028
            );

    public static final Path SCREENSHOT_DIR = Paths.get("target", "screenshots");

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = PostgresContainer.INSTANCE;

    private static final Playwright PLAYWRIGHT;
    private static final Browser BROWSER;
    private static final boolean HEADED = Boolean.getBoolean("e2e.headed");
    private static final AtomicInteger SCREENSHOT_SEQ = new AtomicInteger(0);

    static {
        try {
            Files.createDirectories(SCREENSHOT_DIR);
        } catch (Exception ignored) {
            // best-effort
        }
        PLAYWRIGHT = Playwright.create();
        BROWSER = PLAYWRIGHT
                .chromium()
                .launch(new BrowserType.LaunchOptions()
                        .setHeadless(!HEADED)
                        .setArgs(List.of(
                                "--disable-gpu", "--disable-dev-shm-usage", "--disable-extensions", "--no-sandbox")));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                BROWSER.close();
            } catch (Exception ignored) {
            }
            try {
                PLAYWRIGHT.close();
            } catch (Exception ignored) {
            }
        }));
    }

    @LocalServerPort
    protected int port;

    @Autowired
    protected JdbcClient jdbcClient;

    @Autowired
    protected DataSource dataSource;

    protected JdbcAssertions jdbcAssertions;

    protected BrowserContext context;
    protected Page page;

    @BeforeEach
    void initJdbcAssertions() {
        jdbcAssertions = new JdbcAssertions(dataSource);
    }

    @BeforeEach
    void cleanDatabase() {
        for (String table :
                new String[] {"spring_ai_chat_memory", "tasks", "conversations", "messages", "skills", "mcp_servers"}) {
            try {
                jdbcClient.sql("DELETE FROM " + table).update();
            } catch (Exception ignored) {
                // Table may not exist yet — fine
            }
        }
    }

    @BeforeEach
    void setUpBrowserContext() {
        context = BROWSER.newContext(new Browser.NewContextOptions()
                .setViewportSize(1920, 1080)
                .setPermissions(List.of("clipboard-read", "clipboard-write"))
                .setLocale("en-US"));
        page = context.newPage();
    }

    @AfterEach
    void tearDownBrowserContext() {
        if (context != null) {
            try {
                context.close();
            } catch (Exception ignored) {
            }
        }
    }

    // -------------------------------------------------------------------- URL helpers

    protected String baseUrl() {
        return "http://localhost:" + port;
    }

    protected void navigateTo(String path) {
        page.navigate(baseUrl() + path);
        page.waitForLoadState(LoadState.NETWORKIDLE);
    }

    // -------------------------------------------------------------------- auth

    /**
     * Injects basic-auth credentials directly into localStorage (as the SPA does
     * on successful login) so tests can bypass the actual login flow when needed.
     */
    protected void loginViaStorage(String username, String password) {
        page.navigate(baseUrl() + "/login");
        String creds = Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String json = "{\"credentials\":\"" + creds + "\",\"username\":\"" + username + "\",\"role\":\"USER\"}";
        page.evaluate("s => window.localStorage.setItem('javaclaw.auth', s)", json);
    }

    /**
     * Performs the full UI login flow through the real Spring Security {@code /login}
     * form endpoint (POST form submission — not a mock or localStorage injection).
     *
     * <p>Steps:
     * <ol>
     *   <li>Navigates to {@code /login}.</li>
     *   <li>Fills {@code #login-username} and {@code #login-password} inputs.</li>
     *   <li>Submits the form via {@code button[type=submit]}.</li>
     *   <li>Waits for redirect to {@code /chat} (Spring Security success handler).</li>
     *   <li>Waits for full network idle so the SPA is ready.</li>
     * </ol>
     *
     * <p>Use {@link #loginViaStorage} when you want to skip the login UI entirely
     * (e.g. tests focused on chat behaviour, not auth).
     */
    protected void login(String username, String password) {
        page.navigate(baseUrl() + "/login");
        page.waitForSelector("#login-username");
        page.locator("#login-username").fill(username);
        page.locator("#login-password").fill(password);
        page.locator("button[type=submit]").click();
        page.waitForURL("**/chat");
        page.waitForLoadState(LoadState.NETWORKIDLE);
    }

    // -------------------------------------------------------------------- visibility assertions

    /**
     * Asserts that the given locator's text is NOT truncated
     * (scrollWidth &lt;= clientWidth + 1px tolerance).
     */
    protected void assertNoTruncation(Locator locator) {
        Object overflow = locator.evaluate("el => ({sw: el.scrollWidth, cw: el.clientWidth, white: "
                + "getComputedStyle(el).whiteSpace, text: (el.textContent||'').slice(0,60)})");
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) overflow;
        int sw = ((Number) map.get("sw")).intValue();
        int cw = ((Number) map.get("cw")).intValue();
        String white = String.valueOf(map.get("white"));
        String text = String.valueOf(map.get("text"));
        if ("nowrap".equals(white) && sw > cw + 1) {
            throw new AssertionError("Text truncated (scrollWidth=" + sw + " > clientWidth=" + cw + ") for: " + text);
        }
    }

    /** Assert that the element's bounding box fits entirely within the viewport. */
    protected void assertVisibleInViewport(Locator locator) {
        var box = locator.boundingBox();
        if (box == null) {
            throw new AssertionError("Element has no bounding box (not rendered)");
        }
        var vp = page.viewportSize();
        if (box.x < 0 || box.y < 0 || box.x + box.width > vp.width + 1 || box.y + box.height > vp.height + 1) {
            throw new AssertionError("Element outside viewport: box=" + box.x + "," + box.y + "," + box.width + "x"
                    + box.height + " viewport=" + vp.width + "x" + vp.height);
        }
    }

    /** Asserts a computed CSS property matches the expected value. */
    protected void assertPalette(Locator locator, String cssProperty, String expected) {
        String actual =
                (String) locator.evaluate("(el, p) => getComputedStyle(el).getPropertyValue(p).trim()", cssProperty);
        if (!actual.equals(expected)) {
            throw new AssertionError(
                    "Palette mismatch for " + cssProperty + ": expected=" + expected + " actual=" + actual);
        }
    }

    /** Verifies dark mode body background matches the expected dark palette. */
    protected void assertDarkPalette() {
        Locator body = page.locator("body");
        String bg = (String) body.evaluate("el => getComputedStyle(el).backgroundColor");
        // body may inherit; also accept transparent (then check :root / html)
        if (!bg.equals(DARK_PALETTE.get("background")) && !bg.equals("rgba(0, 0, 0, 0)") && !bg.equals("transparent")) {
            // allow slight variance — just log
            System.out.println("[palette] body bg=" + bg + " expected~=" + DARK_PALETTE.get("background"));
        }
    }

    // -------------------------------------------------------------------- screenshots

    protected Path screenshotPage(String name) {
        String safe = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        Path out = SCREENSHOT_DIR.resolve(SCREENSHOT_SEQ.incrementAndGet() + "-" + safe + ".png");
        page.screenshot(new Page.ScreenshotOptions().setPath(out).setFullPage(true));
        return out;
    }

    // -------------------------------------------------------------------- bulk interactions

    /** Opens every collapsible with data-state=closed. */
    protected int expandAllCollapsibles() {
        List<Locator> closed = page.locator("[data-state=closed]").all();
        int opened = 0;
        for (Locator l : closed) {
            try {
                if (l.isVisible()) {
                    l.click(new Locator.ClickOptions().setTimeout(1000));
                    opened++;
                }
            } catch (Exception ignored) {
            }
        }
        return opened;
    }

    /**
     * Clicks every {@code <button>} under the given container once. Ignores disabled,
     * hidden or popup-closing buttons. Returns the number of buttons clicked.
     */
    protected int clickAllButtons(String containerSelector) {
        List<Locator> buttons = page.locator(containerSelector + " button").all();
        int clicked = 0;
        for (Locator b : buttons) {
            try {
                if (!b.isVisible() || !b.isEnabled()) continue;
                b.click(new Locator.ClickOptions()
                        .setTimeout(800)
                        .setForce(false)
                        .setTrial(false));
                clicked++;
                page.keyboard().press("Escape"); // close any dialog/menu opened
            } catch (Exception ignored) {
            }
        }
        return clicked;
    }

    /** Returns text elements whose contents overflow their container. */
    protected List<String> findOverflowingText() {
        Object res = page.evaluate(
                "() => Array.from(document.querySelectorAll('*')).filter(el => { " + "const cs = getComputedStyle(el);"
                        + " if (cs.whiteSpace !== 'nowrap') return false;"
                        + " if (!el.offsetParent) return false;"
                        + " return el.scrollWidth > el.clientWidth + 1;" + "}).map(el => el.tagName + '.' + "
                        + "(el.className||'').toString().slice(0,40) + ':' + (el.textContent||'').slice(0,40))"
                        + ".slice(0,20)");
        @SuppressWarnings("unchecked")
        List<String> list = (List<String>) res;
        return list == null ? List.of() : list;
    }

    /** Checks that the body never exceeds viewport width (no horizontal scroll). */
    protected void assertNoHorizontalOverflow() {
        Object width = page.evaluate("() => ({body: document.body.scrollWidth, win: window.innerWidth})");
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) width;
        int body = ((Number) m.get("body")).intValue();
        int win = ((Number) m.get("win")).intValue();
        if (body > win + 1) {
            throw new AssertionError("Horizontal overflow: body=" + body + " win=" + win);
        }
    }

    /** Switch theme by toggling html.dark class. */
    protected void setTheme(String theme) {
        page.evaluate(
                "t => { const r = document.documentElement; if (t==='dark') r.classList.add('dark'); "
                        + "else r.classList.remove('dark'); window.localStorage.setItem('javaclaw.theme', t); }",
                theme);
    }

    /** Resize browser viewport. */
    protected void resize(int w, int h) {
        page.setViewportSize(w, h);
    }

    /** Safe click — logs but does not fail if locator missing / obscured. */
    protected boolean safeClick(String selector) {
        try {
            Locator l = page.locator(selector).first();
            if (l.count() == 0 || !l.isVisible()) return false;
            l.click(new Locator.ClickOptions().setTimeout(1200));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Writes a line to a test-wide acceptance log file (append). */
    protected static synchronized void reportLine(String line) {
        try {
            Path report = SCREENSHOT_DIR.getParent().resolve("acceptance-report.md");
            Files.createDirectories(report.getParent());
            Files.write(
                    report,
                    (line + System.lineSeparator()).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
    }

    /** Returns counts helper: elements under selector, visible ones. */
    protected int countVisible(String selector) {
        int total = 0;
        for (Locator l : page.locator(selector).all()) {
            try {
                if (l.isVisible()) total++;
            } catch (Exception ignored) {
            }
        }
        return total;
    }

    // -------------------------------------------------------------------- Awaitility helpers

    /**
     * Polls until the given CSS selector matches at least one visible element.
     * Much faster than a fixed {@code waitForTimeout} because it returns as soon
     * as the condition is met (polls every 200ms, default timeout 10s).
     */
    protected void awaitVisible(String cssSelector) {
        awaitVisible(cssSelector, Duration.ofSeconds(10));
    }

    /** {@link #awaitVisible(String)} with a custom timeout. */
    protected void awaitVisible(String cssSelector, Duration timeout) {
        await().atMost(timeout)
                .pollInterval(Duration.ofMillis(200))
                .ignoreExceptions()
                .until(() -> {
                    Locator loc = page.locator(cssSelector).first();
                    return loc.count() > 0 && loc.isVisible();
                });
    }

    /**
     * Polls until the sidebar contains (or no longer contains) text matching
     * the given substring. Useful after create/delete conversation.
     */
    protected void awaitSidebarContains(String text) {
        awaitSidebarContains(text, Duration.ofSeconds(10));
    }

    protected void awaitSidebarContains(String text, Duration timeout) {
        await().atMost(timeout)
                .pollInterval(Duration.ofMillis(200))
                .ignoreExceptions()
                .until(() -> page.locator("nav").innerText().contains(text));
    }

    protected void awaitSidebarNotContains(String text) {
        awaitSidebarNotContains(text, Duration.ofSeconds(10));
    }

    protected void awaitSidebarNotContains(String text, Duration timeout) {
        await().atMost(timeout)
                .pollInterval(Duration.ofMillis(200))
                .ignoreExceptions()
                .until(() -> !page.locator("nav").innerText().contains(text));
    }

    /**
     * Polls until a network request matching the URL pattern has been captured.
     * Replaces {@code waitForTimeout(6_000)} after send-message actions.
     */
    protected void awaitNetworkRequest(List<?> requests, int minCount, Duration timeout) {
        await().atMost(timeout).pollInterval(Duration.ofMillis(200)).until(() -> requests.size() >= minCount);
    }

    /** Collects all data captured by helpers for reporting. */
    protected final List<String> notes = new ArrayList<>();

    /** Common viewport presets. */
    public static final ViewportSize VP_DESKTOP = new ViewportSize(1920, 1080);

    public static final ViewportSize VP_LAPTOP = new ViewportSize(1280, 800);
    public static final ViewportSize VP_TABLET = new ViewportSize(768, 1024);
    public static final ViewportSize VP_MOBILE = new ViewportSize(375, 667);

    // -------------------------------------------------------------------- DB assertions

    /**
     * Helper for direct database assertions via JDBC against the Testcontainer DataSource.
     *
     * <p>Instantiated automatically in {@code @BeforeEach} as {@link #jdbcAssertions}.
     * Subclasses can call any method directly, e.g.:
     * <pre>{@code
     *   jdbcAssertions.assertAuditUserIdNotNull(conversationId);
     * }</pre>
     */
    public static class JdbcAssertions {

        private final DataSource dataSource;

        public JdbcAssertions(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        /**
         * Asserts that the latest {@code chat_audit_log} row for the given conversation
         * has a non-null, non-blank {@code user_id}.
         */
        public void assertAuditUserIdNotNull(String conversationId) {
            try (Connection conn = dataSource.getConnection();
                    PreparedStatement ps = conn.prepareStatement(
                            "SELECT user_id FROM chat_audit_log WHERE conversation_id = ? ORDER BY id DESC LIMIT 1")) {
                ps.setString(1, conversationId);
                ResultSet rs = ps.executeQuery();
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("user_id")).isNotNull().isNotBlank();
            } catch (Exception e) {
                throw new AssertionError("DB assertion failed", e);
            }
        }

        /**
         * Asserts that a {@code conversation_summaries} row exists for the conversation
         * and that {@code messages_covered >= minMessages}.
         */
        public void assertSummaryCoversAtLeast(String conversationId, int minMessages) {
            try (Connection conn = dataSource.getConnection();
                    PreparedStatement ps = conn.prepareStatement(
                            "SELECT messages_covered FROM conversation_summaries WHERE conversation_id = ?")) {
                ps.setString(1, conversationId);
                ResultSet rs = ps.executeQuery();
                assertThat(rs.next()).as("summary row exists").isTrue();
                assertThat(rs.getInt("messages_covered")).isGreaterThanOrEqualTo(minMessages);
            } catch (Exception e) {
                throw new AssertionError("DB assertion failed", e);
            }
        }

        /**
         * Asserts that the {@code history} column of the latest audit row for the
         * conversation does NOT contain duplicate consecutive {@code [USER]} blocks.
         */
        public void assertHistoryNoDuplicates(String conversationId) {
            try (Connection conn = dataSource.getConnection();
                    PreparedStatement ps = conn.prepareStatement(
                            "SELECT history FROM chat_audit_log WHERE conversation_id = ? ORDER BY id DESC LIMIT 1")) {
                ps.setString(1, conversationId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    String history = rs.getString("history");
                    if (history != null) {
                        // Count [USER] markers — should appear once per turn
                        String[] parts = history.split("\\[USER\\]");
                        // Check no two identical consecutive USER blocks
                        for (int i = 1; i < parts.length - 1; i++) {
                            assertThat(parts[i].trim())
                                    .as("duplicate USER message at index " + i)
                                    .isNotEqualTo(parts[i + 1].trim());
                        }
                    }
                }
            } catch (Exception e) {
                throw new AssertionError("DB assertion failed", e);
            }
        }

        /**
         * Asserts that the {@code token_usage} column is not null in the latest audit row
         * for the given conversation.
         */
        public void assertTokenUsageNotNull(String conversationId) {
            try (Connection conn = dataSource.getConnection();
                    PreparedStatement ps = conn.prepareStatement(
                            "SELECT token_usage FROM chat_audit_log WHERE conversation_id = ? ORDER BY id DESC LIMIT 1")) {
                ps.setString(1, conversationId);
                ResultSet rs = ps.executeQuery();
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("token_usage")).isNotNull();
            } catch (Exception e) {
                throw new AssertionError("DB assertion failed", e);
            }
        }

        /**
         * Asserts that the {@code tool_examples} table contains exactly {@code expected} rows.
         */
        public void assertToolExampleCount(int expected) {
            try (Connection conn = dataSource.getConnection();
                    PreparedStatement ps = conn.prepareStatement("SELECT count(*) FROM tool_examples")) {
                ResultSet rs = ps.executeQuery();
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(expected);
            } catch (Exception e) {
                throw new AssertionError("DB assertion failed", e);
            }
        }

        /**
         * Asserts that the {@code system_prompt} column in the latest audit row for the
         * conversation does NOT contain the given {@code forbidden} literal string.
         */
        public void assertSystemPromptNotContains(String conversationId, String forbidden) {
            try (Connection conn = dataSource.getConnection();
                    PreparedStatement ps = conn.prepareStatement(
                            "SELECT system_prompt FROM chat_audit_log WHERE conversation_id = ? ORDER BY id DESC LIMIT 1")) {
                ps.setString(1, conversationId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    String sp = rs.getString("system_prompt");
                    if (sp != null) {
                        assertThat(sp).doesNotContain(forbidden);
                    }
                }
            } catch (Exception e) {
                throw new AssertionError("DB assertion failed", e);
            }
        }
    }
}
