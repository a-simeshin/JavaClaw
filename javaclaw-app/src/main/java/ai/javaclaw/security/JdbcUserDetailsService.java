package ai.javaclaw.security;

import ai.javaclaw.users.AppUserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * DB-backed {@link UserDetailsService} — loads users from the {@code users} table
 * via {@link AppUserRepository}. Replaces InMemoryUserDetailsManager (Phase 7.1).
 */
@Service
public class JdbcUserDetailsService implements UserDetailsService {

    private final AppUserRepository userRepository;

    public JdbcUserDetailsService(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var appUser = userRepository
                .findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        return User.builder()
                .username(appUser.username())
                .password(appUser.passwordHash())
                .roles(appUser.role())
                .disabled(!appUser.active())
                .build();
    }
}
