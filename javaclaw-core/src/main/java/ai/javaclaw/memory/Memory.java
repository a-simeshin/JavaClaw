package ai.javaclaw.memory;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("memories")
public record Memory(
        @Id String id,
        @Column("owner_id") String ownerId,
        @Column("key") String key,
        @Column("content") String content,
        @Column("category") String category,
        @Column("created_at") Instant createdAt,
        @Column("updated_at") Instant updatedAt) {

    public static Memory create(String ownerId, String key, String content, String category) {
        return new Memory(null, ownerId, key, content, category, Instant.now(), Instant.now());
    }

    public Memory withContent(String newContent) {
        return new Memory(this.id, this.ownerId, this.key, newContent, this.category, this.createdAt, Instant.now());
    }

    public Memory withCategory(String newCategory) {
        return new Memory(this.id, this.ownerId, this.key, this.content, newCategory, this.createdAt, Instant.now());
    }
}
