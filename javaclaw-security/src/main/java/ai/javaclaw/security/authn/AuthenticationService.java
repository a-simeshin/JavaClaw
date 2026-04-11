package ai.javaclaw.security.authn;

import ai.javaclaw.security.authn.twofactor.SecondFactorProvider;
import ai.javaclaw.security.config.SessionProperties;
import ai.javaclaw.security.session.ClientInfo;
import ai.javaclaw.security.session.SessionTokenService;
import ai.javaclaw.users.AppUserRepository;
import ai.javaclaw.users.PermissionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final AuthenticationManager authenticationManager;
    private final SecondFactorProvider secondFactorProvider;
    private final SessionTokenService sessionTokenService;
    private final SessionProperties sessionProperties;
    private final AppUserRepository appUserRepository;
    private final PermissionService permissionService;

    public LoginResult login(String username, String password, ClientInfo clientInfo) {
        Authentication auth;
        try {
            auth = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
        } catch (AuthenticationException ex) {
            log.debug("Authentication failed for user {}: {}", username, ex.getMessage());
            return new LoginResult.Failed(ex.getMessage());
        }

        // Build UserInfo
        var appUserOpt = appUserRepository.findByUsername(username);
        if (appUserOpt.isEmpty()) {
            return new LoginResult.Failed("User not found after authentication");
        }
        var appUser = appUserOpt.get();
        List<String> authorities = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .sorted()
                .toList();
        List<String> roles = authorities.stream()
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .toList();
        var userInfo = new UserInfo(appUser.id(), appUser.username(), null, roles, authorities);

        // 2FA check
        if (secondFactorProvider.isRequired(userInfo)) {
            var challenge = secondFactorProvider.issueChallenge(userInfo);
            return new LoginResult.SecondFactorRequired(
                    challenge.challengeId(),
                    List.of(secondFactorProvider.method().name()));
        }

        // Issue session
        var issued = sessionTokenService.issue(auth, clientInfo, sessionProperties.ttl());
        log.debug("Login success for user {}, session {}", username, issued.sessionId());
        return new LoginResult.Success(issued, userInfo);
    }
}
