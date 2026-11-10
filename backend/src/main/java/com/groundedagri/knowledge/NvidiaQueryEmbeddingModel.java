package com.groundedagri.knowledge;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Primary
public class NvidiaQueryEmbeddingModel implements EmbeddingModel {
    private final NvidiaEmbeddingClient embeddingClient;

    public NvidiaQueryEmbeddingModel(NvidiaEmbeddingClient embeddingClient) {
        this.embeddingClient = embeddingClient;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> inputs = request.getInstructions();
        List<float[]> vectors = embeddingClient.embed(inputs, "query");
        List<Embedding> embeddings = new ArrayList<>(vectors.size());
        for (int i = 0; i < vectors.size(); i++) {
            embeddings.add(new Embedding(vectors.get(i), i));
        }
        EmbeddingResponseMetadata metadata = new EmbeddingResponseMetadata();
        metadata.setModel(embeddingClient.model());
        return new EmbeddingResponse(embeddings, metadata);
    }

    @Override
    public float[] embed(Document document) {
        String text = getEmbeddingContent(document);
        return embeddingClient.embed(List.of(text), "query").getFirst();
    }

    @Override
    public int dimensions() {
        return embeddingClient.dimensions();
    }
}
