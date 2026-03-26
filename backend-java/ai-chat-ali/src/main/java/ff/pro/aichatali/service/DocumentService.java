package ff.pro.aichatali.service;

import com.google.common.collect.Lists;
import ff.pro.aichatali.common.HierarchicalDocSplitter;
import ff.pro.aichatali.common.ThreadPoolHelper;
import ff.pro.aichatali.controller.dto.L3ChunkDto;
import ff.pro.aichatali.repo.RagChunkMapper;
import ff.pro.aichatali.repo.RagChunkPo;
import ff.pro.aichatali.service.rag.BM25Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.EmbeddingUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Handles PDF document ingestion: parse → hierarchical split → embed → index.
 * Used by DocumentController for the /documents/* endpoints.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    private static final String RAG_REPO = "rag_repo";

    final HierarchicalDocSplitter hierarchicalDocSplitter;
    final RagChunkMapper ragChunkMapper;
    final BM25Service bm25Service;
    final EmbeddingService embeddingService;

    public record ProcessResult(String filename, int chunks, String url) {}

    /** storedFilename = UUID (36 chars) + "_" + originalFilename */
    public record DocumentInfo(String filename, String originalFilename, String fileType, long sizeBytes, String url) {}

    /**
     * Parses, splits, embeds and indexes the given PDF file.
     * @return processing stats
     */
    public ProcessResult processDocument(MultipartFile file) {
        String savedPath = saveFile(file);
        String originalFilename = file.getOriginalFilename();
        String storedFilename = Paths.get(savedPath).getFileName().toString();
        String fileUrl = "/uploads/" + RAG_REPO + "/" + storedFilename;

        List<Document> pages = parsePdfByPage(file);
        List<Document> cleanPages = removeReferencesPages(pages);

        List<L3ChunkDto> l3chunks = cleanPages.parallelStream()
                .flatMap(page -> {
                    HierarchicalDocSplitter.HierarchyResult result =
                            hierarchicalDocSplitter.split(page, originalFilename);
                    result.l1().forEach(l1 -> ragChunkMapper.insertParentChunk(l1.getId(), l1));
                    result.l2().forEach(l2 -> ragChunkMapper.insertParentChunk(l2.getId(), l2));
                    return result.l3().stream()
                            .map(l3 -> new L3ChunkDto(new RagChunkPo(l3, null), l3));
                })
                .toList();

        List<CompletableFuture<Void>> futures = Lists.partition(l3chunks, 10).stream()
                .map(batch -> CompletableFuture.runAsync(
                        () -> batchEmbed(batch), ThreadPoolHelper.EXECUTOR_SERVICE))
                .toList();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        ragChunkMapper.batchInsert(l3chunks.stream().map(L3ChunkDto::getRagChunk).toList());
        bm25Service.addDocuments(l3chunks.stream().map(L3ChunkDto::getDocument).toList());

        log.info("Indexed document '{}': {} L3 chunks", originalFilename, l3chunks.size());
        return new ProcessResult(originalFilename, l3chunks.size(), fileUrl);
    }

    /**
     * Lists all documents in the rag_repo upload directory.
     */
    public List<DocumentInfo> listDocuments() {
        Path dir = ragRepoDir();
        if (!Files.exists(dir)) return List.of();
        try {
            return Files.list(dir)
                    .filter(Files::isRegularFile)
                    .map(p -> {
                        try {
                            long size = Files.size(p);
                            String storedName = p.getFileName().toString();
                            // UUID prefix is 36 chars + "_" = 37 chars
                            String original = storedName.length() > 37 ? storedName.substring(37) : storedName;
                            String ext = original.contains(".")
                                    ? original.substring(original.lastIndexOf('.') + 1).toUpperCase()
                                    : "FILE";
                            String url = "/uploads/" + RAG_REPO + "/" + storedName;
                            return new DocumentInfo(storedName, original, ext, size, url);
                        } catch (IOException e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.warn("Failed to list documents: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Deletes the physical file and removes its chunks from Milvus.
     * BM25 index is in-memory and survives only until restart — no per-file deletion.
     * @param filename the stored filename (as returned by listDocuments)
     * @return true if file existed and was deleted
     */
    public boolean deleteDocument(String filename) {
        Path filePath = ragRepoDir().resolve(filename);
        if (!Files.exists(filePath)) return false;
        try {
            Files.delete(filePath);
            // Remove Milvus chunks indexed under this filename as sourceId
            ragChunkMapper.deleteBySourceId(filename);
            log.info("Deleted document '{}' and its vector chunks", filename);
            return true;
        } catch (IOException e) {
            log.error("Failed to delete document '{}': {}", filename, e.getMessage());
            return false;
        }
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private String saveFile(MultipartFile file) {
        try {
            Path dir = ragRepoDir();
            Files.createDirectories(dir);
            String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
            Path dest = dir.resolve(filename);
            file.transferTo(dest.toFile());
            return dest.toAbsolutePath().toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to save uploaded document", e);
        }
    }

    private Path ragRepoDir() {
        return Paths.get(System.getProperty("user.dir"), uploadDir, RAG_REPO);
    }

    private void batchEmbed(List<L3ChunkDto> batch) {
        List<Document> docs = batch.stream().map(L3ChunkDto::getDocument).toList();
        List<float[]> embeddings = embeddingService.embed(docs);
        for (int i = 0; i < docs.size(); i++) {
            Objects.requireNonNull(batch.get(i)).getRagChunk()
                    .setEmbedding(EmbeddingUtils.toList(embeddings.get(i)));
        }
    }

    private List<Document> parsePdfByPage(MultipartFile file) {
        List<Document> result = new ArrayList<>();
        try (PDDocument pdf = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            int totalPages = pdf.getNumberOfPages();
            for (int i = 1; i <= totalPages; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String text = stripper.getText(pdf);
                if (text != null && !text.isBlank()) {
                    result.add(new Document(sanitizeText(text), Map.of("page", i)));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("PDF 解析失败", e);
        }
        return result;
    }

    private String sanitizeText(String text) {
        if (text == null) return "";
        return text.chars()
                .filter(c -> !Character.isSurrogate((char) c))
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString()
                .replaceAll("(?<=[\u4e00-\u9fa5]) +(?=[\u4e00-\u9fa5])", "")
                .replaceAll("(?<=[\u4e00-\u9fa5]) +(?=[a-zA-Z0-9])", "")
                .replaceAll("(?<=[a-zA-Z0-9]) +(?=[\u4e00-\u9fa5])", "")
                .replaceAll(" {2,}", " ")
                .replaceAll("\n{3,}", "\n\n")
                .replaceAll(" +\n", "\n");
    }

    private List<Document> removeReferencesPages(List<Document> documents) {
        List<String> keywords = List.of("参考文献", "References", "REFERENCES");
        for (int i = 0; i < documents.size(); i++) {
            String text = documents.get(i).getText();
            if (text == null) continue;
            boolean isRefPage = text.lines()
                    .map(String::strip)
                    .anyMatch(keywords::contains);
            if (isRefPage) {
                log.info("PDF截取: 总{}页, 保留{}页(去除参考文献)", documents.size(), i);
                return documents.subList(0, i);
            }
        }
        return documents;
    }
}
