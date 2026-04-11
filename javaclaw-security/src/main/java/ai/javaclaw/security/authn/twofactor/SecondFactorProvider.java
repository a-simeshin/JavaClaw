package ai.javaclaw.security.authn.twofactor;

import ai.javaclaw.security.authn.UserInfo;

public interface SecondFactorProvider {
    boolean isRequired(UserInfo user);

    SecondFactorChallenge issueChallenge(UserInfo user);

    boolean verify(String challengeId, String code);

    SecondFactorMethod method();
}
