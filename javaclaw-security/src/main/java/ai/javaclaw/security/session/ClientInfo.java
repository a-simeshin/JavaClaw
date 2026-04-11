package ai.javaclaw.security.session;

/** Client connection metadata captured at session creation. */
public record ClientInfo(String remoteAddr, String userAgent) {
    public static ClientInfo of(jakarta.servlet.http.HttpServletRequest request) {
        return new ClientInfo(request.getRemoteAddr(), request.getHeader("User-Agent"));
    }
}
