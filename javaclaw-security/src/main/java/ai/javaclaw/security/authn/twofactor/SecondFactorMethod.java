package ai.javaclaw.security.authn.twofactor;

public enum SecondFactorMethod {
    NONE,
    TOTP,
    EMAIL_OTP,
    SMS_OTP,
    WEBAUTHN
}
