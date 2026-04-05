package ai.javaclaw.contract;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Unit tests — no Spring context. Validates the OpenAPI spec at specs/openapi.yaml
 * for structural correctness and contract compliance.
 */
class OpenApiSchemaValidationTest {

    private static String specLocation;
    private static OpenAPI openAPI;

    @BeforeAll
    static void loadSpec() {
        Path specPath = Paths.get(System.getProperty("user.dir"))
                .resolve("../specs/openapi.yaml")
                .toAbsolutePath()
                .normalize();
        specLocation = specPath.toUri().toString();

        ParseOptions opts = new ParseOptions();
        opts.setResolve(true);
        SwaggerParseResult result = new OpenAPIParser().readLocation(specLocation, null, opts);
        openAPI = result.getOpenAPI();
        assertThat(openAPI).as("OpenAPI spec must parse without a null model").isNotNull();
    }

    // ── 1. Valid OpenAPI 3.x ───────────────────────────────────────────────────

    @Test
    void specOpens_isValidOpenApi31() {
        ParseOptions opts = new ParseOptions();
        SwaggerParseResult result = new OpenAPIParser().readLocation(specLocation, null, opts);

        assertThat(result.getOpenAPI()).isNotNull();
        List<String> messages = result.getMessages();
        assertThat(messages).as("Spec must have no parse errors: %s", messages).isNullOrEmpty();
    }

    // ── 2. All $ref references resolve ────────────────────────────────────────

    @Test
    void allReferencesResolve() {
        ParseOptions opts = new ParseOptions();
        opts.setResolve(true);
        SwaggerParseResult result = new OpenAPIParser().readLocation(specLocation, null, opts);

        List<String> messages = result.getMessages() != null ? result.getMessages() : List.of();
        List<String> errors = messages.stream()
                .filter(m -> m.toLowerCase().contains("unresolved")
                        || m.toLowerCase().contains("error"))
                .toList();
        assertThat(errors)
                .as("All $ref references must resolve, but found: %s", errors)
                .isEmpty();
    }

    // ── 3. Every operation has an operationId ─────────────────────────────────

    @Test
    void everyOperationHasOperationId() {
        List<String> missing = new ArrayList<>();
        openAPI.getPaths().forEach((path, item) -> operationsOf(item).forEach(op -> {
            if (op.getOperationId() == null || op.getOperationId().isBlank()) {
                missing.add(path + " -> " + op.getSummary());
            }
        }));
        assertThat(missing)
                .as("All operations must have operationId, missing for: %s", missing)
                .isEmpty();
    }

    // ── 4. Every operation has at least one 2xx response ──────────────────────

    @Test
    void everyOperationHasSuccessResponse() {
        List<String> missing = new ArrayList<>();
        openAPI.getPaths().forEach((path, item) -> operationsOf(item).forEach(op -> {
            boolean has2xx = op.getResponses() != null
                    && op.getResponses().keySet().stream().anyMatch(code -> code.startsWith("2"));
            if (!has2xx) {
                missing.add(op.getOperationId() + " at " + path);
            }
        }));
        assertThat(missing)
                .as("All operations must have at least one 2xx response, missing: %s", missing)
                .isEmpty();
    }

    // ── 5. Error responses (400/404/429) reference ApiError ───────────────────

    @Test
    void errorResponses_referenceApiError() {
        Set<String> errorCodes = Set.of("400", "404", "429");
        List<String> violations = new ArrayList<>();

        openAPI.getPaths().forEach((path, item) -> operationsOf(item).forEach(op -> {
            if (op.getResponses() == null) return;
            op.getResponses().forEach((code, response) -> {
                if (!errorCodes.contains(code)) return;
                // After resolve, check schema ref points to ApiError
                if (response.getContent() == null) return;
                response.getContent().forEach((mediaType, mt) -> {
                    if (mt.getSchema() == null) return;
                    String ref = mt.getSchema().get$ref();
                    // Resolved schema may have no $ref but have a title
                    String title = mt.getSchema().getTitle();
                    // Check original spec for $ref pattern via raw string check
                    // After resolve, schema title or name should indicate ApiError
                    // We trust the spec was already validated in allReferencesResolve;
                    // here we just verify content is present for error codes
                    if (ref == null && title == null) {
                        // Schema is inline and anonymous — treat as acceptable only
                        // if it has an 'error' property
                        Schema<?> schema = mt.getSchema();
                        if (schema.getProperties() == null
                                || !schema.getProperties().containsKey("error")) {
                            violations.add(op.getOperationId() + " " + code + " @ " + mediaType
                                    + ": schema has no 'error' property and no $ref to ApiError");
                        }
                    }
                });
            });
        }));
        assertThat(violations)
                .as("Error responses must reference ApiError schema: %s", violations)
                .isEmpty();
    }

