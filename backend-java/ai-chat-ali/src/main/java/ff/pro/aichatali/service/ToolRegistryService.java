package ff.pro.aichatali.service;

import ff.pro.aichatali.controller.dto.ToolDto;
import ff.pro.aichatali.tool.PluggableTool;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

@Service
@Slf4j
public class ToolRegistryService {
    @Lazy
    @Autowired
    List<PluggableTool> pluggableTools;


    @Getter
    private final Map<String, PluggableTool> toolMap = new ConcurrentHashMap<>();
    @Getter
    private List<ToolDto> supportTools;
    @Getter
    private List<ToolCallback> tools = List.of();

    @PostConstruct
    public void init() {
        List<String> names = pluggableTools.stream().map(PluggableTool::getName).toList();
        log.info("Active tools: {}", names);
        List<ToolCallback> toolCallbacks = new ArrayList<>();
        supportTools = IntStream.range(0, pluggableTools.size())
                .mapToObj(i -> {
                    ToolDto toolDto = new ToolDto(
                            pluggableTools.get(i).getName(),
                            pluggableTools.get(i).getTitle(),
                            pluggableTools.get(i).getDescription(),
                            pluggableTools.get(i).getMutuallyExclusiveWith(),
                            1 << i,
                            pluggableTools.get(i).getToolIcon()
                    );
                    toolMap.put(toolDto.name(), pluggableTools.get(i));
                    toolCallbacks.add(pluggableTools.get(i).getToolCallback());
                    return toolDto;
                })
                .toList();
        tools = List.copyOf(toolCallbacks);
        log.info("Support tools: {}", toolMap.keySet());
    }

    public List<ToolCallback> dynamicCallbacks(int toolFlag) {
        if (toolFlag == 0) {
            return List.of();
        }

        return IntStream.range(0, pluggableTools.size())
                .filter(i -> (toolFlag & (1 << i)) != 0)
                .mapToObj(i -> pluggableTools.get(i).getToolCallback())
                .toList();
    }
}
