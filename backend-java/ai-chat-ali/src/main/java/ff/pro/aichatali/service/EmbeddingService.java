package ff.pro.aichatali.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.model.EmbeddingUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author journey
 * @date 2026/3/25
 **/
@Service
@RequiredArgsConstructor
public class EmbeddingService {
    final EmbeddingModel embeddingModel;
    final BatchingStrategy batchingStrategy;
    static final EmbeddingOptions embedingOptions = EmbeddingOptions.builder().dimensions(1024).build();

    public List<float[]> embed(List<Document> docs){

        return embeddingModel.embed(docs, embedingOptions, batchingStrategy);
    }

    public List<Float> embed(String text){
        Document doc = new Document(text);
        return EmbeddingUtils.toList(embeddingModel.embed(List.of(doc), embedingOptions, batchingStrategy).getFirst());
    }

}
