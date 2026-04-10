package ai.javaclaw.api.admin.tools;

import java.io.IOException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;

/**
 * Agent tool for fetching and extracting readable text from web pages using Jsoup. Strips HTML
 * tags, scripts, and styles to return clean text content suitable for LLM consumption.
 */
public class WebFetchTool {

    private static final Logger logger = LoggerFactory.getLogger(WebFetchTool.class);

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int MAX_BODY_SIZE = 1_048_576; // 1 MB
    private static final int MAX_RESPONSE_CHARS = 20_000;
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; JavaClaw/1.0; +https://github.com/javaclaw)";

    @Tool(
            description =
                    """
            Fetches a web page and returns its readable text content (HTML stripped).
            Use this to read articles, documentation, or any public web page.

            - url: Full URL of the page to fetch (must start with http:// or https://).

            Returns the extracted text content (up to 20 000 characters), or an error message on failure.
            """)
    public String fetchPage(final String url) {
        if (url == null || url.isBlank()) {
            return "Error: URL must not be blank.";
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return "Error: URL must start with http:// or https://";
        }
        try {
            final Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(CONNECT_TIMEOUT_MS)
                    .maxBodySize(MAX_BODY_SIZE)
                    .followRedirects(true)
                    .get();

            final String title = doc.title();
            final String bodyText = extractReadableText(doc);

            final StringBuilder result = new StringBuilder();
            if (title != null && !title.isBlank()) {
                result.append("# ").append(title).append("\n\n");
            }
            result.append(bodyText);

            if (result.length() > MAX_RESPONSE_CHARS) {
                return result.substring(0, MAX_RESPONSE_CHARS) + "\n\n[Truncated at " + MAX_RESPONSE_CHARS + " chars]";
            }
            return result.toString();
        } catch (IOException e) {
            logger.warn("fetchPage failed for url={}: {}", url, e.getMessage());
            return "Error: Could not fetch page. " + e.getMessage();
        } catch (Exception e) {
            logger.error("fetchPage unexpected error for url={}", url, e);
            return "Error: Unexpected error. " + e.getMessage();
        }
    }

    @Tool(
            description =
                    """
            Fetches a web page and extracts text matching a CSS selector.
            Use this to extract specific sections like article body, main content, or tables.

            - url: Full URL of the page (must start with http:// or https://).
            - cssSelector: CSS selector to target (e.g. 'article', 'main', '.content', '#readme').

            Returns the text of matched elements, or an error message on failure.
            """)
    public String fetchSelector(final String url, final String cssSelector) {
        if (url == null || url.isBlank()) {
            return "Error: URL must not be blank.";
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return "Error: URL must start with http:// or https://";
        }
        if (cssSelector == null || cssSelector.isBlank()) {
            return "Error: CSS selector must not be blank.";
        }
        try {
            final Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(CONNECT_TIMEOUT_MS)
                    .maxBodySize(MAX_BODY_SIZE)
                    .followRedirects(true)
                    .get();

            final var elements = doc.select(cssSelector);
            if (elements.isEmpty()) {
                return "No elements matched selector '" + cssSelector + "' on " + url;
            }

            final String text = Jsoup.clean(elements.html(), Safelist.none())
                    .replaceAll("(?m)^\\s+$", "")
                    .replaceAll("\n{3,}", "\n\n")
                    .strip();

            if (text.length() > MAX_RESPONSE_CHARS) {
                return text.substring(0, MAX_RESPONSE_CHARS) + "\n\n[Truncated at " + MAX_RESPONSE_CHARS + " chars]";
            }
            return text.isEmpty() ? "Selector matched but contained no text." : text;
        } catch (IOException e) {
            logger.warn("fetchSelector failed for url={}, selector={}: {}", url, cssSelector, e.getMessage());
            return "Error: Could not fetch page. " + e.getMessage();
        } catch (Exception e) {
            logger.error("fetchSelector unexpected error for url={}", url, e);
            return "Error: Unexpected error. " + e.getMessage();
        }
    }

    private static String extractReadableText(final Document doc) {
        doc.select("script, style, nav, footer, header, aside, .ads, .cookie-banner")
                .remove();
        return Jsoup.clean(doc.body().html(), Safelist.none())
                .replaceAll("(?m)^\\s+$", "")
                .replaceAll("\n{3,}", "\n\n")
                .strip();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        public WebFetchTool build() {
            return new WebFetchTool();
        }
    }
}
