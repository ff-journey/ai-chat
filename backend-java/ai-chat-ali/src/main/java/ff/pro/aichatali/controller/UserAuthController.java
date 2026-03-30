package ff.pro.aichatali.controller;

import ff.pro.aichatali.common.UserSimpleDto;
import ff.pro.aichatali.service.AuthService;
import ff.pro.aichatali.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class UserAuthController {

    private final UserService userService;
    private final AuthService authService;

    public UserAuthController(UserService userService, AuthService authService) {
        this.userService = userService;
        this.authService = authService;
    }

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        try {
            UserSimpleDto user = userService.register(username, password);
            String token = authService.issueToken((int) user.getId());
            return Map.of("success", true, "token", token,
                    "userId", user.getId(), "username", user.getUsername());
        } catch (Exception e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        try {
            UserSimpleDto user = userService.login(username, password);
            String token = authService.issueToken((int) user.getId());
            return Map.of("success", true, "token", token,
                    "userId", user.getId(), "username", user.getUsername());
        } catch (Exception e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }
}
