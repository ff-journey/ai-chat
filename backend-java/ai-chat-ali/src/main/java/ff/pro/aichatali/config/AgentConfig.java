package ff.pro.aichatali.config;

import com.alibaba.cloud.ai.agent.studio.loader.AgentLoader;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.Agent;
import com.alibaba.cloud.ai.graph.agent.AgentTool;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import ff.pro.aichatali.tool.FeiyanAgentMedicalTool;
import ff.pro.aichatali.tool.MedicalDiagnosisTool;
import ff.pro.aichatali.tool.PneumoniaRecognitionTool;
import org.springframework.ai.chat.client.advisor.observation.DefaultAdvisorObservationConvention;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

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
                .systemPrompt("你是一个私人助理")
                .description("处理所有用户输入，包括闲聊、问候、询问助手能力、以及无法分类的任何问题")
                .saver(new MemorySaver())
                .build();
    }

    @Bean("medicalAgent")
    public ReactAgent medicalAgent(@Qualifier("openAiChatModel") ChatModel chatModel) {

        return ReactAgent.builder()
                .name("medicalAgent")
                .model(chatModel)
                .description("专门负责医疗问诊，给出专业的医疗诊断")
                .instruction("你是一个医学专家，你需要根据用户的问题，给出医疗诊断和建议")
                .inputType(String.class)
                .build();


    }

    public record MedicalInput(
            String input
    ) {
    }


    @Bean("ragTool")
    public ToolCallback ragTool(VectorStore vectorStore) {
        ToolCallback ragTool = FunctionToolCallback
                .builder("search_knowledge_base",
                        (Function<Input, Map<String, String>>) query -> {
                            List<Document> docs = vectorStore.similaritySearch(
                                    SearchRequest.builder()
                                            .query(query.query)
                                            .topK(5)
                                            .similarityThreshold(0.5)
                                            .build()
                            );
                            if (docs.isEmpty()) return Map.of("result","知识库中没有相关内容");
                            String content = docs.stream()
                                    .map(Document::getText)
                                    .collect(Collectors.joining("\n\n---\n\n"));
                            return Map.of("result", content);
                        })
                .description("从知识库中检索相关文档，当用户需要检索知识库时调用")
                .inputType(Input.class)
                .build();
        return ragTool;
    }

    @Bean("feiyanTool")
    public ToolCallback feiyanTool(MedicalToolConfig medicalToolConfig,
                                   RestTemplate medicalRestTemplate) {
        return FunctionToolCallback
                .builder("feiyan_tool",
                        new PneumoniaRecognitionTool(medicalToolConfig, medicalRestTemplate))
//                .description("Analyze uploaded chest X-ray images to detect pneumonia. Use when user uploads a chest X-ray or asks about pneumonia detection.")
                .description("胸片CNN分类工具，返回肺炎概率")
                .inputType(Input.class)
                .build();
    }

    public record Input(String query) {
    }

    @Bean("supervisor_agent")
    public ReactAgent supervisorAgent(@Qualifier("dashScopeChatModel") ChatModel dashScopeChatModel,
                                      @Qualifier("weather_agent") ReactAgent weatherAgent,
                                      @Qualifier("chat_agent") ReactAgent chatAgent,
                                      @Qualifier("medicalAgent") ReactAgent medicalAgent,
                                      @Qualifier("ragTool") ToolCallback ragTool,
                                      MedicalToolConfig medicalToolConfig,
                                      RestTemplate medicalRestTemplate) {

        ToolCallback weatherAgentTool = FunctionToolCallback
                .builder("weather_agent", (BiFunction<String, ToolContext, String>) (query, ctx) -> {
                    try {
                        RunnableConfig config = RunnableConfig.builder().threadId("supervisor-weather").build();
                        return weatherAgent.stream(query, config)
                                .filter(output -> output instanceof StreamingOutput)
                                .map(output -> {
                                    StreamingOutput so = (StreamingOutput) output;
                                    return so.getOutputType() == OutputType.AGENT_MODEL_STREAMING ? so.message().getText() : "";
                                })
                                .filter(text -> !text.isEmpty())
                                .reduce("", String::concat)
                                .block();
                    } catch (Exception e) {
                        return "Weather agent error: " + e.getMessage();
                    }
                })
                .description("Delegate to weather agent for weather and location queries")
                .inputType(String.class)
                .build();

        ToolCallback medicalTool = FunctionToolCallback
                .builder("medicalAgent", (BiFunction<Input, ToolContext, String>) (inputObj, ctx) -> {
                    try {
                        String input = inputObj != null && inputObj.query() != null ? inputObj.query() : "";
                        String result = medicalAgent.call(Map.of("input", input), RunnableConfig.builder().build()).getText();
                        return result.replaceAll("(?s)<think>.*?</think>\\s*", "").trim();
                    } catch (Exception e) {
                        return "Medical agent error: " + e.getMessage();
                    }
                })
                .description("专门负责医疗问诊，给出专业的医疗诊断")
                .inputType(Input.class)
                .build();
        ToolCallback chatTool = AgentTool.getFunctionToolCallback(chatAgent);
        ToolCallback feiyanAgentMedicalTool = FunctionToolCallback
                .builder("feiyanAgentMedicalTool",
                        new FeiyanAgentMedicalTool(medicalToolConfig, medicalRestTemplate, medicalAgent))
                .description("识别胸部X光影像，给出专业诊断报告")
                .inputType(FeiyanAgentMedicalTool.Input.class)
                .build();
        var prompt = """
                当前时间：{{current_time}}
                你是一个优秀的私人助理, 任何动作前都会分析用户意图，并调用合适的工具。
                任何动作前都首先分析用户意图, 并查看上下文是否已有足够信息做出直接回复。
                 工具选择规则：
                 - 消息包含 [用户已上传胸部X光图片] 标记 → 必须调用肺炎分析工具
                 - 用户咨询医疗问题、症状、用药、诊断建议（无图片标记）→ 在消息历史中汇总重要的病情信息后, 调用医疗问诊工具
                 - 用户提到胸片、肺炎、影像分析但没有图片标记 → 调用医疗问诊工具，工具会告知用户需要上传图片
                 - 用户进行日常闲聊、问候、或非医疗类问题 → 调用普通对话工具
                 - 无法判断意图时 → 视为普通聊天, 可自行回复
                
                 注意事项：
                 - 每次只调用一个工具
                 - 收到工具结果后，直接返回给用户，医疗诊断结果适当加工友好回复, **如果病情信息太少导致判断模糊, 需要主动提问更多信息**
                 - 不要向用户解释你的工作流程和工具规则
                 - 不要向用户介绍你自己能做什么
                 - 如果工具返回包含"请上传"等提示信息，直接以友好的中文转达给用户
                 - 如果医疗诊断结果病情信息不足, 可主动询问用户更多细节
                """;
        prompt = prompt.replace("{{current_time}}",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        return ReactAgent.builder()
                .name("supervisor_agent")
                .model(dashScopeChatModel)
                .tools(List.of(feiyanAgentMedicalTool, medicalTool, ragTool))
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

    @Bean
    public TokenTextSplitter tokenTextSplitter() {
        return new TokenTextSplitter(300, 50, 5, 10000, true);
    }
}
