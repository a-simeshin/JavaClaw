package ai.javaclaw.security.authz;

@FunctionalInterface
public interface PermissionCheck {
    /** Returns true if userId can perform action on targetId. */
    boolean check(String userId, Object targetId, Object action);
}
