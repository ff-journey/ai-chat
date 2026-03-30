package ff.pro.aichatali.controller.dto;


import lombok.Getter;

import java.util.List;

public record ToolDto(
        String name,
        String title,
        String description,
        List<String> mutuallyExclusiveWith,
        int toolFlag,
        String toolIcon
) {
}
