package ff.pro.aichatali.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

@Slf4j
@Service
@EnableScheduling
public class CleanupService {

    @Autowired
    private AuthService authService;

    @Autowired
    private ChatHistoryService chatHistoryService;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Scheduled(fixedDelay = 3_600_000)
    void expireTokens() {
        authService.expireAll();
    }

    @Scheduled(fixedDelay = 3_600_000)
    void cleanHistory() {
        chatHistoryService.deleteOlderThan(Duration.ofDays(7));
    }

    @Scheduled(cron = "0 0 0 * * *")
    void cleanUploads() {
        Path dir = Path.of(uploadDir);
        if (!Files.isDirectory(dir)) return;
        try (var entries = Files.list(dir)) {
            entries.filter(p -> !p.getFileName().toString().equals("rag_repo"))
                   .forEach(p -> {
                       try {
                           Files.delete(p);
                           log.info("Deleted upload: {}", p.getFileName());
                       } catch (IOException e) {
                           log.warn("Failed to delete {}: {}", p.getFileName(), e.getMessage());
                       }
                   });
        } catch (IOException e) {
            log.error("Failed to list uploads dir: {}", e.getMessage());
        }
    }
}