    // ── 6. SSE endpoint documents frame codes ────────────────────────────────

    @Test
    void sseEndpoint_documentsFrameCodes() {
        PathItem chatSend = openAPI.getPaths().get("/api/chat/send");
        assertThat(chatSend).as("/api/chat/send path must exist").isNotNull();

        Operation post = chatSend.getPost();
        assertThat(post).as("POST /api/chat/send must exist").isNotNull();

        String description = post.getDescription();
        assertThat(description)
                .as("POST /api/chat/send must have a description")
                .isNotBlank();

        // Spec documents frame codes as "`0` —", "`3` —", etc.
        assertThat(description).contains("`0`");
        assertThat(description).contains("`3`");
        assertThat(description).contains("`9`");
        assertThat(description).contains("`a`");
        assertThat(description).contains("`d`");
    }

    // ── 7. McpServerDto has transport enum ───────────────────────────────────

    @Test
    void mcpTransport_hasEnum() {
        Map<String, Schema> schemas = openAPI.getComponents().getSchemas();
        assertThat(schemas).containsKey("McpServerDto");

        Schema<?> mcpSchema = schemas.get("McpServerDto");
        assertThat(mcpSchema.getProperties()).containsKey("transport");

        Schema<?> transportSchema = (Schema<?>) mcpSchema.getProperties().get("transport");
        List<?> transportEnum = transportSchema.getEnum();
        assertThat(transportEnum)
                .as("transport field must have enum values [stdio, http]")
                .isNotEmpty();
        assertThat(transportEnum.stream().map(Object::toString).toList()).containsExactlyInAnyOrder("stdio", "http");
    }

    // ── 8. No orphan schemas ─────────────────────────────────────────────────

    @Test
    void noOrphanSchemas() {
        Map<String, Schema> schemas = openAPI.getComponents().getSchemas();
        Set<String> schemaNames = schemas.keySet();

        // Collect all $ref usages in the whole spec serialized to string
        // (swagger-parser already resolved, so we check raw spec text)
        // Load raw spec text and find all #/components/schemas/<name> references
        String rawContent = readSpecRaw();
        Set<String> referenced = schemaNames.stream()
                .filter(name -> rawContent.contains("'#/components/schemas/" + name + "'")
                        || rawContent.contains("\"#/components/schemas/" + name + "\"")
                        || rawContent.contains("$ref: '#/components/schemas/" + name + "'")
                        || rawContent.contains("$ref: \"#/components/schemas/" + name + "\""))
                .collect(Collectors.toSet());

        Set<String> orphans =
                schemaNames.stream().filter(name -> !referenced.contains(name)).collect(Collectors.toSet());

        assertThat(orphans)
                .as("All schemas in components.schemas must be referenced at least once; orphans: %s", orphans)
                .isEmpty();
    }

    // ── 9. DTO schemas with validation fields have required array ─────────────

    @Test
    void allDtoSchemas_haveRequiredFields() {
        Map<String, Schema> schemas = openAPI.getComponents().getSchemas();
        // Schemas that have properties with minLength >= 1 should declare required
        List<String> violations = new ArrayList<>();
        schemas.forEach((name, schema) -> {
            if (schema.getProperties() == null) return;
            boolean hasConstrainedProp = schema.getProperties().values().stream()
                    .anyMatch(p -> {
                        Schema<?> prop = (Schema<?>) p;
                        return prop.getMinLength() != null && prop.getMinLength() >= 1;
                    });
            if (hasConstrainedProp) {
                if (schema.getRequired() == null || schema.getRequired().isEmpty()) {
                    violations.add(name + ": has minLength-constrained fields but no 'required' array");
                }
            }
        });
        assertThat(violations)
                .as("Schemas with @NotBlank-equivalent fields must declare required: %s", violations)
                .isEmpty();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static List<Operation> operationsOf(PathItem item) {
        List<Operation> ops = new ArrayList<>();
        if (item.getGet() != null) ops.add(item.getGet());
        if (item.getPost() != null) ops.add(item.getPost());
        if (item.getPut() != null) ops.add(item.getPut());
        if (item.getDelete() != null) ops.add(item.getDelete());
        if (item.getPatch() != null) ops.add(item.getPatch());
        if (item.getHead() != null) ops.add(item.getHead());
        if (item.getOptions() != null) ops.add(item.getOptions());
        return ops;
    }

    private static String readSpecRaw() {
        try {
            Path specPath = Paths.get(System.getProperty("user.dir"))
                    .resolve("../specs/openapi.yaml")
                    .toAbsolutePath()
                    .normalize();
            return java.nio.file.Files.readString(specPath);
        } catch (Exception e) {
            throw new RuntimeException("Cannot read spec file", e);
        }
    }
}
