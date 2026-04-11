package ai.javaclaw.security.authn;

import java.util.List;

public record UserInfo(String id, String username, String email, List<String> roles, List<String> authorities) {}
