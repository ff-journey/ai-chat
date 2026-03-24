package ff.pro.aichatali.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
  public class ParentChunkStore {
      private final Map<String, Document> store = new ConcurrentHashMap<>();
      private final Path storePath = Path.of("data/parent_chunks.json");

      @Autowired
      private ObjectMapper objectMapper;

      public void put(String id, Document doc) {
          store.put(id, doc);
          persist();
      }

      public Optional<Document> get(String id) {
          return Optional.ofNullable(store.get(id));
      }

      @PostConstruct
      public void load() {
          if (Files.exists(storePath)) {
              try {
                  Map<String, String> raw = objectMapper.readValue(
                      storePath.toFile(),
                      new TypeReference<>() {}
                  );
                  raw.forEach((k, v) -> store.put(k, new Document(v)));
              } catch (IOException e) {
                  // 启动时没有文件也没关系
              }
          }
      }

      private void persist() {
          try {
              Files.createDirectories(storePath.getParent());
              Map<String, String> raw = new HashMap<>();
              store.forEach((k, v) -> raw.put(k, v.getText()));
              objectMapper.writeValue(storePath.toFile(), raw);
          } catch (IOException e) {
              throw new RuntimeException("Failed to persist parent chunks", e);
          }
      }
  }
