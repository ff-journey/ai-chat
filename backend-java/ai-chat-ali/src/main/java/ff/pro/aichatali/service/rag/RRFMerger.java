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

    /**
     * 两路融合（向后兼容保留）
     */
    public List<Document> merge(List<Document> denseResults,
                                List<Document> sparseResults,
                                int topK) {
        return merge(topK, denseResults, sparseResults);
    }

    /**
     * 多路融合：支持任意数量的检索通道（Dense / BM25 / Web / ...）。
     * 每路结果按排名计分 1/(K + rank)，累加后统一排序取 topK。
     *
     * @param topK     最终保留条数
     * @param channels 各路检索结果，顺序不影响 RRF 计分
     */
    @SafeVarargs
    public final List<Document> merge(int topK, List<Document>... channels) {
        Map<String, Double> scores = new HashMap<>();
        Map<String, Document> docMap = new HashMap<>();

        for (List<Document> channel : channels) {
            for (int i = 0; i < channel.size(); i++) {
                Document doc = channel.get(i);
                String id = doc.getId();
                scores.merge(id, 1.0 / (K + i + 1), Double::sum);
                docMap.put(id, doc);
            }
        }

        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(e -> docMap.get(e.getKey()))
                .toList();
    }

}
