package ff.pro.aichatali.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import ff.pro.aichatali.common.RequestContext;
import ff.pro.aichatali.common.UserSimpleDto;
import ff.pro.aichatali.service.AuthService;
import ff.pro.aichatali.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.Optional;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserService userService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = request.getHeader("X-Token");
        Optional<Integer> userIdOpt = authService.validateAndGetUserId(token);
        if (userIdOpt.isEmpty()) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    objectMapper.writeValueAsString(Map.of("code", 401, "message", "未登录"))
            );
            return false;
        }
        UserSimpleDto userInfo = userService.getById(userIdOpt.get());
        RequestContext ctx = RequestContext.getRequestContext();
        ctx.setUserInfo(userInfo);  // set userInfo first so getDeviceLibraryId() resolves admin correctly
        ctx.setRequest(request);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        RequestContext.removeRequestContext();
    }
}
