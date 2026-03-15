package ff.pro.aichatali.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * 将运行时目录下的 uploads/ 和 samples/ 作为静态资源对外提供，
 * 访问路径：/uploads/** 和 /samples/**。
 */
@Configuration
public class CustomWebConfig implements WebMvcConfigurer {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Value("${app.samples.dir:samples}")
    private String samplesDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String workDir = System.getProperty("user.dir");

        String uploadLocation = Paths.get(workDir, uploadDir).toUri().toString();
        if (!uploadLocation.endsWith("/")) uploadLocation += "/";
        registry.addResourceHandler("/"+uploadDir+"/**")
                .addResourceLocations(uploadLocation);

        String samplesLocation = Paths.get(workDir, samplesDir).toUri().toString();
        if (!samplesLocation.endsWith("/")) samplesLocation += "/";
        registry.addResourceHandler("/"+samplesDir+"/**")
                .addResourceLocations(samplesLocation);
    }
}
