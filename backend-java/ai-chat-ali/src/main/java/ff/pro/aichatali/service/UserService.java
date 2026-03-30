package ff.pro.aichatali.service;

import ff.pro.aichatali.common.UserSimpleDto;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class UserService {

    private record UserRecord(int id, String username, String password) {}

    private final Map<Integer, UserRecord> usersById = new ConcurrentHashMap<>();
    private final Map<String, Integer> usernameToId = new ConcurrentHashMap<>();
    private final AtomicInteger idCounter = new AtomicInteger(1);

    public UserService() {
        // Preset admin account with id=0
        UserRecord admin = new UserRecord(0, "admin", "admin123");
        usersById.put(0, admin);
        usernameToId.put("admin", 0);
    }

    public UserSimpleDto register(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalArgumentException("用户名和密码不能为空");
        }
        int newId = idCounter.getAndIncrement();
        Integer prev = usernameToId.putIfAbsent(username, newId);
        if (prev != null) {
            throw new IllegalStateException("用户名已存在");
        }
        usersById.put(newId, new UserRecord(newId, username, password));
        return new UserSimpleDto(newId, username, username);
    }

    public UserSimpleDto login(String username, String password) {
        if (username == null || password == null) {
            throw new IllegalArgumentException("用户名和密码不能为空");
        }
        Integer id = usernameToId.get(username);
        if (id == null) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        UserRecord record = usersById.get(id);
        if (!password.equals(record.password())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        return new UserSimpleDto(record.id(), record.username(), record.username());
    }

    public UserSimpleDto getById(int id) {
        UserRecord record = usersById.get(id);
        if (record == null) return new UserSimpleDto(id);
        return new UserSimpleDto(record.id(), record.username(), record.username());
    }

    public boolean exists(String username) {
        return usernameToId.containsKey(username);
    }
}
