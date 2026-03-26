package ff.pro.aichatali.controller;

import ff.pro.aichatali.service.ToolRegistryService;
import ff.pro.aichatali.tool.PluggableTool;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * GET /api/tools — returns the list of currently registered tools.
 */
@RestController
@RequestMapping("/api/tools")
@RequiredArgsConstructor
public class ToolController {

    private final ToolRegistryService toolRegistryService;

    @GetMapping
    public ResponseEntity<List<Map<String, String>>> listTools() {
        List<Map<String, String>> tools = toolRegistryService.getTools().stream()
                .map(t -> Map.of("name", t.name(), "description", t.description()))
                .toList();
        return ResponseEntity.ok(tools);
    }
}
