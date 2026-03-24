package ff.pro.aichatali.service.rag;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author journey
 * @date 2026/3/24
 **/
@Component
public class RRFMerger {
    private static final int K = 60; // RRF 标准常数

    public List<Document> merge(List<Document> denseResults,
                                List<Document> sparseResults,
                                int topK) {
        Map<String, Double> scores = new HashMap<>();
        Map<String, Document> docMap = new HashMap<>();

        // Dense 结果：按排名计分 1/(K + rank)
        for (int i = 0; i < denseResults.size(); i++) {
            Document doc = denseResults.get(i);
            String id = doc.getId();
            scores.merge(id, 1.0 / (K + i + 1), Double::sum);
            docMap.put(id, doc);
        }

        // BM25 结果：同样方式计分
        for (int i = 0; i < sparseResults.size(); i++) {
            Document doc = sparseResults.get(i);
            String id = doc.getId();
            scores.merge(id, 1.0 / (K + i + 1), Double::sum);
            docMap.put(id, doc);
        }

        // 按总分排序，取 topK
        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(e -> docMap.get(e.getKey()))
                .toList();
    }

}
