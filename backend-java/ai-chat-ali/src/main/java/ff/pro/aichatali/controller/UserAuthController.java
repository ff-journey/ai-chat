package ff.pro.aichatali.controller;

import ff.pro.aichatali.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class UserAuthController {

    private final UserService userService;

    public UserAuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return Map.of("success", false, "message", "Username and password are required");
        }
        if (userService.register(username, password)) {
            return Map.of("success", true, "message", "Registration successful", "userId", username);
        }
        return Map.of("success", false, "message", "Username already exists");
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        if (userService.login(username, password)) {
            return Map.of("success", true, "message", "Login successful", "userId", username);
        }
        return Map.of("success", false, "message", "Invalid username or password");
    }

}
