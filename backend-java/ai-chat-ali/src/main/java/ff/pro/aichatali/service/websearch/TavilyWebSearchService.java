package ff.pro.aichatali.service.websearch;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tavily 联网搜索实现。
 * Tavily 专为 RAG 设计，返回干净的文本片段，可直接用于检索融合。
 */
@Service
@Slf4j
public class TavilyWebSearchService implements WebSearchPort {

    private static final String TAVILY_API_URL = "https://api.tavily.com/search";

    @Value("${tavily.api-key}")
    private String apiKey;

    private final RestClient restClient = RestClient.create();

    @Override
    public List<Document> search(String query, int topK) {
        try {
//            return Collections.emptyList();
            var body = Map.of(
                    "query", query,
                    "max_results", topK,
                    "search_depth", "basic",
                    "include_answer", false
            );

            TavilyResponse response = restClient.post()
                    .uri(TAVILY_API_URL)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(TavilyResponse.class);

            if (response == null || response.results() == null) {
                return Collections.emptyList();
            }

            return response.results().stream()
                    .map(r -> Document.builder()
                            .id(UUID.randomUUID().toString())
                            .text(r.title() + "\n" + r.content())
                            .metadata(Map.of(
                                    "source", "web",
                                    "url", r.url() != null ? r.url() : "",
                                    "title", r.title() != null ? r.title() : "",
                                    "chunk_level", "3"   // 兼容 autoMerge / parentFirst
                            ))
                            .build())
                    .toList();

        } catch (Exception e) {
            log.warn("Tavily search failed for query='{}': {}", query, e.getMessage());
            return Collections.emptyList();
        }
    }

    // ---- Tavily 响应 DTO ----

    record TavilyResponse(List<TavilyResult> results) {}

    record TavilyResult(
            String title,
            String url,
            String content,
            double score,
            @JsonProperty("published_date") String publishedDate
    ) {}
}
