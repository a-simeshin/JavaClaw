package ai.javaclaw.api.chat.a2a;

import ai.javaclaw.api.chat.a2a.A2aAgentCard.A2aCapabilities;
import ai.javaclaw.api.chat.a2a.A2aAgentCard.A2aSkill;
import ai.javaclaw.api.chat.a2a.A2aJsonRpc.JsonRpcRequest;
import ai.javaclaw.api.chat.a2a.A2aJsonRpc.JsonRpcResponse;
import ai.javaclaw.users.UserResolver;
import java.security.Principal;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * A2A protocol controller — Agent Card discovery and JSON-RPC task endpoint.
 *
 * <ul>
 *   <li>{@code GET /.well-known/agent.json} — public Agent Card</li>
 *   <li>{@code POST /api/a2a} — authenticated JSON-RPC endpoint</li>
 * </ul>
 */
@RestController
public class A2aController {

    private final A2aService a2aService;
    private final UserResolver userResolver;

    public A2aController(A2aService a2aService, UserResolver userResolver) {
        this.a2aService = a2aService;
        this.userResolver = userResolver;
    }

    @GetMapping(value = "/.well-known/agent.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public A2aAgentCard agentCard() {
        return new A2aAgentCard(
                "JavaClaw",
                "Enterprise AI agent platform — accepts tasks via A2A protocol",
                "/api/a2a",
                "1.0.0",
                new A2aCapabilities(false, false, true),
                List.of(
                        new A2aSkill(
                                "general", "General Task", "Execute general-purpose AI tasks", List.of("ai", "task")),
                        new A2aSkill(
                                "file-ops",
                                "File Operations",
                                "Read, write, list, delete virtual files",
                                List.of("files")),
                        new A2aSkill(
                                "skills-mgmt",
                                "Skills Management",
                                "List and manage agent skills",
                                List.of("skills"))));
    }

    @PostMapping(
            value = "/api/a2a",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonRpcResponse> handle(@RequestBody JsonRpcRequest request, Principal principal) {
        String userId = resolveUserId(principal);
        JsonRpcResponse response = a2aService.dispatch(request, userId);
        return ResponseEntity.ok(response);
    }

    private String resolveUserId(Principal principal) {
        if (principal == null) {
            return null;
        }
        try {
            return userResolver.resolveUserId(principal.getName());
        } catch (Exception e) {
            return null;
        }
    }
}
