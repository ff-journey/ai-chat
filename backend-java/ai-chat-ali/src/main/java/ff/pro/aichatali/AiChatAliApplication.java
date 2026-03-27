package ff.pro.aichatali;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration;
import org.springframework.ai.vectorstore.milvus.autoconfigure.MilvusVectorStoreAutoConfiguration;

@SpringBootApplication(exclude = {
        OpenAiEmbeddingAutoConfiguration.class,
        MilvusVectorStoreAutoConfiguration.class
})
public class AiChatAliApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiChatAliApplication.class, args);
    }

}
