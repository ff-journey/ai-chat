package ff.pro.aichatali.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserService {

    private final Map<String, String> users = new ConcurrentHashMap<>();

    public boolean register(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return false;
        }
        return users.putIfAbsent(username, password) == null;
    }

    public boolean login(String username, String password) {
        if (username == null || password == null) {
            return false;
        }
        return password.equals(users.get(username));
    }

    public boolean exists(String username) {
        return users.containsKey(username);
    }

}
