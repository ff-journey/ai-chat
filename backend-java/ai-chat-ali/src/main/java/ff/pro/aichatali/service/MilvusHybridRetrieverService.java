package ff.pro.aichatali.service;

import ff.pro.aichatali.config.MilvusConfig;
import ff.pro.aichatali.repo.RagChunkMapper;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.AnnSearchReq;
import io.milvus.v2.service.vector.request.HybridSearchReq;
import io.milvus.v2.service.vector.request.data.EmbeddedText;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.request.ranker.RRFRanker;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import ff.pro.aichatali.common.ToolResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Milvus 原生混合检索服务：稠密向量 ANN + BM25 稀疏向量，通过 hybridSearch 一次调用完成检索与 RRF 融合。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MilvusHybridRetrieverService {

    public record Input(String query) {}

    private static final String COLLECTION = MilvusConfig.COLLECTION_NAME;
    private static final int SEARCH_TOP_K = 20;

    final MilvusClientV2 milvusClientV2;
    final EmbeddingService embeddingService;
    final RerankService rerankService;
    final QueryRewriter queryRewriter;
    final RagChunkMapper ragChunkMapper;

    public ToolResult retrieve(Input query) {
        try {
        List<Document> reranked = hybridSearchAndRerank(query.query(), 0.3f);
        if (reranked.size() < 3) {
            log.info("query rewrite <------------->");
            String stepBackQuery = queryRewriter.stepBack(query.query());
            reranked = hybridSearchAndRerank(stepBackQuery, 0f);
        }
        List<Document> finalChunks = parentFirst(reranked, 6);
        String content = finalChunks.stream()
                .map(Document::getText)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining("\n\n---\n\n"));
        return ToolResult.ok(StringUtils.isNotBlank(content) ? content : "未查询到相关内容");
        } catch (Exception e) {
            log.error("MilvusHybridRetrieverService error: {}", e.getMessage(), e);
            return ToolResult.error("查询失败, 知识库服务错误");
        }
    }

    private List<Document> hybridSearchAndRerank(String query, float rerankScore) {
        // 稠密向量路
        List<Float> denseVec = embeddingService.embed(query);
        AnnSearchReq denseReq = AnnSearchReq.builder()
                .vectorFieldName("embedding")
                .vectors(List.of(new FloatVec(denseVec)))
                .topK(SEARCH_TOP_K)
                .build();

        // 稀疏向量路 — 直接传文本，Milvus BM25 function 自动处理
        AnnSearchReq sparseReq = AnnSearchReq.builder()
                .vectorFieldName("sparse_embedding")
                .vectors(List.of(new EmbeddedText(query)))
                .topK(SEARCH_TOP_K)
                .build();

        // hybridSearch：Milvus 内部 RRF(k=60) 融合两路结果
        HybridSearchReq req = HybridSearchReq.builder()
                .collectionName(COLLECTION)
                .searchRequests(List.of(denseReq, sparseReq))
                .ranker(new RRFRanker(60))
                .topK(SEARCH_TOP_K)
                .outFields(List.of("doc_id", "content", "source_id", "parent_id", "chunk_level"))
                .build();

        SearchResp resp = milvusClientV2.hybridSearch(req);
        List<Document> docs = toDocuments(resp);
        log.debug("hybridSearch 返回 {} 条结果", docs.size());

        List<Document> merged = autoMerge(docs, 2);
        return rerankService.rerank(query, merged, 10, rerankScore);
    }

    private List<Document> toDocuments(SearchResp resp) {
        if (resp.getSearchResults() == null || resp.getSearchResults().isEmpty()) {
            return List.of();
        }
        return resp.getSearchResults().get(0).stream()
                .map(r -> {
                    Map<String, Object> e = r.getEntity();
                    int level = e.get("chunk_level") == null ? 0 : ((Number) e.get("chunk_level")).intValue();
                    return Document.builder()
                            .id((String) e.get("doc_id"))
                            .text((String) e.get("content"))
                            .metadata(Map.of(
                                    "source_id", String.valueOf(e.get("source_id")),
                                    "parent_id", String.valueOf(e.get("parent_id")),
                                    "chunk_level", level
                            ))
                            .build();
                })
                .toList();
    }

    // ── auto-merge (copied from MemoryHybridRetrieverService) ─────────────────

    private List<Document> autoMerge(List<Document> leafChunks, int threshold) {
        List<Document> current = new ArrayList<>(leafChunks);
        for (int round = 0; round < 2; round++) {
            current = mergeSingleLevel(current, threshold);
        }
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
        Map<Boolean, List<Document>> partitioned = chunks.stream()
                .collect(Collectors.partitioningBy(
                        d -> d.getMetadata().containsKey("parent_id")
                                && d.getMetadata().get("parent_id") != null
                ));

        List<Document> noParent = partitioned.get(false);
        List<Document> hasParent = partitioned.get(true);

        Map<String, List<Document>> grouped = hasParent.stream()
                .collect(Collectors.groupingBy(
                        d -> d.getMetadata().get("parent_id").toString()
                ));

        List<Document> duplicates = new ArrayList<>();
        List<Document> result = new ArrayList<>(noParent);
        for (var entry : grouped.entrySet()) {
            if (entry.getValue().size() >= threshold) {
                Optional<Document> parent = ragChunkMapper.getParentChunk(entry.getKey());
                parent.ifPresent(result::add);
                duplicates.addAll(entry.getValue());
            }
        }
        chunks.removeAll(duplicates);
        chunks.removeAll(noParent);
        result.addAll(chunks);
        return result;
    }

    private List<Document> parentFirst(List<Document> merged, int limit) {
        List<Document> upgraded = merged.stream()
                .filter(d -> getLevel(d) < 3)
                .toList();
        List<Document> leaf = merged.stream()
                .filter(d -> getLevel(d) == 3)
                .toList();
        int remaining = Math.max(0, limit - upgraded.size());
        return Stream.concat(upgraded.stream(), leaf.stream().limit(remaining)).toList();
    }

    private int getLevel(Document d) {
        return Integer.parseInt(d.getMetadata().get("chunk_level").toString());
    }
}
