package ff.pro.aichatali.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 统一管理 ChatModel 的创建。
 * 关掉各厂商的自动装配，在此手动构建 Bean，避免 OpenAI 兼容层的兼容性问题。
 */
@Slf4j
@Configuration
public class ChatModelConfig {

    @Bean
    @Primary
    public ChatModel dashScopeChatModel(
            @Value("${ai.llm.api-key}") String apiKey,
            @Value("${ai.llm.model}") String model,
            @Value("${ai.llm.temperature:0.5}") Double temperature
    ) {
        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .apiKey(apiKey)
                .build();

        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .model(model)
                .temperature(temperature)
                .build();

        log.info("ChatModel created: DashScope [model={}, temperature={}]", model, temperature);

        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(options)
                .build();
    }
}
