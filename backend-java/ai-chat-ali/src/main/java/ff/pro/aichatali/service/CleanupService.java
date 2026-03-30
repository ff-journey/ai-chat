package ff.pro.aichatali.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@EnableScheduling
public class CleanupService {

    @Autowired
    private AuthService authService;

    @Autowired
    private ChatHistoryService chatHistoryService;

    @Scheduled(fixedDelay = 3_600_000)
    void expireTokens() {
        authService.expireAll();
    }

    @Scheduled(fixedDelay = 3_600_000)
    void cleanHistory() {
        chatHistoryService.deleteOlderThan(Duration.ofDays(7));
    }
}
