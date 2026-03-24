package ff.pro.aichatali.service.rag;

import com.huaban.analysis.jieba.JiebaSegmenter;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author journey
 * @date 2026/3/24
 **/
@Service
public class BM25Service {
    private final List<Document> indexedDocs = new ArrayList<>();
    private final List<List<String>> tokenizedDocs = new ArrayList<>();

    private static final double K1 = 1.5;
    private static final double B = 0.75;

    public synchronized void addDocuments(List<Document> docs) {
        for (Document doc : docs) {
            indexedDocs.add(doc);
            tokenizedDocs.add(tokenize(doc.getText()==null ? "" : doc.getText()));
        }
    }
    private JiebaSegmenter jiebaSegmenter = new JiebaSegmenter();

    public List<Document> search(String query, int topK) {
        List<String> queryTokens = tokenize(query);
        // 文档平均长度, 用于bm25长度归一化
        double avgDocLen = tokenizedDocs.stream()
                .mapToInt(List::size).average().orElse(1.0);

        List<Map.Entry<Integer, Double>> scored = new ArrayList<>();
        for (int i = 0; i < tokenizedDocs.size(); i++) {
            double score = bm25Score(queryTokens, tokenizedDocs.get(i), avgDocLen);
            scored.add(Map.entry(i, score));
        }

        return scored.stream()
                .filter(e -> e.getValue() > 0)
                .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
                .limit(topK)
                .map(e -> indexedDocs.get(e.getKey()))
                .toList();
    }

    private double bm25Score(List<String> query, List<String> doc, double avgDocLen) {
        Map<String, Long> termFreq = doc.stream()
                .collect(Collectors.groupingBy(t -> t, Collectors.counting()));
        double docLen = doc.size();
        double score = 0;

        for (String term : query) {
            long tf = termFreq.getOrDefault(term, 0L);
            if (tf == 0) continue;
            // 简化版 IDF（正式版需要全局文档频率）
            double idf = Math.log(1 + (indexedDocs.size() + 0.5) / 1.0);
            double numerator = tf * (K1 + 1);
            double denominator = tf + K1 * (1 - B + B * docLen / avgDocLen);
            score += idf * numerator / denominator;
        }
        return score;
    }

    // 简单分词：按空格+标点切分（中文后续可接 jieba4j）
//    private List<String> tokenize(String text) {
//        return Arrays.stream(text.toLowerCase().split("[\\s\\p{Punct}]+"))
//                .filter(t -> !t.isBlank())
//                .toList();
//    }
    private List<String> tokenize(String text) {
        return jiebaSegmenter.process(text, JiebaSegmenter.SegMode.SEARCH)
                .stream()
                .map(token -> token.word.toLowerCase())
                .filter(w -> w.length() > 1)  // 过滤单字停用词
                .toList();
    }
}
