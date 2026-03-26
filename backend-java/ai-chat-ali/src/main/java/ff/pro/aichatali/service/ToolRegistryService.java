package ff.pro.aichatali.service;

import ff.pro.aichatali.tool.PluggableTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Collects all PluggableTool beans registered in the application context
 * and exposes them to the agent builder.
 * To enable/disable a tool, set tools.<name>.enabled=true/false in application.yml.
 */
@Service
@Slf4j
public class ToolRegistryService {

    @Autowired(required = false)
    private List<PluggableTool> tools = List.of();

    public List<ToolCallback> getToolCallbacks() {
        List<String> names = tools.stream().map(PluggableTool::name).toList();
        log.info("Active tools: {}", names);
        return tools.stream().map(PluggableTool::toolCallback).toList();
    }

    public List<String> getToolNames() {
        return tools.stream().map(PluggableTool::name).toList();
    }

    public List<PluggableTool> getTools() {
        return tools;
    }
}
