package ai.javaclaw.security;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code javaclaw.security.users[]} from application.yaml.
 */
@ConfigurationProperties(prefix = "javaclaw.security")
public class SecurityProperties {

    private List<UserEntry> users = new ArrayList<>();

    public List<UserEntry> getUsers() {
        return users;
    }

    public void setUsers(List<UserEntry> users) {
        this.users = users;
    }

    public static class UserEntry {
        private String username;
        private String password;
        private String role = "USER";

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }
    }
}
