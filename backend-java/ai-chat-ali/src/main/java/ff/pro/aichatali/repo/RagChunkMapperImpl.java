package ff.pro.aichatali.repo;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import ff.pro.aichatali.config.MilvusConfig;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.QueryParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.highlevel.dml.InsertRowsParam;
import io.milvus.response.QueryResultsWrapper;
import io.milvus.response.SearchResultsWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class RagChunkMapperImpl implements RagChunkMapper {

    private final MilvusServiceClient milvusClient;
    private static final String COLLECTION = MilvusConfig.COLLECTION_NAME;
    private final Gson gson;


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
                .withTopK(topK)
                .withExpr(filter)   // "source_id == 'xxx' and chunk_level == 3"
                .withOutFields(List.of("doc_id", "content", "source_id", "parent_id"))
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
                .chunkLevel((int) row.get("chunk_level"))
                .build();
    }
}