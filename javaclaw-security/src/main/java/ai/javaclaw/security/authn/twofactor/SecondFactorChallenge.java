package ai.javaclaw.security.authn.twofactor;

public record SecondFactorChallenge(String challengeId, SecondFactorMethod method) {}
