package ai.javaclaw.users;

import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

/** Spring Data JDBC repository for resolving usernames to database user IDs. */
public interface AppUserRepository extends ListCrudRepository<AppUser, String> {

    @Query("SELECT id, username, role FROM users WHERE username = :username AND active = true")
    Optional<AppUser> findByUsername(@Param("username") String username);
}
