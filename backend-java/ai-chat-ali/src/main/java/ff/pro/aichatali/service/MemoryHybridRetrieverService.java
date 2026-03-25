package ff.pro.aichatali.service;

import ff.pro.aichatali.repo.RagChunkMapper;
import ff.pro.aichatali.repo.RagChunkPo;
import ff.pro.aichatali.service.rag.BM25Service;
import ff.pro.aichatali.service.rag.RRFMerger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * @author journey
 * @date 2026/3/16
 **/
@Service
@RequiredArgsConstructor
@Slf4j
public class MemoryHybridRetrieverService implements BiFunction<MemoryHybridRetrieverService.Input, ToolContext, Map<String, String>> {

    public record Input(String query) {}

    final RagChunkMapper ragChunkMapper;
    final BM25Service bm25Service;
    final RRFMerger rrfMerger;
    final EmbeddingService embeddingService;


    private Map<String, String> memoryHybridRetriever(String query){
//        List<Document> denseDoc = vectorStore.searchByVector(
//                SearchRequest.builder()
//                        .query(query)
//                        .topK(5)
//                        .similarityThreshold(0.5)
//                        .build()
//        );

        List<RagChunkPo> ragChunks = ragChunkMapper.searchByVector(embeddingService.embed(query), 5, null);

        List<Document> sparseDoc = bm25Service.search(query, 5);

        List<Document> denseDoc = ragChunks.stream().map(RagChunkPo::getDocument).toList();
        List<Document> mergedDoc = rrfMerger.merge(denseDoc, sparseDoc, 3);
//        String content = mergedDoc.stream().map(Document::getText).collect(Collectors.joining("\n\n---\n\n"));

        List<String> sourceId = mergedDoc.stream().map(it -> it.getMetadata().get("parent_id").toString()).toList();
        List<Document> parentChunk = ragChunkMapper.getParentChunk(sourceId, 2);
        String content = parentChunk.stream().map(Document::getText).filter(StringUtils::isNotBlank).collect(Collectors.joining("\n\n---\n\n"));
        return Map.of("result", StringUtils.isNotBlank(content)?content:"未查询到相关内容");
    }

    @Override
    public Map<String, String> apply(Input query, ToolContext toolContext) {
        try {
            return memoryHybridRetriever(query.query());
        } catch (Exception e){
            log.error(e.getMessage(),e);
            return Map.of("error", "查询失败, 知识库服务错误");
        }
    }

}
