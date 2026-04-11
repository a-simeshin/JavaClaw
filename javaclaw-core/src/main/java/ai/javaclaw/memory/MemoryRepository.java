package ai.javaclaw.memory;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

public interface MemoryRepository extends ListCrudRepository<Memory, String> {

    List<Memory> findAllByOwnerId(String ownerId);

    List<Memory> findAllByOwnerIdIsNull();

    Optional<Memory> findByOwnerIdAndKey(String ownerId, String key);

    Optional<Memory> findByOwnerIdIsNullAndKey(String key);

    @Query(
            "SELECT * FROM memories WHERE owner_id = :ownerId AND (key ILIKE '%' || :query || '%' OR content ILIKE '%' || :query || '%')")
    List<Memory> searchByOwner(@Param("ownerId") String ownerId, @Param("query") String query);

    @Query(
            "SELECT * FROM memories WHERE owner_id IS NULL AND (key ILIKE '%' || :query || '%' OR content ILIKE '%' || :query || '%')")
    List<Memory> searchGlobal(@Param("query") String query);

    @Modifying
    @Query("DELETE FROM memories WHERE owner_id = :ownerId AND key = :key")
    void deleteByOwnerIdAndKey(@Param("ownerId") String ownerId, @Param("key") String key);

    @Modifying
    @Query("DELETE FROM memories WHERE owner_id IS NULL AND key = :key")
    void deleteByOwnerIdIsNullAndKey(@Param("key") String key);
}
