package ai.javaclaw.security.authn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.javaclaw.security.authn.twofactor.SecondFactorChallenge;
import ai.javaclaw.security.authn.twofactor.SecondFactorMethod;
import ai.javaclaw.security.authn.twofactor.SecondFactorProvider;
import ai.javaclaw.security.config.SessionProperties;
import ai.javaclaw.security.session.ClientInfo;
import ai.javaclaw.security.session.IssuedToken;
import ai.javaclaw.security.session.SessionTokenService;
import ai.javaclaw.users.AppUser;
import ai.javaclaw.users.AppUserRepository;
import ai.javaclaw.users.PermissionService;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    AuthenticationManager authenticationManager;

    @Mock
    SecondFactorProvider secondFactorProvider;

    @Mock
    SessionTokenService sessionTokenService;

    @Mock
    AppUserRepository appUserRepository;

    @Mock
    PermissionService permissionService;

    private AuthenticationService service;
    private SessionProperties sessionProperties;

    private static final String USERNAME = "alice";
    private static final String PASSWORD = "secret";
    private static final AppUser APP_USER = new AppUser("u-1", USERNAME, "{argon2}hash", "USER", true);
    private static final ClientInfo CLIENT_INFO = new ClientInfo("127.0.0.1", "TestAgent");

    @BeforeEach
    void setUp() {
        sessionProperties = new SessionProperties(Duration.ofHours(24), Duration.ofHours(1));
        service = new AuthenticationService(
                authenticationManager, secondFactorProvider,
                sessionTokenService, sessionProperties,
                appUserRepository, permissionService);
    }

    @SuppressWarnings("unchecked")
    private static void stubAuthorities(Authentication auth, String... authNames) {
        Collection<GrantedAuthority> auths = new java.util.ArrayList<>();
        for (String name : authNames) auths.add(new SimpleGrantedAuthority(name));
        doReturn(auths).when(auth).getAuthorities();
    }

    @Test
    void login_validCredentials_returnsSuccess() {
        var auth = mock(Authentication.class);
        stubAuthorities(auth, "ROLE_USER");
        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(appUserRepository.findByUsername(USERNAME)).thenReturn(Optional.of(APP_USER));
        when(secondFactorProvider.isRequired(any())).thenReturn(false);

        var issued = new IssuedToken(
                "raw-token", UUID.randomUUID().toString(), Instant.now().plusSeconds(86400));
        when(sessionTokenService.issue(eq(auth), eq(CLIENT_INFO), any(Duration.class)))
                .thenReturn(issued);

        var result = service.login(USERNAME, PASSWORD, CLIENT_INFO);

        assertThat(result).isInstanceOf(LoginResult.Success.class);
        var success = (LoginResult.Success) result;
        assertThat(success.token()).isSameAs(issued);
        assertThat(success.user().username()).isEqualTo(USERNAME);
        assertThat(success.user().id()).isEqualTo("u-1");
        assertThat(success.user().roles()).containsExactly("USER");
        assertThat(success.user().authorities()).containsExactly("ROLE_USER");
    }

    @Test
    void login_badCredentials_returnsFailed() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        var result = service.login(USERNAME, "wrong", CLIENT_INFO);

        assertThat(result).isInstanceOf(LoginResult.Failed.class);
        assertThat(((LoginResult.Failed) result).reason()).contains("Bad credentials");
    }

    @Test
    void login_secondFactorRequired_returnsSecondFactorRequired() {
        var auth = mock(Authentication.class);
        stubAuthorities(auth, "ROLE_USER");
        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(appUserRepository.findByUsername(USERNAME)).thenReturn(Optional.of(APP_USER));
        when(secondFactorProvider.isRequired(any())).thenReturn(true);

        var challenge = new SecondFactorChallenge("chal-123", SecondFactorMethod.TOTP);
        when(secondFactorProvider.issueChallenge(any())).thenReturn(challenge);
        when(secondFactorProvider.method()).thenReturn(SecondFactorMethod.TOTP);

        var result = service.login(USERNAME, PASSWORD, CLIENT_INFO);

        assertThat(result).isInstanceOf(LoginResult.SecondFactorRequired.class);
        var sfr = (LoginResult.SecondFactorRequired) result;
        assertThat(sfr.challengeId()).isEqualTo("chal-123");
        assertThat(sfr.availableMethods()).containsExactly("TOTP");
    }

    @Test
    void login_userNotFoundAfterAuth_returnsFailed() {
        var auth = mock(Authentication.class);
        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(appUserRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());

        var result = service.login(USERNAME, PASSWORD, CLIENT_INFO);

        assertThat(result).isInstanceOf(LoginResult.Failed.class);
        assertThat(((LoginResult.Failed) result).reason()).contains("User not found");
    }
}
