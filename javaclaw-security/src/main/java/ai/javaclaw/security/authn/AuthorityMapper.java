package ai.javaclaw.security.authn;

import ai.javaclaw.users.Permission;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public final class AuthorityMapper {

    private AuthorityMapper() {}

    public static Set<GrantedAuthority> fromPermissions(String role, Collection<Permission> permissions) {
        Set<GrantedAuthority> authorities = permissions.stream()
                .map(p -> (GrantedAuthority) new SimpleGrantedAuthority(p.authority()))
                .collect(Collectors.toCollection(HashSet::new));
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        return authorities;
    }
}
