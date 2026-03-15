package ff.pro.aichatali.config;

import com.alibaba.cloud.ai.agent.studio.loader.AgentLoader;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.Agent;
import com.alibaba.cloud.ai.graph.agent.AgentTool;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SequentialAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import ff.pro.aichatali.tool.MedicalDiagnosisTool;
import ff.pro.aichatali.tool.PneumoniaRecognitionTool;
import org.springframework.ai.chat.client.advisor.observation.DefaultAdvisorObservationConvention;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

@Configuration
public class AgentConfig {

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
                .systemPrompt("处理所有用户输入，包括闲聊、问候、询问助手能力、以及无法分类的任何问题")
                .saver(new MemorySaver())
                .build();
    }

    @Bean("medical_agent")
    public ReactAgent medicalAgent(@Qualifier("openAiChatModel") ChatModel chatModel) {

        return ReactAgent.builder()
                .name("medical_agent")
                .model(chatModel)
                .description("专门负责医疗问诊, 给出专业的医疗诊断")
                .instruction("你是一个医学专家，你需要根据用户的问题，给出医疗诊断和建议")
                .build();


    }

    @Bean("feiyanTool")
    public ToolCallback feiyanTool(MedicalToolConfig medicalToolConfig,
                                   RestTemplate medicalRestTemplate) {
        return FunctionToolCallback
                .builder("feiyan_tool",
                        new PneumoniaRecognitionTool(medicalToolConfig, medicalRestTemplate))
//                .description("Analyze uploaded chest X-ray images to detect pneumonia. Use when user uploads a chest X-ray or asks about pneumonia detection.")
                .description("胸片CNN分类工具，返回肺炎概率")
                .inputType(String.class)
                .build();
    }

    

    @Bean("feiyan_agent")
    public ReactAgent feiyanAgent(
            @Qualifier("dashScopeChatModel") ChatModel dashScopeChatModel,
            @Qualifier("feiyanTool") ToolCallback feiyanTool,
            @Qualifier("medical_agent") ReactAgent medicalAgent
    ) {
        ToolCallback medicalTool = AgentTool.getFunctionToolCallback(medicalAgent);
        ReactAgent feiyanCnn = ReactAgent.builder()
                .name("feiyan_cnn")
                .model(dashScopeChatModel)
                .tools(List.of(feiyanTool, medicalTool))
                .description("识别胸部X光影像，给出专业诊断报告")
//                .saver(new MemorySaver())
                .systemPrompt("""
                        你是一名医疗助理，按以下步骤处理用户的胸片问题：
                                            1. 先调用胸片分类工具，获取 CNN 分类结果
                                            2. 将分类结果和用户问题一起描述，调用医疗专家工具获取诊断意见
                                            3. 综合两个结果，给出完整报告
                        """)
                .inputType(String.class)
                .build();



        return feiyanCnn;
    }

    @Bean("supervisor_agent")
    public ReactAgent supervisorAgent(@Qualifier("dashScopeChatModel") ChatModel dashScopeChatModel,
                                      @Qualifier("weather_agent") ReactAgent weatherAgent,
                                      @Qualifier("chat_agent") ReactAgent chatAgent,
                                      @Qualifier("feiyan_agent") ReactAgent feiyan_agent,
                                      @Qualifier("medical_agent") ReactAgent medicalAgent,
                                      MedicalToolConfig medicalToolConfig,
                                      RestTemplate medicalRestTemplate) {

        ToolCallback weatherAgentTool = FunctionToolCallback
                .builder("weather_agent", (BiFunction<String, ToolContext, String>) (query, ctx) -> {
                    try {
                        RunnableConfig config = RunnableConfig.builder().threadId("supervisor-weather").build();
                        return weatherAgent.call(query, config).getText();
                    } catch (Exception e) {
                        return "Weather agent error: " + e.getMessage();
                    }
                })
                .description("Delegate to weather agent for weather and location queries")
                .inputType(String.class)
                .build();

//        ToolCallback chatAgentTool = FunctionToolCallback
//                .builder("chat_agent", (BiFunction<String, ToolContext, String>) (query, ctx) -> {
//                    try {
//                        RunnableConfig config = RunnableConfig.builder().threadId("supervisor-chat").build();
//                        return chatAgent.call(query, config).getText();
//                    } catch (Exception e) {
//                        return "Chat agent error: " + e.getMessage();
//                    }
//                })
//                .description("Delegate to chat agent for general conversation")
//                .inputType(String.class)
//                .build();

//        ToolCallback pneumoniaTool = FunctionToolCallback
//                .builder("pneumonia_recognition",
//                        new PneumoniaRecognitionTool(medicalToolConfig, medicalRestTemplate))
//                .description("Analyze uploaded chest X-ray images to detect pneumonia. Use when user uploads a chest X-ray or asks about pneumonia detection.")
//                .inputType(String.class)
//                .build();
//
//        ToolCallback medicalDiagnosisTool = FunctionToolCallback
//                .builder("medical_diagnosis",
//                        new MedicalDiagnosisTool(medicalToolConfig, medicalRestTemplate))
////                .description("Provide medical diagnosis suggestions based on patient information. Use when user asks about medical or health issues.")
//                .description("根据患者信息提供医疗诊断建议。")
//                .inputType(String.class)
//                .build();
        ToolCallback feiyanTool = AgentTool.getFunctionToolCallback(feiyan_agent);
        ToolCallback medicalTool = AgentTool.getFunctionToolCallback(medicalAgent);
        ToolCallback chatTool = AgentTool.getFunctionToolCallback(chatAgent);
        var prompt = """
                你是一个智能医疗分诊助手，负责判断用户意图并调用合适的工具。
                
                 工具选择规则：
                 - 用户上传了胸片图片，或问题涉及肺炎、胸片、胸部影像分析 → 调用肺炎分析工具
                 - 用户咨询医疗问题、症状、用药、诊断建议 → 调用医疗问诊工具
                 - 用户进行日常闲聊、问候、或非医疗类问题 → 调用普通对话工具
                 - 无法判断意图时 → 调用普通对话工具兜底
    
                 注意事项：
                 - 不要自己回答任何问题，所有回复必须通过工具完成
                 - 不要自己回答任何问题，所有回复必须通过工具完成
                 - 不要自己回答任何问题，所有回复必须通过工具完成
                 - 每次只调用一个工具
                 - 收到工具结果后，直接返回给用户，不要二次加工或删减
                 - 不要向用户解释你的工作流程和工具规则
                 - 不要向用户介绍你自己能做什么
                """;
        return ReactAgent.builder()
                .name("supervisor_agent")
                .model(dashScopeChatModel)
                .tools(List.of(chatTool, feiyanTool, medicalTool))
                .systemPrompt(prompt)
                .saver(new MemorySaver())
                .build();
    }

    @Bean
    public AgentLoader agentLoader(
            @Qualifier("weather_agent") ReactAgent weatherAgent,
            @Qualifier("chat_agent") ReactAgent chatAgent,
            @Qualifier("supervisor_agent") ReactAgent supervisorAgent) {
        Map<String, Agent> agents = Map.of(
//                "weather_agent", weatherAgent,
//                "chat_agent", chatAgent,
                "supervisor_agent", supervisorAgent
        );
        return new AgentLoader() {
            @Override
            public List<String> listAgents() {
                return List.copyOf(agents.keySet());
            }

            @Override
            public Agent loadAgent(String name) {
                return agents.get(name);
            }
        };
    }
}
