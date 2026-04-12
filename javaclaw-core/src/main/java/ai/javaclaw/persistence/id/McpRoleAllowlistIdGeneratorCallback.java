package ai.javaclaw.persistence.id;

import ai.javaclaw.mcp.McpRoleAllowlist;
import java.util.UUID;
import org.springframework.data.relational.core.mapping.event.BeforeConvertCallback;
import org.springframework.stereotype.Component;

/**
 * Генератор идентификаторов для {@link McpRoleAllowlist}.
 *
 * <p><b>Критично:</b> таблица {@code mcp_role_allowlist} (миграция V34) не имеет
 * {@code DEFAULT gen_random_uuid()} в DDL, при этом фабрика {@code McpRoleAllowlist.create()}
 * передаёт {@code id=null}. Без этого колбэка {@code INSERT} падает с NOT NULL/PK-нарушением.
 * Колбэк гарантирует, что новому ряду всегда проставляется UUID.
 */
@Component
public class McpRoleAllowlistIdGeneratorCallback implements BeforeConvertCallback<McpRoleAllowlist> {

    @Override
    public McpRoleAllowlist onBeforeConvert(final McpRoleAllowlist entry) {
        if (entry.id() == null) {
            return new McpRoleAllowlist(
                    UUID.randomUUID().toString(), entry.serverId(), entry.role(), entry.createdAt());
        }
        return entry;
    }
}
