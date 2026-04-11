package ai.javaclaw.security.web;

import ai.javaclaw.security.audit.AuthAuditService;
import ai.javaclaw.security.audit.AuthEventType;
import ai.javaclaw.security.authn.AuthenticationService;
import ai.javaclaw.security.authn.LoginResult;
import ai.javaclaw.security.authn.UserInfo;
import ai.javaclaw.security.config.CookieProperties;
import ai.javaclaw.security.session.ClientInfo;
import ai.javaclaw.security.session.SessionCookieWriter;
import ai.javaclaw.security.session.SessionTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationService authenticationService;
    private final SessionTokenService sessionTokenService;
    private final SessionCookieWriter cookieWriter;
    private final CookieProperties cookieProperties;
    private final AuthAuditService authAuditService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @RequestBody LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        var clientInfo = ClientInfo.of(httpRequest);
        LoginResult result = authenticationService.login(request.username(), request.password(), clientInfo);

        return switch (result) {
            case LoginResult.Success success -> {
                cookieWriter.write(httpResponse, success.token().rawToken());
                authAuditService.log(
                        AuthEventType.LOGIN_SUCCESS,
                        request.username(),
                        clientInfo.remoteAddr(),
                        "/api/auth/login",
                        null);
                yield ResponseEntity.ok(
                        new LoginResponse(success.user(), success.token().expiresAt()));
            }
            case LoginResult.Failed failed -> {
                authAuditService.log(
                        AuthEventType.LOGIN_FAILURE,
                        request.username(),
                        clientInfo.remoteAddr(),
                        "/api/auth/login",
                        failed.reason());
                yield ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            case LoginResult.SecondFactorRequired ignored ->
                ResponseEntity.status(HttpStatus.ACCEPTED).build();
        };
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            HttpServletRequest httpRequest, HttpServletResponse httpResponse, Authentication authentication) {
        String rawToken = extractRawToken(httpRequest);
        if (rawToken != null) {
            sessionTokenService.revoke(rawToken, "logout");
        }
        cookieWriter.clear(httpResponse);
        String username = authentication != null ? authentication.getName() : null;
        var clientInfo = ClientInfo.of(httpRequest);
        authAuditService.log(AuthEventType.LOGOUT, username, clientInfo.remoteAddr(), "/api/auth/logout", null);
    }

    @PostMapping("/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logoutAll(
            HttpServletRequest httpRequest, HttpServletResponse httpResponse, Authentication authentication) {
        if (authentication != null) {
            sessionTokenService.revokeAllForUser(authentication.getName(), "logout_all");
        }
        cookieWriter.clear(httpResponse);
        String username = authentication != null ? authentication.getName() : null;
        var clientInfo = ClientInfo.of(httpRequest);
        authAuditService.log(AuthEventType.LOGOUT_ALL, username, clientInfo.remoteAddr(), "/api/auth/logout-all", null);
    }

    @GetMapping("/me")
    public ResponseEntity<UserInfo> me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        var userDetails = (UserDetails) authentication.getPrincipal();
        List<String> authorities = userDetails.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .sorted()
                .toList();
        List<String> roles = authorities.stream()
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .toList();
        var userInfo = new UserInfo(authentication.getName(), authentication.getName(), null, roles, authorities);
        return ResponseEntity.ok(userInfo);
    }

    @GetMapping("/sessions")
    public List<SessionInfoDto> listSessions(Authentication authentication) {
        return sessionTokenService.listActiveSessions(authentication.getName()).stream()
                .map(s -> new SessionInfoDto(
                        s.id(), s.createdAt(), s.lastUsedAt(), s.expiresAt(), s.remoteAddr(), s.userAgent()))
                .toList();
    }

    @DeleteMapping("/sessions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeSession(@PathVariable String id, Authentication authentication) {
        // Only allow revoking own sessions (verify ownership before revoking)
        boolean owned = sessionTokenService.listActiveSessions(authentication.getName()).stream()
                .anyMatch(s -> s.id().equals(id));
        if (owned) {
            sessionTokenService.revokeById(id, "revoked_by_user");
        }
    }

    @GetMapping("/csrf")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void csrf(org.springframework.security.web.csrf.CsrfToken csrfToken) {
        // Accessing getToken() forces Spring's DeferredCsrfToken to materialize,
        // which triggers CookieCsrfTokenRepository.saveToken() → XSRF-TOKEN cookie is written.
        if (csrfToken != null) {
            csrfToken.getToken();
        }
    }

    private String extractRawToken(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (var cookie : request.getCookies()) {
            if (cookieProperties.name().equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
