package ff.pro.aichatali.config;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import ff.pro.aichatali.service.ToolRegistryService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.observation.DefaultAdvisorObservationConvention;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.BiFunction;

@Configuration
public class AgentConfig {

    @Bean("generalChatClient")
    @Primary
    public ChatClient generalChatClient(@Qualifier("dashScopeChatModel") DashScopeChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem("你是一个通用助手")
                .build();
    }

    @Bean("weather_agent")
    public ReactAgent weatherAgent(@Qualifier("dashScopeChatModel") ChatModel chatModel) {
        ToolCallback weatherTool = FunctionToolCallback
                .builder("get_weather", (BiFunction<String, ToolContext, String>) (city, ctx) -> "Sunny in " + city + "!")
                .description("Get weather for a city")
                .inputType(String.class)
                .build();
        return ReactAgent.builder()
                .advisorObservationConvention(new DefaultAdvisorObservationConvention())
                .name("weather_agent")
                .model(chatModel)
                .tools(List.of(weatherTool))
                .systemPrompt("You are a weather assistant. Use get_weather tool to answer.")
                .saver(new MemorySaver())
                .build();
    }

    @Bean("chat_agent")
    public ReactAgent chatAgent(@Qualifier("dashScopeChatModel") ChatModel chatModel) {
        return ReactAgent.builder()
                .name("chat_agent")
                .model(chatModel)
                .systemPrompt("你是一个私人助理：鼠鼠助手")
                .description("处理所有用户输入，包括闲聊、问候、询问助手能力、以及无法分类的任何问题")
                .saver(new MemorySaver())
                .build();
    }

    @Bean("supervisor_agent")
    public ReactAgent supervisorAgent(
            @Qualifier("dashScopeChatModel") ChatModel dashScopeChatModel,
            ToolRegistryService toolRegistryService) {

        String prompt = """
                当前时间：{{current_time}}
                你是一个优秀的私人助理：鼠鼠助手, 任何动作前都首先分析用户意图, 并查看上下文是否已有足够信息做出直接回复。
                 【工具选择规则】：
                 1. 消息包含 [用户已上传胸部X光图片] 且用户需要完整诊断报告
                    → 分两步：①先调用 pneumoniaCnnTool 获取影像分类结果，②再调用 medicalDiagnosis
                 2. 消息包含 [用户已上传胸部X光图片] 且用户只需快速判断是否肺炎
                    → 仅调用 pneumoniaCnnTool
                 3. 用户咨询医疗问题、症状、用药、诊断建议（无图片）
                    → 若症状信息充足，汇总病情信息后直接调用 medicalDiagnosis
                    → 若症状信息不足（缺少持续时间、既往病史、用药史），主动追问后再调用medicalDiagnosis
                 4. 用户提到胸片、肺炎、影像分析但没有图片标记
                    → 告知用户需要上传图片，不调用工具
                 5. 用户进行日常闲聊、问候、或非医疗类问题
                    → 直接回复，无需调用工具

                 【注意事项】：
                 - 多步骤任务按顺序逐步调用工具，每步等待结果后再进行下一步
                 - 工具调用失败或返回异常时，告知用户当前服务暂时不可用，不要编造结果
                 - 收到工具结果后，整合所有信息后统一回复用户，医疗诊断结果适当加工为友好语言
                 - 不要向用户解释工作流程和工具调用过程
                 - 不要向用户介绍你自己能做什么
                 【免责声明】
                 所有诊断建议仅供参考，不构成正式医疗意见，请以医院专业医生的诊断为准。
                """.replace("{{current_time}}",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));

        return ReactAgent.builder()
                .name("supervisor_agent")
                .model(dashScopeChatModel)
                .tools(toolRegistryService.getToolCallbacks())
                .systemPrompt(prompt)
                .saver(new MemorySaver())
                .build();
    }
}
