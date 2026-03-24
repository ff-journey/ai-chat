package ff.pro.aichatali.repo;

import java.util.List;

public interface RagChunkMapper {
    void batchInsert(List<RagChunkPo> chunks);
    List<RagChunkPo> searchByVector(List<Float> vector, int topK, String filter);
    List<RagChunkPo> findBySourceId(String sourceId);
    void deleteBySourceId(String sourceId);
}