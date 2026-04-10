package ai.javaclaw.api.admin.files;

import ai.javaclaw.users.UserResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;

/**
 * Workspace file CRUD with per-user isolation. Each user sees their own files
 * plus global files (owner_id = NULL). Write/delete operations target only user-owned files.
 */
@RestController
@RequestMapping("/api/files")
public class FileController {

    private static final String BASE = "/api/files";

    private final VirtualFileService fileService;
    private final UserResolver userResolver;

    public FileController(final VirtualFileService fileService, final UserResolver userResolver) {
        this.fileService = fileService;
        this.userResolver = userResolver;
    }

    @GetMapping
    public FileNodeDto tree(final Principal principal) {
        final String userId = userResolver.resolveUserId(principal.getName());
        return fileService.treeForUser(userId);
    }

    @GetMapping("/**")
    public FileContentDto read(final HttpServletRequest request, final Principal principal) {
        final String userId = userResolver.resolveUserId(principal.getName());
        return fileService.readForUser(userId, extractPath(request));
    }

    @PutMapping("/**")
    public FileContentDto write(
            final HttpServletRequest request, @RequestBody final FileContentDto body, final Principal principal) {
        final String userId = userResolver.resolveUserId(principal.getName());
        final String path = extractPath(request);
        final String content = body != null ? body.content() : "";
        return fileService.writeForUser(userId, path, content);
    }

    @DeleteMapping("/**")
    public ResponseEntity<Void> delete(final HttpServletRequest request, final Principal principal) {
        final String userId = userResolver.resolveUserId(principal.getName());
        fileService.deleteForUser(userId, extractPath(request));
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    public ResponseEntity<FileContentDto> create(
            @Valid @RequestBody final CreateFileRequest body, final Principal principal) {
        final String userId = userResolver.resolveUserId(principal.getName());
        return ResponseEntity.status(201).body(fileService.createForUser(userId, body.path(), body.content()));
    }

    private static String extractPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (!uri.startsWith(BASE)) {
            throw new IllegalArgumentException("invalid path");
        }
        String tail = uri.substring(BASE.length());
        if (tail.startsWith("/")) {
            tail = tail.substring(1);
        }
        return UriUtils.decode(tail, java.nio.charset.StandardCharsets.UTF_8);
    }
}
