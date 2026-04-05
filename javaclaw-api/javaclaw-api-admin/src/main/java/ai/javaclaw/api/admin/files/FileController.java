package ai.javaclaw.api.admin.files;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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
 * Workspace file CRUD. Path is extracted from the tail of the request URL
 * because Spring's {@code @PathVariable} does not preserve slashes natively.
 */
@RestController
@RequestMapping("/api/files")
public class FileController {

    private static final String BASE = "/api/files";

    private final VirtualFileService fileService;

    public FileController(final VirtualFileService fileService) {
        this.fileService = fileService;
    }

    @GetMapping
    public FileNodeDto tree() {
        return fileService.tree();
    }

    @GetMapping("/**")
    public FileContentDto read(final HttpServletRequest request) {
        return fileService.read(extractPath(request));
    }

    @PutMapping("/**")
    public FileContentDto write(final HttpServletRequest request, @RequestBody final FileContentDto body) {
        final String path = extractPath(request);
        final String content = body != null ? body.content() : "";
        return fileService.write(path, content);
    }

    @DeleteMapping("/**")
    public ResponseEntity<Void> delete(final HttpServletRequest request) {
        fileService.delete(extractPath(request));
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    public ResponseEntity<FileContentDto> create(@Valid @RequestBody final CreateFileRequest body) {
        return ResponseEntity.status(201).body(fileService.create(body.path(), body.content()));
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
