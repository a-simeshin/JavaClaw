package ai.javaclaw.security.authn.twofactor;

import ai.javaclaw.security.authn.UserInfo;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(value = SecondFactorProvider.class, ignored = NoopSecondFactorProvider.class)
public class NoopSecondFactorProvider implements SecondFactorProvider {

    @Override
    public boolean isRequired(UserInfo user) {
        return false;
    }

    @Override
    public SecondFactorChallenge issueChallenge(UserInfo user) {
        throw new UnsupportedOperationException("Noop provider does not issue challenges");
    }

    @Override
    public boolean verify(String challengeId, String code) {
        return true;
    }

    @Override
    public SecondFactorMethod method() {
        return SecondFactorMethod.NONE;
    }
}
