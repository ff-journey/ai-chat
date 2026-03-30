package ff.pro.aichatali.config;

import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MilvusConfig {

    public static final String DATABASE_NAME = "default";
    public static final String COLLECTION_NAME = "rag_java_demo";

    @Bean
    public BatchingStrategy batchingStrategy() {
        return new TokenCountBatchingStrategy();
    }

    @Bean
    public MilvusClientV2 milvusClientV2(
            @Value("${spring.ai.vectorstore.milvus.client.host}") String host,
            @Value("${spring.ai.vectorstore.milvus.client.port}") int port,
            @Value("${spring.ai.vectorstore.milvus.client.token}") String token,
            @Value("${spring.ai.vectorstore.milvus.client.connect-timeout-ms:10000}") long connectTimeout,
            @Value("${spring.ai.vectorstore.milvus.client.keep-alive-time-ms:55000}") long keepAliveTime,
            @Value("${spring.ai.vectorstore.milvus.client.keep-alive-timeout-ms:20000}") long keepAliveTimeout,
            @Value("${spring.ai.vectorstore.milvus.client.idle-timeout-ms:86400000}") long idleTimeout,
            @Value("${spring.ai.vectorstore.milvus.client.secure:false}") boolean secure
    ) {
        return new MilvusClientV2(ConnectConfig.builder()
                .uri((secure ? "https" : "http") + "://" + host + ":" + port)
                .connectTimeoutMs(connectTimeout)
                .keepAliveTimeMs(keepAliveTime)
                .keepAliveTimeoutMs(keepAliveTimeout)
                .idleTimeoutMs(idleTimeout)
                .token(token)
                .secure(secure)
                .build());
    }
}
