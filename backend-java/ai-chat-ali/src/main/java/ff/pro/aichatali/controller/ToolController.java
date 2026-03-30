package ff.pro.aichatali.controller;

import ff.pro.aichatali.controller.dto.ToolDto;
import ff.pro.aichatali.service.ToolRegistryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GET /api/tools — returns the list of currently registered tools with mutual exclusion rules.
 *
 * Response: [{name, description, mutuallyExclusiveWith: [toolName, ...]}, ...]
 */
@RestController
@RequestMapping("/api/tools")
@RequiredArgsConstructor
public class ToolController {

    private final ToolRegistryService toolRegistryService;

    @GetMapping
    public ResponseEntity<List<ToolDto>> listTools() {
        List<ToolDto> tools = toolRegistryService.getSupportTools();
        return ResponseEntity.ok(tools);
    }
}
