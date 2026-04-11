package ai.javaclaw.security.authn;

import static org.assertj.core.api.Assertions.assertThat;

import ai.javaclaw.users.Permission;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

class AuthorityMapperTest {

    @Test
    void fromPermissions_includesRoleAndPermAuthorities() {
        var permissions = List.of(Permission.CHAT_SEND, Permission.CHAT_READ, Permission.CONVERSATION_LIST);

        Set<GrantedAuthority> authorities = AuthorityMapper.fromPermissions("USER", permissions);

        Set<String> names =
                authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());

        assertThat(names).contains("ROLE_USER", "PERM_CHAT_SEND", "PERM_CHAT_READ", "PERM_CONVERSATION_LIST");
    }

    @Test
    void fromPermissions_emptyPermissions_onlyRolePresent() {
        Set<GrantedAuthority> authorities = AuthorityMapper.fromPermissions("ADMIN", List.of());

        Set<String> names =
                authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());

        assertThat(names).containsExactly("ROLE_ADMIN");
    }

    @Test
    void fromPermissions_permNameMatchesAuthorityMethod() {
        var permissions = List.of(Permission.SYSTEM_ADMIN);

        Set<GrantedAuthority> authorities = AuthorityMapper.fromPermissions("ADMIN", permissions);

        Set<String> names =
                authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());

        // Verify PERM_ prefix matches Permission.authority() contract
        assertThat(names).contains(Permission.SYSTEM_ADMIN.authority());
    }
}
