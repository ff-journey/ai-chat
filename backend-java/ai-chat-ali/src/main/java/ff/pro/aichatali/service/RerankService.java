package ff.pro.aichatali.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class RerankService {

    @Value("${reranker.api-key}")
    private String rerankerApiKey;
    @Value("${reranker.base-url}")
    private String rerankerApiUrl;

    private final RestClient restClient = RestClient.create();

    public List<Document> rerank(String query, List<Document> docs, int topK, float rerankScore) {
        if (docs.isEmpty()) return docs;

        // 构造请求体
        var body = Map.of(
                "model", "jina-reranker-v2-base-multilingual",
                "query", query,
                "documents", docs.stream().map(Document::getText).toList(),
                "top_n", Math.min(topK, docs.size())
        );

        RerankResponse response = restClient.post()
                .uri(rerankerApiUrl)
                .header("Authorization", "Bearer " + rerankerApiKey)
                .header("Content-Type", "application/json")
                .body(body)
                .retrieve()
                .body(RerankResponse.class);

        if (response == null || response.results() == null) return docs;

        // 按 relevance_score 降序，用 index 映射回原始 Document
        return response.results().stream()
                .sorted(Comparator.comparingDouble(RerankResult::relevanceScore).reversed())
                .filter(r->r.relevanceScore()>rerankScore)
                .map(r -> docs.get(r.index()))
                .toList();
    }

    // 响应结构
    record RerankResponse(List<RerankResult> results) {
    }

    record RerankResult(int index, double relevanceScore) {
        @JsonProperty("relevance_score")
        public double relevanceScore() {
            return relevanceScore;
        }
    }
}