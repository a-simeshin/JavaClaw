package ai.javaclaw.users;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

/** Spring Data JDBC repository for users table. */
public interface AppUserRepository extends ListCrudRepository<AppUser, String> {

    @Query("SELECT * FROM users WHERE username = :username AND active = true")
    Optional<AppUser> findByUsername(@Param("username") String username);

    @Query("SELECT * FROM users WHERE active = true ORDER BY username")
    List<AppUser> findAllActive();

    boolean existsByUsername(String username);

    @Modifying
    @Query("UPDATE users SET active = false, updated_at = now() WHERE id = :id")
    void deactivate(@Param("id") String id);

    @Modifying
    @Query("UPDATE users SET password_hash = :hash, updated_at = now() WHERE id = :id")
    void updatePassword(@Param("id") String id, @Param("hash") String hash);

    @Modifying
    @Query("UPDATE users SET role = :role, updated_at = now() WHERE id = :id")
    void updateRole(@Param("id") String id, @Param("role") String role);
}
