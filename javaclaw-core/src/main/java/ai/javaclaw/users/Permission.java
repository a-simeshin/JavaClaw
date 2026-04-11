package ai.javaclaw.users;

/**
 * Granular permissions for RBAC (Phase 13 — 15.2.2).
 * Each permission controls access to a specific operation domain.
 */
public enum Permission {

    // Chat
    CHAT_SEND,
    CHAT_READ,
    CHAT_DELETE,

    // Conversations
    CONVERSATION_CREATE,
    CONVERSATION_LIST,
    CONVERSATION_DELETE,
    CONVERSATION_SHARE,
    CONVERSATION_ACCESS_ALL,

    // Files
    FILE_READ,
    FILE_WRITE,
    FILE_DELETE,
    FILE_UPLOAD,
    FILE_DOWNLOAD,

    // Skills
    SKILL_LIST,
    SKILL_CREATE,
    SKILL_UPDATE,
    SKILL_DELETE,
    SKILL_EXECUTE,

    // MCP Servers
    MCP_LIST,
    MCP_CREATE,
    MCP_UPDATE,
    MCP_DELETE,
    MCP_CONNECT,

    // Users
    USER_LIST,
    USER_CREATE,
    USER_UPDATE,
    USER_DELETE,

    // Tasks
    TASK_CREATE,
    TASK_LIST,
    TASK_CANCEL,
    TASK_APPROVE,

    // Audit
    AUDIT_READ,

    // Agent
    AGENT_CHAT,
    AGENT_CONFIGURE,

    // System
    SYSTEM_ADMIN;

    /** Spring Security authority name (e.g. "PERM_CHAT_SEND"). */
    public String authority() {
        return "PERM_" + name();
    }
}
