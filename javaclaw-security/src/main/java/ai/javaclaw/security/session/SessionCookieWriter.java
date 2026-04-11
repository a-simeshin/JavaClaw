package ai.javaclaw.security.session;

import ai.javaclaw.security.config.CookieProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SessionCookieWriter {

    private final CookieProperties props;

    public void write(HttpServletResponse response, String rawToken) {
        Cookie cookie = buildCookie(rawToken, props.maxAgeSeconds());
        response.addCookie(cookie);
        // SameSite is not in javax.servlet Cookie API — must set via header
        String header = String.format(
                "%s=%s; Path=%s; Max-Age=%d; HttpOnly; SameSite=%s%s%s",
                props.name(),
                rawToken,
                props.path(),
                props.maxAgeSeconds(),
                props.sameSite(),
                props.secure() ? "; Secure" : "",
                props.domain() != null && !props.domain().isBlank() ? "; Domain=" + props.domain() : "");
        response.setHeader("Set-Cookie", header);
    }

    public void clear(HttpServletResponse response) {
        String header = String.format(
                "%s=; Path=%s; Max-Age=0; HttpOnly; SameSite=%s%s%s",
                props.name(),
                props.path(),
                props.sameSite(),
                props.secure() ? "; Secure" : "",
                props.domain() != null && !props.domain().isBlank() ? "; Domain=" + props.domain() : "");
        response.setHeader("Set-Cookie", header);
    }

    private Cookie buildCookie(String value, int maxAge) {
        Cookie cookie = new Cookie(props.name(), value);
        cookie.setPath(props.path());
        cookie.setHttpOnly(true);
        cookie.setSecure(props.secure());
        cookie.setMaxAge(maxAge);
        if (props.domain() != null && !props.domain().isBlank()) {
            cookie.setDomain(props.domain());
        }
        return cookie;
    }
}
