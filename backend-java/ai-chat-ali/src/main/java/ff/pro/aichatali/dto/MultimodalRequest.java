package ff.pro.aichatali.dto;

import lombok.Data;

import java.util.List;

@Data
public class MultimodalRequest {

    private String message;

    private String threadId = "default";

    private List<MediaItem> media;

}
