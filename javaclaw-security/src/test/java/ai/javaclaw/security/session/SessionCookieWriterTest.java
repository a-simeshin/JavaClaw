package ai.javaclaw.security.session;

import static org.assertj.core.api.Assertions.assertThat;

import ai.javaclaw.security.config.CookieProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

class SessionCookieWriterTest {

    private SessionCookieWriter writer;

    @BeforeEach
    void setUp() {
        CookieProperties props = new CookieProperties("JCLAW_SESSION", "/", "", true, "Lax", 86400);
        writer = new SessionCookieWriter(props);
    }

    @Test
    void write_setsSetCookieHeaderWithCorrectFlags() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.write(response, "tok123");

        String header = response.getHeader("Set-Cookie");
        assertThat(header).isNotNull();
        assertThat(header).contains("JCLAW_SESSION=tok123");
        assertThat(header).contains("Path=/");
        assertThat(header).contains("Max-Age=86400");
        assertThat(header).contains("HttpOnly");
        assertThat(header).contains("SameSite=Lax");
        assertThat(header).contains("Secure");
    }

    @Test
    void write_noDomainSegmentWhenDomainBlank() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.write(response, "tok123");

        String header = response.getHeader("Set-Cookie");
        assertThat(header).doesNotContain("Domain=");
    }

    @Test
    void write_includesDomainWhenSet() {
        CookieProperties props = new CookieProperties("JCLAW_SESSION", "/", "example.com", true, "Lax", 86400);
        SessionCookieWriter writerWithDomain = new SessionCookieWriter(props);
        MockHttpServletResponse response = new MockHttpServletResponse();

        writerWithDomain.write(response, "tok123");

        String header = response.getHeader("Set-Cookie");
        assertThat(header).contains("Domain=example.com");
    }

    @Test
    void clear_setsMaxAgeZero() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.clear(response);

        String header = response.getHeader("Set-Cookie");
        assertThat(header).isNotNull();
        assertThat(header).contains("JCLAW_SESSION=");
        assertThat(header).contains("Max-Age=0");
        assertThat(header).contains("HttpOnly");
        assertThat(header).contains("SameSite=Lax");
    }

    @Test
    void clear_noSecureFlagWhenSecureIsFalse() {
        CookieProperties props = new CookieProperties("JCLAW_SESSION", "/", "", false, "Lax", 86400);
        SessionCookieWriter insecureWriter = new SessionCookieWriter(props);
        MockHttpServletResponse response = new MockHttpServletResponse();

        insecureWriter.clear(response);

        String header = response.getHeader("Set-Cookie");
        assertThat(header).doesNotContain("; Secure");
    }
}
