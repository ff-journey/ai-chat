package ff.pro.aichatali.common;

/**
 * Unified output wrapper for all PluggableTool implementations.
 * <p>
 * status: "ok" | "error"
 * content: result text or error message
 */
public record ToolResult(String status, String content) {

    public static ToolResult ok(String content) {
        return new ToolResult("ok", content);
    }

    public static ToolResult error(String message) {
        return new ToolResult("error", message);
    }
}
