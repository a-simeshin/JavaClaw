package ai.javaclaw.security.authn;

import ai.javaclaw.security.session.IssuedToken;
import java.util.List;

public sealed interface LoginResult {

    record Success(IssuedToken token, UserInfo user) implements LoginResult {}

    record SecondFactorRequired(String challengeId, List<String> availableMethods) implements LoginResult {}

    record Failed(String reason) implements LoginResult {}
}
