package ai.javaclaw.files;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("virtual_files")
public record VirtualFile(
        @Id String id,
        @Column("owner_id") String ownerId,
        @Column("path") String path,
        @Column("content") String content,
        @Column("content_type") String contentType,
        @Column("size_bytes") long sizeBytes,
        @Column("created_at") Instant createdAt,
        @Column("updated_at") Instant updatedAt) {

    public static VirtualFile newGlobalFile(final String path, final String content, final String contentType) {
        final String safeContent = content == null ? "" : content;
        final long size = safeContent.getBytes(StandardCharsets.UTF_8).length;
        return new VirtualFile(null, null, path, safeContent, contentType, size, Instant.now(), Instant.now());
    }

    public static VirtualFile newUserFile(
            final String ownerId, final String path, final String content, final String contentType) {
        final String safeContent = content == null ? "" : content;
        final long size = safeContent.getBytes(StandardCharsets.UTF_8).length;
        return new VirtualFile(null, ownerId, path, safeContent, contentType, size, Instant.now(), Instant.now());
    }

    public VirtualFile withUpdatedContent(final String newContent) {
        final String safeContent = newContent == null ? "" : newContent;
        final long size = safeContent.getBytes(StandardCharsets.UTF_8).length;
        return new VirtualFile(
                this.id, this.ownerId, this.path, safeContent, this.contentType, size, this.createdAt, Instant.now());
    }
}
