package ai.javaclaw.security.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import ai.javaclaw.security.config.CookieProperties;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class SessionCookieAuthFilterTest {

    @Mock
    private SessionTokenService sessionTokenService;

    private SessionCookieAuthFilter filter;
    private CookieProperties cookieProperties;

    @BeforeEach
    void setUp() {
        cookieProperties = new CookieProperties("JCLAW_SESSION", "/", "", true, "Lax", 86400);
        filter = new SessionCookieAuthFilter(sessionTokenService, cookieProperties);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validCookie_setsAuthenticationInSecurityContext() throws Exception {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "user1", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        when(sessionTokenService.resolve("valid-token")).thenReturn(Optional.of(auth));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("JCLAW_SESSION", "valid-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                .isEqualTo("user1");
        assertThat(chain.getRequest()).isNotNull(); // chain was called
    }

    @Test
    void noCookies_filterPassesThrough_securityContextEmpty() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        // no cookies set
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(sessionTokenService);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void invalidToken_resolveReturnsEmpty_securityContextEmpty() throws Exception {
        when(sessionTokenService.resolve("bad-token")).thenReturn(Optional.empty());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("JCLAW_SESSION", "bad-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void wrongCookieName_filterIgnores_securityContextEmpty() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("OTHER_COOKIE", "some-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(sessionTokenService);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void blankCookieValue_filterIgnores_securityContextEmpty() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("JCLAW_SESSION", "   "));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(sessionTokenService);
    }
}
