package ff.pro.aichatali.repo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import ff.pro.aichatali.config.MilvusConfig;
import io.milvus.common.clientenum.FunctionType;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.index.request.CreateIndexReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.QueryResp;
import io.milvus.v2.service.vector.response.SearchResp;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Repository
@RequiredArgsConstructor
public class RagChunkMapperImpl implements RagChunkMapper {

    private final MilvusClientV2 milvusClientV2;
    private static final String COLLECTION = MilvusConfig.COLLECTION_NAME;
    private final Gson gson;
    private final Map<String, Document> store = new ConcurrentHashMap<>();
    private final Path storePath = Path.of("data/parent_chunks.json");
    final ObjectMapper objectMapper;

    // ── Collection initialization ─────────────────────────────────────────────

    @PostConstruct
    public void init() {
        loadParentChunks();
        createChunkCollection();
    }

    private void createChunkCollection() {
        boolean exists = milvusClientV2.hasCollection(
                HasCollectionReq.builder().collectionName(COLLECTION).build());
        if (exists) {
            log.info("Milvus collection '{}' already exists, skip creation", COLLECTION);
            return;
        }

        CreateCollectionReq.CollectionSchema schema = milvusClientV2.createSchema();
        schema.addField(AddFieldReq.builder()
                .fieldName("doc_id").dataType(DataType.VarChar)
                .maxLength(36).isPrimaryKey(true).autoID(false).build());
        schema.addField(AddFieldReq.builder()
                .fieldName("content").dataType(DataType.VarChar)
                .maxLength(65535).enableAnalyzer(true)
                .analyzerParams(Map.of("type", "chinese")).build());
        schema.addField(AddFieldReq.builder()
                .fieldName("source_id").dataType(DataType.VarChar)
                .maxLength(128).build());
        schema.addField(AddFieldReq.builder()
                .fieldName("parent_id").dataType(DataType.VarChar)
                .maxLength(36).build());
        schema.addField(AddFieldReq.builder()
                .fieldName("chunk_level").dataType(DataType.Int32).build());
        schema.addField(AddFieldReq.builder()
                .fieldName("embedding").dataType(DataType.FloatVector)
                .dimension(1024).build());
        schema.addField(AddFieldReq.builder()
                .fieldName("sparse_embedding").dataType(DataType.SparseFloatVector).build());
        schema.addFunction(CreateCollectionReq.Function.builder()
                .name("bm25_fn")
                .functionType(FunctionType.BM25)
                .inputFieldNames(List.of("content"))
                .outputFieldNames(List.of("sparse_embedding"))
                .build());

        milvusClientV2.createCollection(CreateCollectionReq.builder()
                .collectionName(COLLECTION)
                .collectionSchema(schema)
                .consistencyLevel(ConsistencyLevel.STRONG)
                .build());

        milvusClientV2.createIndex(CreateIndexReq.builder()
                .collectionName(COLLECTION)
                .indexParams(List.of(
                        IndexParam.builder().fieldName("embedding")
                                .indexType(IndexParam.IndexType.IVF_FLAT)
                                .metricType(IndexParam.MetricType.IP)
                                .extraParams(Map.of("nlist", 1024)).build(),
                        IndexParam.builder().fieldName("sparse_embedding")
                                .indexType(IndexParam.IndexType.SPARSE_INVERTED_INDEX)
                                .metricType(IndexParam.MetricType.BM25).build(),
                        IndexParam.builder().fieldName("source_id")
                                .indexType(IndexParam.IndexType.INVERTED).build(),
                        IndexParam.builder().fieldName("parent_id")
                                .indexType(IndexParam.IndexType.INVERTED).build(),
                        IndexParam.builder().fieldName("chunk_level")
                                .indexType(IndexParam.IndexType.BITMAP).build()
                )).build());

        milvusClientV2.loadCollection(
                LoadCollectionReq.builder().collectionName(COLLECTION).build());

        log.info("Milvus collection '{}' created and loaded", COLLECTION);
    }

    // ── CRUD operations ───────────────────────────────────────────────────────

