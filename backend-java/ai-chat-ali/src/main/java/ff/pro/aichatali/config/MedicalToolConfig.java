package ff.pro.aichatali.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Data
@Configuration
@ConfigurationProperties(prefix = "medical")
public class MedicalToolConfig {

    private CnnConfig cnn = new CnnConfig();

    private VllmConfig vllm = new VllmConfig();

    @Data
    public static class CnnConfig {
        private String url = "http://localhost:5000/api/pneumonia/predict";
        private boolean enabled = true;
    }

    @Data
    public static class VllmConfig {
        private String url = "http://localhost:8000/v1/chat/completions";
        private String model = "qwen3-medical";
        private boolean enabled = true;
    }

    @Bean
    public RestTemplate medicalRestTemplate() {
        return new RestTemplate();
    }

}
