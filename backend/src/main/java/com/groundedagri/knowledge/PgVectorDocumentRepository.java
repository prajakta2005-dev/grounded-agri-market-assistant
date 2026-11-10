package com.groundedagri.knowledge;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.StringJoiner;

@Repository
public class PgVectorDocumentRepository {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final JdbcTemplate jdbcTemplate;
    private final SourceDocumentRepository sourceRepository;

    public PgVectorDocumentRepository(JdbcTemplate jdbcTemplate,
                                      SourceDocumentRepository sourceRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.sourceRepository = sourceRepository;
    }

    @Transactional
    public void replaceSourceVectors(String sourceId, List<String> idsToDelete,
                                     List<PassageVector> vectors, List<SourceVectorState> states) {
        deleteVectors(idsToDelete);
        if (!vectors.isEmpty()) {
            List<StoredVector> storedVectors = vectors.stream().map(this::serialize).toList();
            jdbcTemplate.batchUpdate(
                    "INSERT INTO vector_store (id, content, metadata, embedding) VALUES (?::uuid, ?, ?::json, ?::vector) "
                            + "ON CONFLICT (id) DO UPDATE SET content = EXCLUDED.content, "
                            + "metadata = EXCLUDED.metadata, embedding = EXCLUDED.embedding",
                    storedVectors,
                    storedVectors.size(),
                    (statement, vector) -> {
                        statement.setString(1, vector.id());
                        statement.setString(2, vector.content());
                        statement.setString(3, vector.metadataJson());
                        statement.setString(4, vector.embeddingLiteral());
                    });
        }
        sourceRepository.replaceVectorStates(sourceId, states);
    }

    @Transactional
    public void removeSourceVectors(String sourceId, List<String> ids) {
        deleteVectors(ids);
        sourceRepository.deleteVectorStates(sourceId);
    }

    private void deleteVectors(List<String> ids) {
        if (ids.isEmpty()) return;
        jdbcTemplate.batchUpdate("DELETE FROM vector_store WHERE id = ?::uuid", ids, ids.size(),
                (statement, id) -> statement.setString(1, id));
    }

    private StoredVector serialize(PassageVector vector) {
        try {
            return new StoredVector(vector.id().toString(), vector.content(),
                    OBJECT_MAPPER.writeValueAsString(vector.metadata()), formatVector(vector.embedding()));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Could not serialize vector metadata.", exception);
        }
    }

    private String formatVector(float[] values) {
        StringJoiner vector = new StringJoiner(",", "[", "]");
        for (float value : values) {
            if (!Float.isFinite(value)) throw new IllegalArgumentException("Vector contains a non-finite value.");
            vector.add(Float.toString(value));
        }
        return vector.toString();
    }

    public record PassageVector(UUID id, String content, Map<String, Object> metadata, float[] embedding) {}

    private record StoredVector(String id, String content, String metadataJson, String embeddingLiteral) {}
}
