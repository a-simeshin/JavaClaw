package ai.javaclaw.api.admin.users;

import ai.javaclaw.users.AppUser;
import ai.javaclaw.users.UserService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin CRUD endpoints for user management (Phase 7.1). */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    public UserController(UserService userService, PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public List<UserDto> list() {
        return userService.listActive().stream().map(UserDto::from).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserDto> get(@PathVariable String id) {
        return userService
                .findById(id)
                .map(UserDto::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<UserDto> create(@RequestBody CreateUserRequest request) {
        var user = userService.create(
                request.username(),
                passwordEncoder.encode(request.password()),
                request.role() != null ? request.role() : "USER");
        return ResponseEntity.status(201).body(UserDto.from(user));
    }

    @PutMapping("/{id}/role")
    public ResponseEntity<Void> updateRole(@PathVariable String id, @RequestBody UpdateRoleRequest request) {
        userService.updateRole(id, request.role());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/password")
    public ResponseEntity<Void> updatePassword(@PathVariable String id, @RequestBody UpdatePasswordRequest request) {
        userService.updatePassword(id, passwordEncoder.encode(request.password()));
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable String id) {
        userService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    public record UserDto(String id, String username, String role, boolean active) {
        static UserDto from(AppUser u) {
            return new UserDto(u.id(), u.username(), u.role(), u.active());
        }
    }

    public record CreateUserRequest(String username, String password, String role) {}

    public record UpdateRoleRequest(String role) {}

    public record UpdatePasswordRequest(String password) {}

    @org.springframework.web.bind.annotation.ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<java.util.Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(java.util.Map.of("error", ex.getMessage()));
    }
}
