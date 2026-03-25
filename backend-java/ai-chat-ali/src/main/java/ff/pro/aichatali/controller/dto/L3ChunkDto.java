package ff.pro.aichatali.controller.dto;

import ff.pro.aichatali.repo.RagChunkPo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.ai.document.Document;

/**
 * @author journey
 * @date 2026/3/25
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class L3ChunkDto {
    RagChunkPo ragChunk;
    Document document;
}
