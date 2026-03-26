package ff.pro.aichatali.service.websearch;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * 联网搜索抽象接口，返回标准 Document 列表，可直接参与 RRF 融合。
 * 实现类只需关注搜索逻辑，无需了解下游 RAG pipeline。
 */
public interface WebSearchPort {

    /**
     * @param query 搜索关键词
     * @param topK  最多返回条数
     * @return Document 列表，metadata 包含 source=web / url / title / chunk_level=3
     */
    List<Document> search(String query, int topK);
}
