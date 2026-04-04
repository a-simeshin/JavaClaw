package ai.javaclaw.api.admin.files;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
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

    private final WorkspaceFileService fileService;

    public FileController(WorkspaceFileService fileService) {
        this.fileService = fileService;
    }

    @GetMapping
    public FileNodeDto tree() throws IOException {
        return fileService.tree();
    }

    @GetMapping("/**")
    public FileContentDto read(HttpServletRequest request) throws IOException {
        return fileService.read(extractPath(request));
    }

    @PutMapping("/**")
    public FileContentDto write(HttpServletRequest request, @RequestBody FileContentDto body) throws IOException {
        String path = extractPath(request);
        String content = body != null ? body.content() : "";
        return fileService.write(path, content);
    }

    @DeleteMapping("/**")
    public ResponseEntity<Void> delete(HttpServletRequest request) throws IOException {
        fileService.delete(extractPath(request));
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    public ResponseEntity<FileContentDto> create(@Valid @RequestBody CreateFileRequest body) throws IOException {
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
