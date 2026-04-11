package ai.javaclaw.api.chat.a2a;

import java.util.List;
import java.util.Map;

/**
 * A2A JSON-RPC request/response DTOs following the A2A protocol specification.
 */
public final class A2aJsonRpc {

    private A2aJsonRpc() {}

    // --- JSON-RPC envelope ---

    public record JsonRpcRequest(String jsonrpc, String method, String id, Map<String, Object> params) {
        public static final String VERSION = "2.0";
    }

    public record JsonRpcResponse(String jsonrpc, String id, Object result, JsonRpcError error) {
        public static JsonRpcResponse success(String id, Object result) {
            return new JsonRpcResponse("2.0", id, result, null);
        }

        public static JsonRpcResponse error(String id, int code, String message) {
            return new JsonRpcResponse("2.0", id, null, new JsonRpcError(code, message, null));
        }
    }

    public record JsonRpcError(int code, String message, Object data) {}

    // --- A2A Task model ---

    public enum TaskState {
        submitted,
        working,
        input_required,
        completed,
        failed,
        canceled
    }

    public record A2aTask(String id, TaskState status, List<A2aMessage> history, List<A2aArtifact> artifacts) {}

    public record A2aMessage(String role, List<A2aPart> parts) {}

    public record A2aPart(String type, String text) {
        public static A2aPart text(String text) {
            return new A2aPart("text", text);
        }
    }

    public record A2aArtifact(String name, List<A2aPart> parts) {}

    // --- JSON-RPC error codes ---
    public static final int PARSE_ERROR = -32700;
    public static final int INVALID_REQUEST = -32600;
    public static final int METHOD_NOT_FOUND = -32601;
    public static final int INVALID_PARAMS = -32602;
    public static final int TASK_NOT_FOUND = -32001;
    public static final int TASK_NOT_CANCELABLE = -32002;
}
