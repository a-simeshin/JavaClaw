package ai.javaclaw.security.authn.twofactor;

import static org.assertj.core.api.Assertions.*;

import ai.javaclaw.security.authn.UserInfo;
import java.util.List;
import org.junit.jupiter.api.Test;

class NoopSecondFactorProviderTest {

    private final NoopSecondFactorProvider provider = new NoopSecondFactorProvider();

    @Test
    void isRequired_always_returns_false() {
        var user = new UserInfo("id", "user", null, List.of("USER"), List.of("PERM_CHAT_SEND"));
        assertThat(provider.isRequired(user)).isFalse();
    }

    @Test
    void method_returns_NONE() {
        assertThat(provider.method()).isEqualTo(SecondFactorMethod.NONE);
    }

    @Test
    void issueChallenge_throws_UnsupportedOperationException() {
        var user = new UserInfo("id", "user", null, List.of(), List.of());
        assertThatThrownBy(() -> provider.issueChallenge(user)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void verify_returns_true() {
        assertThat(provider.verify("any-challenge-id", "000000")).isTrue();
    }
}
