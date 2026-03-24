package ff.pro.aichatali.config;

import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.client.HttpClient;

/**
 * 配置 Spring AI OpenAI 客户端使用 HTTP/1.1，
 * 避免 Reactor Netty 默认尝试 HTTP/2 升级（h2c）导致 vllm/uvicorn 报 400 Bad Request。
 */
@Configuration
public class HttpClientConfig {

    @Bean
    public RestClientCustomizer forceHttp11RestClientCustomizer() {
        return builder -> builder.requestFactory(new SimpleClientHttpRequestFactory());
    }

    /**
     * 强制 WebClient（用于 Spring AI OpenAI 流式调用）使用 HTTP/1.1，
     * 禁止 Reactor Netty 发送 h2c upgrade 请求。
     */
    @Bean
    public WebClientCustomizer forceHttp11WebClientCustomizer() {
        return builder -> builder.clientConnector(
                new ReactorClientHttpConnector(
                        HttpClient.create().protocol(HttpProtocol.HTTP11)
                )
        );
    }
}
