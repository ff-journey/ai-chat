package ff.pro.aichatali.common;


import org.jetbrains.annotations.NotNull;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class HierarchicalDocSplitter {
    private static final int MIN_CHUNK_RATIO = 3;   // 最小 chunk = size / 3
    // 中文句末标点
    static final Set<Character> sentenceEnd = Set.of('。', '！', '？', '；', '\n');
    // 中文句中停顿
    static final Set<Character> pauseMark = Set.of('，', '、', '：');

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

        // L1: 大段，约 1024 字
        List<Document> l1 = splitText(source.getText(), 2048, 0, sourceId, null, 1);
        List<Document> l2 = new ArrayList<>();
        List<Document> l3 = new ArrayList<>();

        for (Document d1 : l1) {
            // L2: 段落，约 256 字
            List<Document> l2s = splitText(d1.getText(), 512, 0, sourceId, d1.getId(), 2);
            l2.addAll(l2s);
            for (Document d2 : l2s) {
                // L3: 叶子，约 64 字 → 存 Milvus
                l3.addAll(splitL3SemanticAware(d2.getText(), 128, 10, sourceId, d2.getId()));
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
            // 规则切割：父层级直接硬切, 子层级找最近的语义边界
            int end = hardEnd;
            String chunkText = text.substring(start, end);
            boolean isLastChunk = (end == text.length());
            // 最后一个chunk不受minSize限制，避免短文档/短尾部被丢弃
            if (isLastChunk ||chunkText.length() >= minSize) {
                Document doc = toDoc(sourceId, parentId, level, chunkText);
                chunks.add(doc);
            }
            if (end == text.length()) break;
            start += (size - overlap);
        }
        return chunks;
    }

    @NotNull
    private static Document toDoc(String sourceId, String parentId, int level, String chunkText) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("chunk_level", level);
        meta.put("source_id", sourceId);
        if (parentId != null) meta.put("parent_id", parentId);
        Document e = new Document(chunkText, meta);
        return e;
    }


    private List<Document> splitL3SemanticAware(String text, int targetSize, int overlapSize, String sourceId, String parentId) {

        List<Document> chunks = new ArrayList<>();
        int start = 0;

        while (start < text.length()) {
            int end = Math.min(start + targetSize, text.length());
            if (end == text.length()) {
                chunks.add(toDoc(sourceId, parentId, 3, text.substring(start)));
                break;
            }

            int lookback = targetSize / 5;
            int cutAt = findSemanticCut(text, end, lookback);

            chunks.add(toDoc(sourceId, parentId, 3, text.substring(start, cutAt)));

            // overlap：从cutAt往前推overlapSize，再找语义边界作为下一个start
            int overlapStart = Math.max(start + 1, cutAt - overlapSize);
            start = findSemanticCut(text, overlapStart, overlapSize / 3);
        }
        return chunks;
    }

    // 抽出来复用
    private int findSemanticCut(String text, int end, int lookback) {
        for (int i = end; i >= Math.max(0, end - lookback); i--) {
            if (sentenceEnd.contains(text.charAt(i))) return i + 1;
        }
        for (int i = end; i >= Math.max(0, end - lookback); i--) {
            if (pauseMark.contains(text.charAt(i))) return i + 1;
        }
        return end;
    }
}
