package ff.pro.aichatali;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.vectorstore.milvus.autoconfigure.MilvusVectorStoreAutoConfiguration;
import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeChatAutoConfiguration;
import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeEmbeddingAutoConfiguration;

@SpringBootApplication(exclude = {
        OpenAiChatAutoConfiguration.class,          // Chat 由 ChatModelConfig 手动管理
        DashScopeChatAutoConfiguration.class,        // Chat 由 ChatModelConfig 手动管理
        DashScopeEmbeddingAutoConfiguration.class,   // Embedding 用 OpenAI 兼容层 (SiliconFlow)
        MilvusVectorStoreAutoConfiguration.class
})
public class AiChatAliApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiChatAliApplication.class, args);
    }

}
