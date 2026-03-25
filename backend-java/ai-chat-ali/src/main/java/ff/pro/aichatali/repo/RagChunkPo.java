package ff.pro.aichatali.repo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.EmbeddingUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RagChunkPo {
    private String docId;
    private String content;
    private String sourceId;
    private String parentId;
    private int chunkLevel;
//    private Map<String, String> metadata;
    private List<Float> embedding;
    private Document document;


    public RagChunkPo(Document chunk, List<Float> embedding) {
        this.docId = chunk.getId();
        this.content = chunk.getText();
        this.sourceId = ((String) chunk.getMetadata().get("source_id"));
        this.parentId = ((String) chunk.getMetadata().get("parent_id"));
        this.chunkLevel = ((Integer) chunk.getMetadata().get("chunk_level"));
        this.embedding = embedding;
        this.document = chunk;
    }
}
