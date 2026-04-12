package ai.javaclaw.users;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JDBC repository for the {@code users} table.
 *
 * <p>The three dialect-sensitive {@code UPDATE ... updated_at = now()} statements that
 * used to live here have been extracted to
 * {@link ai.javaclaw.persistence.api.AppUserQueryRepository}.
 */
public interface AppUserRepository extends ListCrudRepository<AppUser, String> {

    @Query("SELECT * FROM users WHERE username = :username AND active = true")
    Optional<AppUser> findByUsername(@Param("username") String username);

    @Query("SELECT * FROM users WHERE active = true ORDER BY username")
    List<AppUser> findAllActive();

    boolean existsByUsername(String username);
}
