package ff.pro.aichatali.tool.rag_tool;

import ff.pro.aichatali.common.ToolResult;
import ff.pro.aichatali.config.MilvusConfig;
import ff.pro.aichatali.repo.RagChunkMapper;
import ff.pro.aichatali.service.EmbeddingService;
import ff.pro.aichatali.service.MilvusHybridRetrieverService;
import ff.pro.aichatali.service.QueryRewriter;
import ff.pro.aichatali.service.RerankService;
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
public class RagTool implements BiFunction<MilvusHybridRetrieverService.Input, ToolContext, ToolResult> {


    final MilvusHybridRetrieverService milvusHybridRetrieverService;

    @Override
    public ToolResult apply(MilvusHybridRetrieverService.Input input, ToolContext toolContext) {
        try {
            return milvusHybridRetrieverService.retrieve(input);
        } catch (Exception e) {
            log.error("MilvusHybridRetrieverService error: {}", e.getMessage(), e);
            return ToolResult.error("查询失败, 知识库服务错误");
        }
    }


}
