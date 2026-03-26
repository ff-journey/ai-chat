package ff.pro.aichatali.tool.feiyan_tool;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import ff.pro.aichatali.config.MedicalToolConfig;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.function.BiFunction;

/**
 * @author journey
 * @date 2026/3/16
 **/
public class FeiyanAgentMedicalTool implements BiFunction<FeiyanAgentMedicalTool.Input, ToolContext, String> {

    public record Input(String query) {}

    private final FeiyanCnnTool feiyanCnnTool;
    private final ReactAgent medicalAgent;

    public FeiyanAgentMedicalTool(MedicalToolConfig config, RestTemplate restTemplate, ReactAgent medicalAgent) {
        this.feiyanCnnTool = new FeiyanCnnTool(config, restTemplate);
        this.medicalAgent = medicalAgent;
    }

    @Override
    public String apply(Input inputObj, ToolContext toolContext) {
        String input = inputObj != null && inputObj.query() != null ? inputObj.query() : "";
        try {
        Object img = toolContext.getContext().get("uploaded_image_path");

        if (img == null) {
            return "未检测到上传的胸部X光图片。如需进行肺炎影像分析，请先上传胸片图片；如有其他医疗问题，我可以为您提供医疗建议。";
        }

        // Step 1: CNN 分类
        String cnnResult = feiyanCnnTool.apply(input, toolContext);

        // Step 2: 将分类结果连同用户问题交给医疗专家
        String medicalInput = "用户问题：" + input + "\n胸片诊断分析：" + cnnResult;
            AssistantMessage response = medicalAgent.call(Map.of("input", medicalInput), RunnableConfig.builder().build());
            return response.getText().replaceAll("(?s)<think>.*?</think>\\s*", "").trim();
        } catch (GraphRunnerException e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

}