    @Override
    public void batchInsert(List<RagChunkPo> chunks) {
        if (CollectionUtils.isEmpty(chunks)) {
            return;
        }
        List<JsonObject> rows = new ArrayList<>();
        for (RagChunkPo c : chunks) {
            JsonObject obj = new JsonObject();
            obj.addProperty("doc_id", c.getDocId());
            obj.addProperty("content", c.getContent());
            obj.addProperty("source_id", c.getSourceId());
            obj.addProperty("parent_id", c.getParentId());
            obj.addProperty("chunk_level", c.getChunkLevel());
            obj.add("embedding", gson.toJsonTree(c.getEmbedding()));
            rows.add(obj);
        }
        milvusClientV2.insert(InsertReq.builder()
                .collectionName(COLLECTION)
                .data(rows)
                .build());
    }

    @Override
    public List<RagChunkPo> searchByVector(List<Float> vector, int topK, String filter) {
        SearchResp resp = milvusClientV2.search(SearchReq.builder()
                .collectionName(COLLECTION)
                .data(List.of(new FloatVec(vector)))
                .annsField("embedding")
                .topK(topK)
                .filter(filter == null ? "" : filter)
                .outputFields(List.of("doc_id", "content", "source_id", "parent_id", "chunk_level"))
                .build());
        return resp.getSearchResults().get(0).stream()
                .map(hit -> toChunkPO(hit.getEntity()))
                .toList();
    }

    @Override
    public List<RagChunkPo> findBySourceId(String sourceId) {
        QueryResp resp = milvusClientV2.query(QueryReq.builder()
                .collectionName(COLLECTION)
                .filter("source_id == \"" + sourceId + "\"")
                .outputFields(List.of("doc_id", "content", "source_id", "parent_id", "chunk_level"))
                .build());
        return resp.getQueryResults().stream()
                .map(r -> toChunkPO(r.getEntity()))
                .toList();
    }

    @Override
    public void deleteBySourceId(String sourceId) {
        milvusClientV2.delete(DeleteReq.builder()
                .collectionName(COLLECTION)
                .filter("source_id == \"" + sourceId + "\"")
                .build());
    }

    private RagChunkPo toChunkPO(Map<String, Object> fields) {
        int level = fields.get("chunk_level") == null ? 0 : ((Number) fields.get("chunk_level")).intValue();
        return RagChunkPo.builder()
                .docId((String) fields.get("doc_id"))
                .content((String) fields.get("content"))
                .sourceId((String) fields.get("source_id"))
                .parentId((String) fields.get("parent_id"))
                .chunkLevel(level)
                .document(Document.builder()
                        .id((String) fields.get("doc_id"))
                        .text((String) fields.get("content"))
                        .metadata(Map.of(
                                "source_id", (String) fields.get("source_id"),
                                "parent_id", (String) fields.get("parent_id"),
                                "chunk_level", level))
                        .build())
                .build();
    }

    // ── Parent chunk store (file-backed) ──────────────────────────────────────

    @Override
    public void insertParentChunk(String id, Document doc) {
        store.put(id, doc);
        persist();
    }

    @Override
    public Optional<Document> getParentChunk(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Document> getParentChunk(List<String> ids) {
        return ids.stream().map(id -> store.getOrDefault(id, new Document(""))).toList();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record DocumentDTO(String id, String text, Map<String, Object> metadata) {}

    private void loadParentChunks() {
        if (Files.exists(storePath)) {
            try {
                Map<String, DocumentDTO> raw = objectMapper.readValue(
                        storePath.toFile(), new TypeReference<>() {});
                Map<String, Document> documents = raw.entrySet().stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                e -> Document.builder()
                                        .id(e.getValue().id())
                                        .text(e.getValue().text())
                                        .metadata(e.getValue().metadata())
                                        .build()
                        ));
                store.putAll(documents);
            } catch (IOException e) {
                log.error("Failed to load parent chunks", e);
            }
        }
    }

    private void persist() {
        try {
            Files.createDirectories(storePath.getParent());
            objectMapper.writeValue(storePath.toFile(), new HashMap<>(store));
        } catch (IOException e) {
            throw new RuntimeException("Failed to persist parent chunks", e);
        }
    }
}
