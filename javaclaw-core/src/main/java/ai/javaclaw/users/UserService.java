package ai.javaclaw.users;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

/**
 * Service for user management — CRUD operations on the users table.
 */
@Service
public class UserService {

    private final AppUserRepository repository;
    private final CustomRoleRepository roleRepository;

    public UserService(AppUserRepository repository, CustomRoleRepository roleRepository) {
        this.repository = repository;
        this.roleRepository = roleRepository;
    }

    public List<AppUser> listActive() {
        return repository.findAllActive();
    }

    public Optional<AppUser> findById(String id) {
        return repository.findById(id);
    }

    public Optional<AppUser> findByUsername(String username) {
        return repository.findByUsername(username);
    }

    public AppUser create(String username, String encodedPassword, String role) {
        Assert.hasText(username, "username must not be blank");
        Assert.hasText(encodedPassword, "password must not be blank");
        Assert.hasText(role, "role must not be blank");
        validateRoleExists(role);
        if (repository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists: " + username);
        }
        return repository.save(AppUser.create(username, encodedPassword, role));
    }

    public void updateRole(String id, String role) {
        Assert.hasText(id, "id must not be blank");
        Assert.hasText(role, "role must not be blank");
        validateRoleExists(role);
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("User not found: " + id);
        }
        repository.updateRole(id, role);
    }

    private void validateRoleExists(String role) {
        if (!roleRepository.existsByName(role)) {
            throw new IllegalArgumentException("Unknown role: " + role);
        }
    }

    public void updatePassword(String id, String encodedPassword) {
        Assert.hasText(id, "id must not be blank");
        Assert.hasText(encodedPassword, "password must not be blank");
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("User not found: " + id);
        }
        repository.updatePassword(id, encodedPassword);
    }

    public void deactivate(String id) {
        Assert.hasText(id, "id must not be blank");
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("User not found: " + id);
        }
        repository.deactivate(id);
    }
}
