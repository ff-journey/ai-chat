package ff.pro.aichatali.repo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import ff.pro.aichatali.config.MilvusConfig;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.QueryParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.QueryResultsWrapper;
import io.milvus.response.SearchResultsWrapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    private final MilvusServiceClient milvusClient;
    private static final String COLLECTION = MilvusConfig.COLLECTION_NAME;
    private final Gson gson;
    private final Map<String, Document> store = new ConcurrentHashMap<>();
    private final Path storePath = Path.of("data/parent_chunks.json");
    final ObjectMapper objectMapper;

    @Override
    public void batchInsert(List<RagChunkPo> chunks) {
        List<InsertParam.Field> fields = List.of(
                new InsertParam.Field("doc_id", chunks.stream().map(RagChunkPo::getDocId).toList()),
                new InsertParam.Field("content", chunks.stream().map(RagChunkPo::getContent).toList()),
//                new InsertParam.Field("metadata", chunks.stream().map(it->gson.toJsonTree(it.getMetadata())).toList()),
                new InsertParam.Field("source_id", chunks.stream().map(RagChunkPo::getSourceId).toList()),
                new InsertParam.Field("parent_id", chunks.stream().map(RagChunkPo::getParentId).toList()),
                new InsertParam.Field("chunk_level", chunks.stream().map(RagChunkPo::getChunkLevel).toList()),
                new InsertParam.Field("embedding", chunks.stream().map(RagChunkPo::getEmbedding).toList())
        );
        milvusClient.insert(InsertParam.newBuilder()
                .withCollectionName(COLLECTION)
                .withFields(fields)
                .build());
    }

    @Override
    public List<RagChunkPo> searchByVector(List<Float> vector, int topK, String filter) {
        SearchParam param = SearchParam.newBuilder()
                .withCollectionName(COLLECTION)
                .withFloatVectors(List.of(vector))
                .withVectorFieldName("embedding")
                .withTopK(topK)
                .withExpr(filter == null ? "" : filter)   // "source_id == 'xxx' and chunk_level == 3"
                .withOutFields(List.of("doc_id", "content", "source_id", "parent_id", "chunk_level"))
                .build();

        SearchResultsWrapper wrapper = new SearchResultsWrapper(
                milvusClient.search(param).getData().getResults()
        );
        return wrapper.getRowRecords(0).stream()
                .map(this::toChunkPO)
                .toList();
    }

    @Override
    public List<RagChunkPo> findBySourceId(String sourceId) {
        QueryParam param = QueryParam.newBuilder()
                .withCollectionName(COLLECTION)
                .withExpr("source_id == \"" + sourceId + "\"")
                .withOutFields(List.of("doc_id", "content", "source_id", "parent_id", "chunk_level"))
                .build();

        return new QueryResultsWrapper(milvusClient.query(param).getData()).getRowRecords().stream()
                .map(this::toChunkPO)
                .toList();
    }

    @Override
    public void deleteBySourceId(String sourceId) {

    }

    private RagChunkPo toChunkPO(QueryResultsWrapper.RowRecord row) {
        return RagChunkPo.builder()
                .docId((String) row.get("doc_id"))
                .content((String) row.get("content"))
                .sourceId((String) row.get("source_id"))
                .parentId((String) row.get("parent_id"))
//                .metadata(gson.fromJson((String) row.get("metadata"), new TypeToken<Map<String, Object>>(){}.getType()))
                .chunkLevel(row.get("chunk_level") == null ? 0 : (int) row.get("chunk_level"))
                .document(Document.builder()
                        .id((String) row.get("doc_id"))
                        .text((String) row.get("content"))
                        .metadata(Map.of("source_id", (String) row.get("source_id"), "parent_id", (String) row.get("parent_id"), "chunk_level", (int) row.get("chunk_level")))
                        .build())
                .build();
    }

    @Override
    public void insertParentChunk(String id, Document doc) {
        store.put(id, doc);
        persist();
    }

    public Optional<Document> getParentChunk(String id) {
        return Optional.ofNullable(store.get(id));
    }

    public List<Document> getParentChunk(List<String> ids) {
        List<Document> docs = ids.stream().map(id -> store.getOrDefault(id, new Document(""))).toList();
        return docs;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record DocumentDTO(
            String id,
            String text,
            Map<String, Object> metadata
    ) {
    }

    @PostConstruct
    public void load() {
        if (Files.exists(storePath)) {
            try {
                Map<String, DocumentDTO> raw = objectMapper.readValue(
                        storePath.toFile(),
                        new TypeReference<>() {
                        }
                );
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
                // 启动时没有文件也没关系
                log.error("Failed to load parent chunks", e);
            }
        }
    }

    private void persist() {
        try {
            Files.createDirectories(storePath.getParent());
            Map<String, Document> raw = new HashMap<>(store);
            objectMapper.writeValue(storePath.toFile(), raw);
        } catch (IOException e) {
            throw new RuntimeException("Failed to persist parent chunks", e);
        }
    }
}