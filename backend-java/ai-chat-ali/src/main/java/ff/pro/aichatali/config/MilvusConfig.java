package ff.pro.aichatali.config;

import io.milvus.client.MilvusServiceClient;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.grpc.DataType;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.collection.*;
import io.milvus.param.index.CreateIndexParam;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class MilvusConfig implements InitializingBean {
    @Autowired
    private MilvusServiceClient milvusClient;
    @Autowired
    private EmbeddingModel embeddingModel;

    public static final String DATABASE_NAME = "default";
    public static final String COLLECTION_NAME = "rag_java_demo";
    @Bean
    public BatchingStrategy batchingStrategy() {
        return new TokenCountBatchingStrategy();
    }
    @Bean
    public MilvusVectorStore chunkVectorStore() {
        //        collection-name: rag_java_demo
        //        embedding-dimension: 1024
        //        metric-type: COSINE
        //        initialize-schema: true
        MilvusVectorStore config =
                MilvusVectorStore.builder(milvusClient, embeddingModel)
                        .collectionName(COLLECTION_NAME)
                        .databaseName(DATABASE_NAME)
                        .metricType(MetricType.IP)
                        .embeddingDimension(1024)
                        .initializeSchema(false)
                        .build();

        return config;
    }


    @Override
    public void afterPropertiesSet() throws Exception {

    }

    private void createChunkCollection() {
        // 已存在则跳过
        Boolean exists = milvusClient.hasCollection(
                HasCollectionParam.newBuilder()
                        .withDatabaseName("default")
                        .withCollectionName("chunks")
                        .build()
        ).getData();

        if (exists) return;

        // 定义字段
        FieldType docId = FieldType.newBuilder()
                .withName("doc_id")
                .withDataType(DataType.VarChar)
                .withMaxLength(36)
                .withPrimaryKey(true)
                .withAutoID(false)
                .build();

        FieldType content = FieldType.newBuilder()
                .withName("content")
                .withDataType(DataType.VarChar)
                .withMaxLength(65535)
                .build();

        FieldType metadata = FieldType.newBuilder()
                .withName("metadata")
                .withDataType(DataType.JSON)
                .withMaxLength(65535)
                .build();

        FieldType sourceId = FieldType.newBuilder()
                .withName("source_id")
                .withDataType(DataType.VarChar)
                .withMaxLength(128)
                .build();

        FieldType parentId = FieldType.newBuilder()
                .withName("parent_id")
                .withDataType(DataType.VarChar)
                .withMaxLength(36)
                .build();

        FieldType chunkLevel = FieldType.newBuilder()
                .withName("chunk_level")
                .withDataType(DataType.Int64)
                .build();

        FieldType embedding = FieldType.newBuilder()
                .withName("embedding")
                .withDataType(DataType.FloatVector)
                .withDimension(1536)
                .build();

        // 建 collection
        milvusClient.createCollection(
                CreateCollectionParam.newBuilder()
                        .withDatabaseName(DATABASE_NAME)
                        .withCollectionName(COLLECTION_NAME)
                        .withConsistencyLevel(ConsistencyLevelEnum.STRONG)
                        .withSchema(CollectionSchemaParam.newBuilder()
                                .addFieldType(docId)
                                .addFieldType(content)
                                .addFieldType(sourceId)
                                .addFieldType(metadata)
                                .addFieldType(parentId)
                                .addFieldType(chunkLevel)
                                .addFieldType(embedding)
                                .build())
                        .build()
        );

        // 向量索引
        milvusClient.createIndex(CreateIndexParam.newBuilder()
                .withDatabaseName(DATABASE_NAME)
                .withCollectionName(COLLECTION_NAME)
                .withFieldName("embedding")
                .withIndexType(IndexType.IVF_FLAT)
                .withMetricType(MetricType.IP)
                .withExtraParam("{\"nlist\":1024}")
                .withSyncMode(false)
                .build()
        );

        // 倒排索引
        milvusClient.createIndex(CreateIndexParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withFieldName("source_id")
                .withIndexType(IndexType.INVERTED)
                .build()
        );

        milvusClient.createIndex(CreateIndexParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withFieldName("parent_id")
                .withIndexType(IndexType.INVERTED)
                .build()
        );

        milvusClient.createIndex(CreateIndexParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withFieldName("chunk_level")
                .withIndexType(IndexType.BITMAP)
                .build()
        );

        // 加载到内存
        milvusClient.loadCollection(
                LoadCollectionParam.newBuilder()
                        .withDatabaseName(DATABASE_NAME)
                        .withCollectionName(COLLECTION_NAME)
                        .build()
        );
    }
}
