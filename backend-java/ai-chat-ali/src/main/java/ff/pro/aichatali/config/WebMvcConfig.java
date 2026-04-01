package ff.pro.aichatali.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * 将运行时目录下的 uploads/ 和 samples/ 作为静态资源对外提供，
 * 访问路径：/uploads/** 和 /samples/**。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Value("${app.samples.dir:samples}")
    private String samplesDir;

    @Autowired
    private AuthInterceptor authInterceptor;

    @Autowired
    private RateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .order(0)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/api/auth/**",
                        "/sse/**",
                        "/",
                        "/index.html",
                        "/*.js", "/*.css", "/*.ico",
                        "/media/**",
                        "/uploads/**",
                        "/samples/**",
                        "/api/samples/**"
                );
        registry.addInterceptor(rateLimitInterceptor)
                .order(1)
                .addPathPatterns("/chat/**");
    }

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

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("mvc-async-");
        executor.initialize();
        configurer.setTaskExecutor(executor);
        configurer.setDefaultTimeout(300_000L); // 300秒，适合流式场景
    }
}
