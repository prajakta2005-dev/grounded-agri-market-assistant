package com.groundedagri.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class SourceVectorIndexer {
    private static final Logger logger = LoggerFactory.getLogger(SourceVectorIndexer.class);
    private static final int MAX_CHUNK_CHARS = 900;

    private final SourceDocumentRepository sourceRepository;
    private final PgVectorDocumentRepository vectorRepository;
    private final NvidiaEmbeddingClient embeddingClient;

    public SourceVectorIndexer(SourceDocumentRepository sourceRepository,
                               PgVectorDocumentRepository vectorRepository,
                               NvidiaEmbeddingClient embeddingClient) {
        this.sourceRepository = sourceRepository;
        this.vectorRepository = vectorRepository;
        this.embeddingClient = embeddingClient;
    }

    public int indexAll(List<SourceDocumentEntry> entries) {
        Set<String> activeSourceIds = new HashSet<>();
        int indexed = 0;
        for (SourceDocumentEntry entry : entries) {
            String sourceId = entry.document().id();
            activeSourceIds.add(sourceId);
            try {
                if (index(entry)) indexed++;
            } catch (Exception exception) {
                logIndexFailure(sourceId, exception);
            }
        }

        Map<String, List<SourceVectorState>> bySource = new LinkedHashMap<>();
        for (SourceVectorState state : sourceRepository.findAllVectorStates()) {
            bySource.computeIfAbsent(state.sourceId(), ignored -> new ArrayList<>()).add(state);
        }
        for (Map.Entry<String, List<SourceVectorState>> entry : bySource.entrySet()) {
            if (activeSourceIds.contains(entry.getKey())) continue;
            try {
                vectorRepository.removeSourceVectors(entry.getKey(),
                        entry.getValue().stream().map(state -> state.vectorId().toString()).toList());
            } catch (Exception exception) {
                logger.warn("Could not remove stale vectors for source {} ({})", entry.getKey(),
                        exception.getClass().getSimpleName());
            }
        }
        return indexed;
    }

    public boolean index(SourceDocumentEntry entry) {
        SourceDocument source = entry.document();
        List<String> chunks = chunk(source.title() + "\n" + source.content());
        List<SourceVectorState> previous = sourceRepository.findVectorStates(source.id());
        List<SourceVectorState> next = expectedStates(entry, chunks);
        if (sameIndex(previous, next)) return false;

        List<float[]> embeddings = embeddingClient.embed(chunks, "passage");
        if (embeddings.size() != chunks.size()) {
            throw new IllegalStateException("Embedding service returned a different number of vectors than passages.");
        }
        List<PgVectorDocumentRepository.PassageVector> vectors = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            vectors.add(new PgVectorDocumentRepository.PassageVector(
                    next.get(i).vectorId(), chunks.get(i), metadata(entry, i), embeddings.get(i)));
        }

        Set<String> idsToReplace = new LinkedHashSet<>();
        previous.forEach(state -> idsToReplace.add(state.vectorId().toString()));
        next.forEach(state -> idsToReplace.add(state.vectorId().toString()));
        vectorRepository.replaceSourceVectors(source.id(), List.copyOf(idsToReplace), vectors, next);
        return true;
    }

    public boolean isIndexed(SourceDocumentEntry entry) {
        List<SourceVectorState> current = sourceRepository.findVectorStates(entry.document().id());
        List<SourceVectorState> expected = expectedStates(entry,
                chunk(entry.document().title() + "\n" + entry.document().content()));
        return sameIndex(current, expected);
    }

    public void remove(SourceDocumentEntry entry) {
        List<String> ids = sourceRepository.findVectorStates(entry.document().id()).stream()
                .map(state -> state.vectorId().toString()).toList();
        vectorRepository.removeSourceVectors(entry.document().id(), ids);
    }

    private List<SourceVectorState> expectedStates(SourceDocumentEntry entry, List<String> chunks) {
        List<SourceVectorState> states = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            states.add(new SourceVectorState(entry.document().id(), i, stableId(entry.document().id(), i),
                    contentHash(entry, chunks.get(i))));
        }
        return states;
    }

    private Map<String, Object> metadata(SourceDocumentEntry entry, int chunkIndex) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source_id", entry.document().id());
        metadata.put("title", entry.document().title());
        metadata.put("url", entry.document().url());
        metadata.put("category", entry.category());
        metadata.put("crop", entry.cropFilter());
        metadata.put("region", entry.regionFilter());
        metadata.put("chunk_index", chunkIndex);
        return metadata;
    }

    private void logIndexFailure(String sourceId, Exception exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof RestClientResponseException responseException) {
                logger.warn("Could not index source {} (embedding service HTTP {})", sourceId,
                        responseException.getStatusCode().value());
                return;
            }
            cause = cause.getCause();
        }
        logger.warn("Could not index source {} ({})", sourceId, exception.getClass().getSimpleName());
    }

    private boolean sameIndex(List<SourceVectorState> previous, List<SourceVectorState> next) {
        if (previous.size() != next.size()) return false;
        for (int i = 0; i < next.size(); i++) {
            SourceVectorState oldState = previous.get(i);
            SourceVectorState newState = next.get(i);
            if (oldState.chunkIndex() != newState.chunkIndex()
                    || !oldState.contentHash().equals(newState.contentHash())
                    || !oldState.vectorId().equals(newState.vectorId())) return false;
        }
        return true;
    }

    private List<String> chunk(String text) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String sentence : text.split("(?<=[.!?])\\s+")) {
            if (current.length() > 0 && current.length() + sentence.length() + 1 > MAX_CHUNK_CHARS) {
                chunks.add(current.toString().trim());
                current.setLength(0);
            }
            if (sentence.length() > MAX_CHUNK_CHARS) {
                for (int start = 0; start < sentence.length(); start += MAX_CHUNK_CHARS) {
                    chunks.add(sentence.substring(start, Math.min(start + MAX_CHUNK_CHARS, sentence.length())).trim());
                }
            } else {
                if (current.length() > 0) current.append(' ');
                current.append(sentence);
            }
        }
        if (current.length() > 0) chunks.add(current.toString().trim());
        return chunks.stream().filter(part -> !part.isBlank()).toList();
    }

    private UUID stableId(String sourceId, int chunkIndex) {
        return UUID.nameUUIDFromBytes(("agri-source:" + sourceId + ":" + chunkIndex)
                .getBytes(StandardCharsets.UTF_8));
    }

    private String contentHash(SourceDocumentEntry entry, String text) {
        String value = String.join("\n", embeddingClient.model(), Integer.toString(embeddingClient.dimensions()),
                entry.document().id(), entry.document().url(), entry.category(), entry.cropFilter(),
                entry.regionFilter(), text);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
