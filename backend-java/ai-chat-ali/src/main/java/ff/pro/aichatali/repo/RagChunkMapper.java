package ff.pro.aichatali.repo;

import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Optional;

public interface RagChunkMapper {
    void batchInsert(List<RagChunkPo> chunks);
    List<RagChunkPo> searchByVector(List<Float> vector, int topK, String filter);
    List<RagChunkPo> findBySourceId(String sourceId);
    void deleteBySourceId(String sourceId);

    void insertParentChunk(String id, Document doc);
    Optional<Document> getParentChunk(String id);
    List<Document> getParentChunk(List<String> ids);
}