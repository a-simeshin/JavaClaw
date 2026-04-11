package ai.javaclaw.users;

import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

/**
 * Resolves an authenticated username to the database user ID from the
 * {@code users} table.
 *
 * <p>Used by controllers that need per-user data isolation (conversations,
 * virtual files).
 */
@Service
public class UserResolver {

    private final AppUserRepository userRepository;

    public UserResolver(final AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Returns the database user ID for the given username.
     *
     * @param username the authenticated username — must not be blank
     * @throws IllegalStateException if the user is not found in the database
     */
    public String resolveUserId(final String username) {
        Assert.hasText(username, "username must not be blank");
        return resolveUser(username).id();
    }

    /**
     * Returns the role for the given username.
     *
     * @param username the authenticated username — must not be blank
     * @throws IllegalStateException if the user is not found in the database
     */
    public String resolveUserRole(final String username) {
        Assert.hasText(username, "username must not be blank");
        return resolveUser(username).role();
    }

    private AppUser resolveUser(final String username) {
        return userRepository
                .findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("User not found in database: " + username));
    }
}
