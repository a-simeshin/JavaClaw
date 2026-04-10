package ai.javaclaw.files;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;

public interface VirtualFileRepository extends ListCrudRepository<VirtualFile, String> {

    Optional<VirtualFile> findByOwnerIdIsNullAndPath(String path);

    List<VirtualFile> findAllByOwnerIdIsNull();

    void deleteByOwnerIdIsNullAndPath(String path);

    boolean existsByOwnerIdIsNullAndPath(String path);

    long countByOwnerIdIsNull();

    // Per-user file methods (Phase 4.3)
    Optional<VirtualFile> findByOwnerIdAndPath(String ownerId, String path);

    List<VirtualFile> findAllByOwnerId(String ownerId);

    void deleteByOwnerIdAndPath(String ownerId, String path);

    boolean existsByOwnerIdAndPath(String ownerId, String path);
}
