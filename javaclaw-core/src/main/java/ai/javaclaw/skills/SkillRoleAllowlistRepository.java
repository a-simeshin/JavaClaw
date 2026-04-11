package ai.javaclaw.skills;

import java.util.List;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

public interface SkillRoleAllowlistRepository extends ListCrudRepository<SkillRoleAllowlist, String> {

    List<SkillRoleAllowlist> findBySkillId(String skillId);

    List<SkillRoleAllowlist> findByRole(String role);

    boolean existsBySkillIdAndRole(String skillId, String role);

    @Modifying
    @Query("DELETE FROM skill_role_allowlist WHERE skill_id = :skillId AND role = :role")
    void deleteBySkillIdAndRole(String skillId, String role);

    @Modifying
    @Query("DELETE FROM skill_role_allowlist WHERE skill_id = :skillId")
    void deleteAllBySkillId(String skillId);
}
