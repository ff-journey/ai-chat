package ff.pro.aichatali.config;

/**
 * SSE event pushed to the observability channel (/sse/{threadId}).
 *
 * type values: AGENT_THINKING | TOOL_START | TOOL_END | AGENT_STEP_END | DONE
 */
public record AgentEvent(
        String type,
        String agentName,
        String toolName,
        String input,
        String output,
        boolean done,
        String parentNode,
        String currentNode
) {
    public static AgentEvent thinking(String agentName, String input, String parentNode) {
        return new AgentEvent("AGENT_THINKING", agentName, null, input, null, false, parentNode, agentName);
    }

    public static AgentEvent toolStart(String toolName, String input, String parentNode) {
        return new AgentEvent("TOOL_START", null, toolName, input, null, false, parentNode, toolName);
    }

    public static AgentEvent toolEnd(String toolName, String output, String parentNode) {
        return new AgentEvent("TOOL_END", null, toolName, null, output, true, parentNode, toolName);
    }

    public static AgentEvent stepEnd(String agentName, String output, String parentNode) {
        return new AgentEvent("AGENT_STEP_END", agentName, null, null, output, true, parentNode, agentName);
    }

    public static AgentEvent allDone() {
        return new AgentEvent("DONE", null, null, null, null, true, null, null);
    }
}
