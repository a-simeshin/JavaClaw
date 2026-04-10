package ai.javaclaw.api.admin.tools;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WebFetchToolTest {

    private WebFetchTool tool;

    @BeforeEach
    void setUp() {
        tool = WebFetchTool.builder().build();
    }

    // ── fetchPage validation ──────────────────────────────────────────────────

    @Test
    void fetchPage_nullUrl_returnsError() {
        final String result = tool.fetchPage(null);
        assertThat(result).startsWith("Error:").contains("blank");
    }

    @Test
    void fetchPage_blankUrl_returnsError() {
        final String result = tool.fetchPage("   ");
        assertThat(result).startsWith("Error:").contains("blank");
    }

    @Test
    void fetchPage_noScheme_returnsError() {
        final String result = tool.fetchPage("www.example.com");
        assertThat(result).startsWith("Error:").contains("http");
    }

    @Test
    void fetchPage_ftpScheme_returnsError() {
        final String result = tool.fetchPage("ftp://example.com/file");
        assertThat(result).startsWith("Error:").contains("http");
    }

    @Test
    void fetchPage_invalidHost_returnsError() {
        final String result = tool.fetchPage("http://this-host-does-not-exist-12345.invalid");
        assertThat(result).startsWith("Error:");
    }

    // ── fetchSelector validation ──────────────────────────────────────────────

    @Test
    void fetchSelector_nullUrl_returnsError() {
        final String result = tool.fetchSelector(null, "body");
        assertThat(result).startsWith("Error:").contains("blank");
    }

    @Test
    void fetchSelector_blankSelector_returnsError() {
        final String result = tool.fetchSelector("https://example.com", "  ");
        assertThat(result).startsWith("Error:").contains("selector");
    }

    @Test
    void fetchSelector_nullSelector_returnsError() {
        final String result = tool.fetchSelector("https://example.com", null);
        assertThat(result).startsWith("Error:").contains("selector");
    }

    @Test
    void fetchSelector_noScheme_returnsError() {
        final String result = tool.fetchSelector("example.com", "body");
        assertThat(result).startsWith("Error:").contains("http");
    }

    @Test
    void fetchSelector_invalidHost_returnsError() {
        final String result = tool.fetchSelector("https://this-host-does-not-exist-12345.invalid", "p");
        assertThat(result).startsWith("Error:");
    }

    // ── builder ───────────────────────────────────────────────────────────────

    @Test
    void builder_createsInstance() {
        final WebFetchTool built = WebFetchTool.builder().build();
        assertThat(built).isNotNull();
    }
}
