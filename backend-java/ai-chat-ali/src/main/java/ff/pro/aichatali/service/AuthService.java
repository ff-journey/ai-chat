package ff.pro.aichatali.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    private record TokenInfo(int userId, Instant expiry) {}

    private final Map<String, TokenInfo> tokenStore = new ConcurrentHashMap<>();
    private final Map<Integer, String> userTokenMap = new ConcurrentHashMap<>();

    /** Issues a new token for userId, kicking out any existing session. */
    public String issueToken(int userId) {
        revokeByUserId(userId);
        String token = UUID.randomUUID().toString().replace("-", "");
        Instant expiry = Instant.now().plusSeconds(7200); // 2h
        tokenStore.put(token, new TokenInfo(userId, expiry));
        userTokenMap.put(userId, token);
        return token;
    }

    /** Returns the userId if the token is valid and unexpired. */
    public Optional<Integer> validateAndGetUserId(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        TokenInfo info = tokenStore.get(token);
        if (info == null) return Optional.empty();
        if (Instant.now().isAfter(info.expiry())) {
            tokenStore.remove(token);
            userTokenMap.remove(info.userId());
            return Optional.empty();
        }
        return Optional.of(info.userId());
    }

    public void revokeByUserId(int userId) {
        String oldToken = userTokenMap.remove(userId);
        if (oldToken != null) {
            tokenStore.remove(oldToken);
        }
    }

    /** Called by CleanupService periodically to purge expired tokens. */
    public void expireAll() {
        Instant now = Instant.now();
        tokenStore.entrySet().removeIf(entry -> {
            if (now.isAfter(entry.getValue().expiry())) {
                userTokenMap.remove(entry.getValue().userId());
                return true;
            }
            return false;
        });
    }
}
