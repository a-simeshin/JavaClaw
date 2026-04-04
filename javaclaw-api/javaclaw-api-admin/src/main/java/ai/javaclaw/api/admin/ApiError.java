package ai.javaclaw.api.admin;

import java.time.Instant;

/** Uniform error envelope for {@code /api/**} endpoints. */
public record ApiError(String error, String message, Instant timestamp) {

    public static ApiError of(String error, String message) {
        return new ApiError(error, message, Instant.now());
    }
}
