package ai.javaclaw.security.authn;

import ai.javaclaw.users.AppUserRepository;
import ai.javaclaw.users.Permission;
import ai.javaclaw.users.PermissionService;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * DB-backed {@link UserDetailsService} — loads users from the {@code users} table
 * via {@link AppUserRepository}. Includes granular permissions as authorities (Phase 13).
 */
@Service
public class JdbcUserDetailsService implements UserDetailsService {

    private final AppUserRepository userRepository;
    private final PermissionService permissionService;

    public JdbcUserDetailsService(AppUserRepository userRepository, PermissionService permissionService) {
        this.userRepository = userRepository;
        this.permissionService = permissionService;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var appUser = userRepository
                .findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        // Build authorities: ROLE_xxx + granular PERM_xxx
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + appUser.role()));

        Set<Permission> permissions = permissionService.getPermissionsForRole(appUser.role());
        for (Permission perm : permissions) {
            authorities.add(new SimpleGrantedAuthority(perm.authority()));
        }

        return User.builder()
                .username(appUser.username())
                .password(appUser.passwordHash())
                .authorities(authorities)
                .disabled(!appUser.active())
                .build();
    }
}
