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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author journey
 * @date 2026/3/16
 **/
@Service
@RequiredArgsConstructor
@Slf4j
public class MemoryHybridRetrieverService implements BiFunction<MemoryHybridRetrieverService.Input, ToolContext, Map<String, String>> {

    public record Input(String query) {
    }

    final RagChunkMapper ragChunkMapper;
    final BM25Service bm25Service;
    final RRFMerger rrfMerger;
    final EmbeddingService embeddingService;
    final RerankService rerankService;
    final GradingService gradingService;
    final QueryRewriter queryRewriter;


    private Map<String, String> memoryHybridRetriever(String query) {

        List<Document> reranked = hybridRetrieverAndRrfAndRerank(query, 0);
//        reranked = gradingService.filterRelevant(query, reranked);
        if (reranked.size() < 2) {
            // 两种策略都试，取 union
            String stepBackQuery = queryRewriter.stepBack(query);
//            String hydeDoc = queryRewriter.hyde(query);

            reranked = hybridRetrieverAndRrfAndRerank(stepBackQuery, 0);  // 用改写后的 query 检索
//            List<Document> r2 = hybridRetrieverAndRrfAndRerank(hydeDoc, 0);
            // 合并去重，不再走 grading（避免死循环）
//            reranked = Stream.concat(r1.stream(), r2.stream())
//                    .collect(Collectors.toMap(
//                            d -> d.getMetadata().get("chunk_id").toString(),
//                            d -> d, (a, b) -> a))
//                    .values().stream().limit(5).toList();
        }
        //升级合并, 超过阈值则升级为上层块
        List<Document> mergeChunks = autoMerge(reranked, 2);
        List<Document> finalChunks = parentFirst(mergeChunks, 6);
        String content = finalChunks.stream().map(Document::getText).filter(StringUtils::isNotBlank).collect(Collectors.joining("\n\n---\n\n"));
        return Map.of("result", StringUtils.isNotBlank(content) ? content : "未查询到相关内容");
    }

    private List<Document> hybridRetrieverAndRrfAndRerank(String query, float rerankScore) {
        List<RagChunkPo> denseChunks = ragChunkMapper.searchByVector(embeddingService.embed(query), 20, null);

        List<Document> sparseDoc = bm25Service.search(query, 20);

        List<Document> denseDoc = denseChunks.stream().map(RagChunkPo::getDocument).toList();
        // rrf融合
        List<Document> rrfDoc = rrfMerger.merge(denseDoc, sparseDoc, 20);
        // Rerank 精排（20→10），在 auto-merge 之前
        List<Document> reranked = rerankService.rerank(query, rrfDoc, 10, rerankScore);
        return reranked;
    }

    private int getLevel(Document d) {
        return Integer.parseInt(d.getMetadata().get("chunk_level").toString());
    }

    private List<Document> parentFirst(List<Document> merged, int limit) {
        // 分离：升级的父块 vs 未升级的 L3
        List<Document> upgradedChunks = merged.stream()
                .filter(d -> getLevel(d) < 3)   // L1 或 L2，说明发生了升级
                .toList();

        List<Document> leafChunks = merged.stream()
                .filter(d -> getLevel(d) == 3)  // 未触发升级的 L3
                .toList();

        // 升级块全部保留，剩余槽位填 L3（总上限6）
        int remaining = Math.max(0, limit - upgradedChunks.size());
        List<Document> finalDocs = Stream.concat(
                upgradedChunks.stream(),
                leafChunks.stream().limit(remaining)
        ).toList();
        return finalDocs;
    }

    @Override
    public Map<String, String> apply(Input query, ToolContext toolContext) {
        try {
            return memoryHybridRetriever(query.query());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return Map.of("error", "查询失败, 知识库服务错误");
        }
    }

    public List<Document> autoMerge(List<Document> leafChunks, int threshold) {

        List<Document> current = new ArrayList<>(leafChunks);

        // 两轮迭代：L3→L2，L2→L1
        for (int round = 0; round < 2; round++) {
            current = mergeSingleLevel(current, threshold);
        }

        // 最终去重（按 chunk_id）
        return current.stream()
                .collect(Collectors.toMap(
                        Document::getId,
                        d -> d,
                        (a, b) -> a
                ))
                .values()
                .stream()
                .toList();
    }

    private List<Document> mergeSingleLevel(List<Document> chunks, int threshold) {
        // 分两组：有 parent_id 的参与合并，没有的（已是 L1）直接保留
        Map<Boolean, List<Document>> partitioned = chunks.stream()
                .collect(Collectors.partitioningBy(
                        d -> d.getMetadata().containsKey("parent_id")
                                && d.getMetadata().get("parent_id") != null
                ));

        List<Document> noParent = partitioned.get(false); // L1，不再升级
        List<Document> hasParent = partitioned.get(true);

        // 按 parent_id 分组
        Map<String, List<Document>> grouped = hasParent.stream()
                .collect(Collectors.groupingBy(
                        d -> d.getMetadata().get("parent_id").toString()
                ));
        List<Document> duplicateL3 = new ArrayList<>();
        List<Document> result = new ArrayList<>(noParent);
        for (var entry : grouped.entrySet()) {
            String parentId = entry.getKey();

            if (entry.getValue().size() >= threshold) {
                Optional<Document> parent = ragChunkMapper.getParentChunk(parentId);
                parent.ifPresent(result::add); // 升级
                duplicateL3.addAll(entry.getValue());
            }
        }
        chunks.removeAll(duplicateL3);
        chunks.removeAll(noParent);
        result.addAll(chunks);
        return result;
    }
}
