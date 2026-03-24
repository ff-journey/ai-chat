package ff.pro.aichatali.common;


import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class HierarchicalDocSplitter {
    private static final int MIN_CHUNK_RATIO = 3;   // 最小 chunk = size / 3
    private static final String SEPARATORS = "。！？；\n";

    public record HierarchyResult(
            List<Document> l1,
            List<Document> l2,
            List<Document> l3
    ) {}

    public HierarchyResult split(Document source, String sourceId) {
//        String sourceId = UUID.randomUUID().toString();
        if (source.getText()==null) {
            return new HierarchyResult(List.of(), List.of(), List.of());
        }

        // L1: 大段，约 1200 字
        List<Document> l1 = splitText(source.getText(), 1200, 200, sourceId, null, 1);
        List<Document> l2 = new ArrayList<>();
        List<Document> l3 = new ArrayList<>();

        for (Document d1 : l1) {
            // L2: 段落，约 600 字
            List<Document> l2s = splitText(d1.getText(), 600, 100, sourceId, d1.getId(), 2);
            l2.addAll(l2s);
            for (Document d2 : l2s) {
                // L3: 叶子，约 300 字 → 存 Milvus
                l3.addAll(splitText(d2.getText(), 300, 50, sourceId, d2.getId(), 3));
            }
        }

        return new HierarchyResult(l1, l2, l3);
    }

    private List<Document> splitText(String text, int size, int overlap,
                                     String sourceId, String parentId, int level) {
        List<Document> chunks = new ArrayList<>();
        int minSize = size / MIN_CHUNK_RATIO;
        int start = 0;
        while (start < text.length()) {
            int hardEnd = Math.min(start + size, text.length());
            // 规则切割：尝试找语义边界
            int end = hardEnd;
            if (hardEnd < text.length()) {
                int boundary = findBoundary(text, start, hardEnd);
                if (boundary > start) end = boundary;
            }
            String chunkText = text.substring(start, end);
            if (chunkText.length() >= minSize) {
                Map<String, Object> meta = new HashMap<>();
                meta.put("chunk_level", level);
                meta.put("source_id", sourceId);
                if (parentId != null) meta.put("parent_id", parentId);
                chunks.add(new Document(chunkText, meta));
            }
            if (end == text.length()) break;
            start += (size - overlap);
        }
        return chunks;
    }

    /**
     * 规则切割：在 end 附近向前找最近的语义边界
     */
    private int findBoundary(String text, int start, int end) {
        // 从 end 向前扫描，最多回退 size/4
        int scanLimit = Math.max(start, end - (end - start) / 4);
        for (int i = end - 1; i >= scanLimit; i--) {
            if (SEPARATORS.indexOf(text.charAt(i)) >= 0) {
                return i + 1; // 边界后一位作为 end（包含标点）
            }
        }
        return -1; // 找不到，fallback 硬截断
    }
}
