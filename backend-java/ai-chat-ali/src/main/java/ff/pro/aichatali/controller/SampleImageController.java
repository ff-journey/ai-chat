package ff.pro.aichatali.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/samples")
public class SampleImageController {

    @Value("${app.samples.dir:samples}")
    private String samplesDir;

    private static final Map<String, String> CATEGORY_LABELS = Map.of(
            "NORMAL", "正常",
            "COVID", "新冠肺炎",
            "Viral_Pneumonia", "病毒型肺炎"
    );

    @GetMapping
    public List<Map<String, Object>> listSamples() throws IOException {
        Path samplesPath = Paths.get(samplesDir);
        if (!Files.exists(samplesPath)) {
            return List.of();
        }

        List<Map<String, Object>> categories = new ArrayList<>();
        try (Stream<Path> dirs = Files.list(samplesPath)) {
            dirs.filter(Files::isDirectory).sorted().forEach(dir -> {
                String category = dir.getFileName().toString();
                try (Stream<Path> files = Files.list(dir)) {
                    List<Map<String, String>> images = files
                            .filter(f -> {
                                String name = f.getFileName().toString().toLowerCase();
                                return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png");
                            })
                            .sorted()
                            .map(f -> Map.of(
                                    "filename", f.getFileName().toString(),
                                    "label", f.getFileName().toString()
                            ))
                            .toList();

                    if (!images.isEmpty()) {
                        categories.add(Map.of(
                                "category", category,
                                "label", CATEGORY_LABELS.getOrDefault(category, category),
                                "images", images
                        ));
                    }
                } catch (IOException e) {
                    // skip this category
                }
            });
        }
        return categories;
    }

    @GetMapping("/{category}/{filename}")
    public ResponseEntity<Resource> getSampleImage(
            @PathVariable String category,
            @PathVariable String filename) {
        Path filePath = Paths.get(samplesDir, category, filename);
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            return ResponseEntity.notFound().build();
        }

        // Validate path traversal
        try {
            Path normalized = filePath.toRealPath();
            Path samplesRoot = Paths.get(samplesDir).toRealPath();
            if (!normalized.startsWith(samplesRoot)) {
                return ResponseEntity.badRequest().build();
            }
        } catch (IOException e) {
            return ResponseEntity.badRequest().build();
        }

        String lower = filename.toLowerCase();
        MediaType mediaType = lower.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;

        Resource resource = new FileSystemResource(filePath);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .body(resource);
    }

}
