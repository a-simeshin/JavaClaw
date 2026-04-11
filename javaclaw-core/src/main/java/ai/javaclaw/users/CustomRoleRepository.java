package ai.javaclaw.users;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JDBC repository for {@code custom_roles} table.
 */
public interface CustomRoleRepository extends ListCrudRepository<CustomRole, Long> {

    @Query("SELECT * FROM custom_roles WHERE name = :name")
    Optional<CustomRole> findByName(@Param("name") String name);

    @Query("SELECT COUNT(*) > 0 FROM custom_roles WHERE name = :name")
    boolean existsByName(@Param("name") String name);

    @Query("SELECT * FROM custom_roles WHERE built_in = FALSE ORDER BY name")
    List<CustomRole> findAllCustom();

    @Query("SELECT * FROM custom_roles ORDER BY built_in DESC, name")
    List<CustomRole> findAllOrdered();
}
