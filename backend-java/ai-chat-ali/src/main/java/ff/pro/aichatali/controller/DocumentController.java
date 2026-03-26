package ff.pro.aichatali.controller;

import ff.pro.aichatali.service.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Document (RAG knowledge base) management endpoints.
 */
@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
@Slf4j
public class DocumentController {

    private final DocumentService documentService;

    /**
     * POST /documents/upload
     * Uploads a PDF and triggers hierarchical RAG indexing.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
        }
        try {
            DocumentService.ProcessResult result = documentService.processDocument(file);
            return ResponseEntity.ok(Map.of(
                    "filename", result.filename(),
                    "chunks", result.chunks(),
                    "url", result.url(),
                    "message", "Document indexed successfully"
            ));
        } catch (Exception e) {
            log.error("Document upload failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to process document: " + e.getMessage()));
        }
    }

    /**
     * GET /documents
     * Lists all indexed documents in the rag_repo directory.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listDocuments() {
        return ResponseEntity.ok(Map.of("documents", documentService.listDocuments()));
    }

    /**
     * DELETE /documents/{filename}
     * Deletes the document file and its Milvus vector chunks.
     */
    @DeleteMapping("/{filename}")
    public ResponseEntity<Map<String, Object>> deleteDocument(@PathVariable String filename) {
        boolean deleted = documentService.deleteDocument(filename);
        if (deleted) {
            return ResponseEntity.ok(Map.of("message", "Document deleted: " + filename));
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}
