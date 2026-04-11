package ai.javaclaw.security.web;

import ai.javaclaw.security.authn.UserInfo;
import java.time.Instant;

public record LoginResponse(UserInfo user, Instant sessionExpiresAt) {}
