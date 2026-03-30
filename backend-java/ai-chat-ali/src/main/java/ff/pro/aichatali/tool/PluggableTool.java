package ff.pro.aichatali.tool;

import org.springframework.ai.tool.ToolCallback;

import java.util.List;

/**
 * Marker interface for atomized tools.
 * Each implementation is an independent Spring @Component.
 * ToolRegistryService collects all beans and feeds them to the agent.
 */
public interface PluggableTool {
    String getName();

    String getTitle();
    String getDescription();
    ToolCallback getToolCallback();

    default String getToolIcon(){ return "fa-puzzle-piece";}
    /**
     * Names of tools that cannot be active at the same time as this tool.
     * Returned by /api/tools so the frontend can enforce mutual exclusion in the UI.
     */

    default List<String> getMutuallyExclusiveWith() { return List.of(); }

}
