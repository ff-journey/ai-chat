package ff.pro.aichatali.tool;

import ff.pro.aichatali.service.rag.BM25Service;
import ff.pro.aichatali.service.rag.RRFMerger;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * @author journey
 * @date 2026/3/16
 **/
public class MemoryHybridRetrieverTool implements BiFunction<MemoryHybridRetrieverTool.Input, ToolContext, Map<String, String>> {

    public record Input(String query) {}

    VectorStore vectorStore;
    BM25Service bm25Service;
    RRFMerger rrfMerger;





    private Map<String, String> memoryHybridRetriever(String query){
        List<Document> denseDoc = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(5)
                        .similarityThreshold(0.5)
                        .build()
        );

        List<Document> sparseDoc = bm25Service.search(query, 5);

        List<Document> mergedDoc = rrfMerger.merge(denseDoc, sparseDoc, 5);
        String content = mergedDoc.stream().map(Document::getText).collect(Collectors.joining("\n\n---\n\n"));
        return Map.of("result", content);
    }

    @Override
    public Map<String, String> apply(Input query, ToolContext toolContext) {
        try {
            return memoryHybridRetriever(query.query());
        } catch (Exception e){
            return Map.of("error", "查询失败, 知识库服务错误");
        }
    }

}
