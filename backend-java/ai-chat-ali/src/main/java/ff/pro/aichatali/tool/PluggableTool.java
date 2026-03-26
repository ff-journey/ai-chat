package ff.pro.aichatali.tool;

import org.springframework.ai.tool.ToolCallback;

/**
 * Marker interface for atomized tools.
 * Each implementation is an independent Spring @Component.
 * ToolRegistryService collects all beans and feeds them to the agent.
 */
public interface PluggableTool {
    String name();
    String description();
    ToolCallback toolCallback();
}
