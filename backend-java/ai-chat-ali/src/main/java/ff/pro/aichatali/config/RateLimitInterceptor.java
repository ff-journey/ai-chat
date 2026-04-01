package ff.pro.aichatali.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import ff.pro.aichatali.common.RequestContext;
import ff.pro.aichatali.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${rate-limit.reminder-interval:20}")
    private int reminderInterval;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        long userId = RequestContext.getRequestContext().getUserInfo().getId();

        // admin (id=0) is not rate limited
        if (userId == 0) {
            return true;
        }

        RateLimitService.RateLimitResult result = rateLimitService.checkAndRecord(userId);

        // Always set rate limit headers
        response.setHeader("X-RateLimit-Used", String.valueOf(result.used()));
        response.setHeader("X-RateLimit-Limit", String.valueOf(result.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.limit() - result.used()));

        if (!result.allowed()) {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    objectMapper.writeValueAsString(Map.of(
                            "code", 429,
                            "message", "对话次数已用尽（" + result.limit() + " 轮/2小时），请稍后再试"
                    ))
            );
            return false;
        }

        // Reminder at every reminderInterval (20, 40, ...)
        if (result.used() > 0 && result.used() % reminderInterval == 0 && result.used() < result.limit()) {
            response.setHeader("X-RateLimit-Reminder", "true");
        }

        return true;
    }
}
