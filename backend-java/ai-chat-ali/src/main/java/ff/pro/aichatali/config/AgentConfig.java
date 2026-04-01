package ff.pro.aichatali.config;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.summarization.SummarizationHook;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
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

/**
 * AgentConfig — supervisor agent + shared MemorySaver.
 *
 * The MemorySaver is a singleton bean shared across all agent instances.
 * ChatController builds per-request ReactAgents with the selected tool set,
 * but always reuses this saver so conversation history persists by threadId.
 */

@Configuration
public class AgentConfig {

    /** Shared across all agent instances — stores conversation checkpoints by threadId. */
    @Bean("supervisorMemorySaver")
    public MemorySaver supervisorMemorySaver() {
        return new MemorySaver();
    }

    @Bean("generalChatClient")
    @Primary
    public ChatClient generalChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem("你是一个通用助手")
                .build();
    }

    @Bean("weatherAgent")
    public ReactAgent weatherAgent(ChatModel chatModel) {
        ToolCallback weatherTool = FunctionToolCallback
                .builder("get_weather", (BiFunction<String, ToolContext, String>) (city, ctx) -> "Sunny in " + city + "!")
                .description("Get weather for a city")
                .inputType(String.class)
                .build();
        return ReactAgent.builder()
                .advisorObservationConvention(new DefaultAdvisorObservationConvention())
                .name("weatherAgent")
                .model(chatModel)
                .tools(List.of(weatherTool))
                .systemPrompt("You are a weather assistant. Use get_weather tool to answer.")
                .saver(new MemorySaver())
                .build();
    }

    @Bean("chatAgent")
    public ReactAgent chatAgent(ChatModel chatModel) {
        return ReactAgent.builder()
                .name("chatAgent")
                .model(chatModel)
                .systemPrompt("你是一个私人助理：鼠鼠助手")
                .description("处理所有用户输入，包括闲聊、问候、询问助手能力、以及无法分类的任何问题")
                .saver(new MemorySaver())
                .build();
    }


    /** System prompt template — shared between the default bean and per-request agents. */
//    public static String buildSupervisorPrompt() {
//        return """
//                当前时间：%s
//                你是一个优秀的私人助理：鼠鼠助手, 任何动作前都首先分析用户意图, 并查看上下文是否已有足够信息做出直接回复。
//                 【工具选择规则】：
//                 1. 消息包含 [用户已上传胸部X光图片] 且用户需要完整诊断报告
//                    → 分两步：①先调用 pneumoniaCnnTool 获取影像分类结果，②再调用 medicalDiagnosisTool
//                 2. 消息包含 [用户已上传胸部X光图片] 且用户只需快速判断是否肺炎
//                    → 仅调用 pneumoniaCnnTool
//                 3. 用户咨询医疗问题、症状、用药、诊断建议（无图片）
//                    → 若症状信息充足，汇总病情信息后直接调用 medicalDiagnosisTool
//                    → 若症状信息不足（缺少持续时间、既往病史、用药史），主动追问后再调用medicalDiagnosisTool
//                 4. 用户提到胸片、肺炎、影像分析但没有图片标记
//                    → 告知用户需要上传图片，不调用工具
//                 5. 用户进行日常闲聊、问候、或非医疗类问题
//                    → 直接回复，无需调用工具
//
//                 【注意事项】：
//                 - 多步骤任务按顺序逐步调用工具，每步等待结果后再进行下一步
//                 - 工具调用失败或返回异常时，告知用户当前服务暂时不可用，不要编造结果
//                 - 收到工具结果后，整合所有信息后统一回复用户，医疗诊断结果适当加工为友好语言
//                 - 不要向用户解释工作流程和工具调用过程
//                 - 不要向用户介绍你自己能做什么
//                 【免责声明】
//                 所有诊断建议仅供参考，不构成正式医疗意见，请以医院专业医生的诊断为准。
//                """.formatted(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
//    }
    public static String buildSupervisorPrompt() {
        return """
                当前时间：%s
                你是鼠鼠助手，一个专业的医疗辅助 AI，任何操作前先完整理解用户意图和对话历史。
                用自然对话风格回答，不要使用 markdown 标题、emoji、分隔线
                
                【回复规范】
                - 语气专业且亲切，避免过度使用专业术语
                - 关键信息不全时先澄清再推理，别跳步
                - 知识类回复引用知识库结果时注明来源
                - 用户提到有影像、检查报告等资料时，先检查当前可用工具是否支持处理，如支持则引导用户提供，如不支持则建议用户提供文字描述
                - 不做确定性诊断结论，使用「可能」「建议就医确认」等表述
                - 仅在给出具体的诊断分析、用药建议、治疗方案时附加免责声明，闲聊、追问、纯知识科普时不加
                - 涉及诊断或治疗建议时，用自然语气提醒用户以医生意见为准，不要每次使用相同的固定句式
                
                【决策优先级（从高到低）】
                P0 - 紧急安全：
                - 用户表达紧急症状（胸痛、呼吸困难、意识模糊、大量出血等）
                  → 立即告知拨打急救电话（120），不等待工具调用，不做诊断
                
                P1 - 工具调用：
                - 根据【当前可用工具】中的工具描述判断是否需要调用
                - 当同时存在知识检索类工具和诊断推理类工具时，
                  → 涉及医疗问题应先调用检索工具获取参考资料，调用诊断工具
                - 纯知识类问题
                  → 调用检索工具即可，无需调用诊断工具
                - 信息不足时先追问，再走上述流程
                - 对于已在上下文中充分讨论过且无新信息的追问，可直接基于已有结果回复，无需重复调用工具
                
                P2 - 非医疗：
                - 闲聊、问候、与医疗无关的问题
                  → 自行选择工具调用或者直接回复，简短友好，间歇性引导用户描述健康问题
                
                【当前可用工具】
                
                """.formatted(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
    }

    @Bean("summarizationHook")
    public SummarizationHook summarizationHook(ChatModel chatModel) {
        return SummarizationHook.builder()
                .model(chatModel)
                .maxTokensBeforeSummary(80000)
                .messagesToKeep(50)
                .build();
    }

    @Bean("supervisorAgent")
    public ReactAgent supervisorAgent(
            ChatModel chatModel,
            CustomToolCallbackProvider toolCallbackProvider,
            @Qualifier("supervisorMemorySaver") MemorySaver memorySaver,
            @Qualifier("summarizationHook") SummarizationHook summarizationHook,
            DynamicToolPromptInterceptor dynamicToolPromptInterceptor,
            ToolCallCapture toolCallCapture,
            LoggingModelInterceptor loggingModelInterceptor
    ) {

        return ReactAgent.builder()
                .name("supervisorAgent")
                .model(chatModel)
                .toolCallbackProviders(toolCallbackProvider)
                .systemPrompt(buildSupervisorPrompt())
                .saver(memorySaver)
                .hooks(summarizationHook)
                .interceptors(dynamicToolPromptInterceptor, loggingModelInterceptor,toolCallCapture)
                .build();
    }
}
