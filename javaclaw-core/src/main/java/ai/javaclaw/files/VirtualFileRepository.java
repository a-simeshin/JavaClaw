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
}
