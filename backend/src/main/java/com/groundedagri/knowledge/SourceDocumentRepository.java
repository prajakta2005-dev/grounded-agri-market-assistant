package com.groundedagri.knowledge;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class SourceDocumentRepository {
    private static final String SELECT_COLUMNS = """
            id, title, organization, document_date, accessed_date, url, crops, content,
            category, crop_filter, region_filter, managed_by_user, source_type, file_name
            """;

    private static final RowMapper<SourceDocumentEntry> ENTRY_MAPPER = (rs, rowNum) ->
            new SourceDocumentEntry(
                    new SourceDocument(
                            rs.getString("id"),
                            rs.getString("title"),
                            rs.getString("organization"),
                            rs.getString("document_date"),
                            rs.getString("accessed_date"),
                            rs.getString("url"),
                            rs.getString("crops"),
                            rs.getString("content"),
                            rs.getString("source_type"),
                            rs.getString("file_name")),
                    rs.getString("category"),
                    rs.getString("crop_filter"),
                    rs.getString("region_filter"),
                    rs.getBoolean("managed_by_user"));

    private final JdbcTemplate jdbcTemplate;

    public SourceDocumentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SourceDocumentEntry> findAllEntries() {
        return jdbcTemplate.query("SELECT " + SELECT_COLUMNS + " FROM source_document ORDER BY id", ENTRY_MAPPER);
    }

    public SourceDocumentEntry findEntryById(String id) {
        return jdbcTemplate.query("SELECT " + SELECT_COLUMNS + " FROM source_document WHERE id = ?",
                ENTRY_MAPPER, id).stream().findFirst().orElse(null);
    }

    public List<ManagedSourceDocument> findAdminSources() {
        return jdbcTemplate.query("SELECT " + SELECT_COLUMNS + ", "
                        + "EXISTS (SELECT 1 FROM source_vector_state state WHERE state.source_id = source_document.id) AS indexed "
                        + "FROM source_document ORDER BY managed_by_user DESC, title",
                (rs, rowNum) -> {
                    SourceDocumentEntry entry = ENTRY_MAPPER.mapRow(rs, rowNum);
                    return new ManagedSourceDocument(entry.document(), entry.category(), entry.cropFilter(),
                            entry.regionFilter(), entry.managedByUser(), rs.getBoolean("indexed"));
                });
    }

    public void insertManaged(SourceDocumentEntry entry) {
        SourceDocument document = entry.document();
        jdbcTemplate.update("INSERT INTO source_document "
                        + "(id, title, organization, document_date, accessed_date, url, crops, content, "
                        + "category, crop_filter, region_filter, managed_by_user, source_type, file_name) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, true, ?, ?)",
                document.id(), document.title(), document.organization(), document.documentDate(),
                document.accessedDate(), document.url(), document.crops(), document.content(),
                entry.category(), entry.cropFilter(), entry.regionFilter(), document.sourceType(), document.fileName());
    }

    public boolean updateManaged(SourceDocumentEntry entry) {
        SourceDocument document = entry.document();
        return jdbcTemplate.update("UPDATE source_document SET title = ?, organization = ?, document_date = ?, "
                        + "accessed_date = ?, url = ?, crops = ?, content = ?, category = ?, crop_filter = ?, "
                        + "region_filter = ?, source_type = ?, file_name = ? "
                        + "WHERE id = ? AND managed_by_user = true",
                document.title(), document.organization(), document.documentDate(), document.accessedDate(),
                document.url(), document.crops(), document.content(), entry.category(), entry.cropFilter(),
                entry.regionFilter(), document.sourceType(), document.fileName(), document.id()) == 1;
    }

    public boolean deleteManaged(String id) {
        return jdbcTemplate.update("DELETE FROM source_document WHERE id = ? AND managed_by_user = true", id) == 1;
    }

    public List<SourceDocument> findAll() {
        return findAllEntries().stream().map(SourceDocumentEntry::document).toList();
    }

    public List<SourceDocument> findByIdsInOrder(List<String> ids) {
        if (ids.isEmpty()) return List.of();
        Map<String, SourceDocument> byId = new LinkedHashMap<>();
        findAllEntries().forEach(entry -> byId.put(entry.document().id(), entry.document()));
        List<SourceDocument> result = new ArrayList<>();
        for (String id : ids) {
            SourceDocument document = byId.get(id);
            if (document != null) result.add(document);
        }
        return List.copyOf(result);
    }

    public List<SourceVectorState> findVectorStates(String sourceId) {
        return jdbcTemplate.query(
                "SELECT source_id, chunk_index, vector_id, content_hash FROM source_vector_state WHERE source_id = ? ORDER BY chunk_index",
                (rs, rowNum) -> new SourceVectorState(
                        rs.getString("source_id"),
                        rs.getInt("chunk_index"),
                        rs.getObject("vector_id", UUID.class),
                        rs.getString("content_hash")),
                sourceId);
    }

    public List<SourceVectorState> findAllVectorStates() {
        return jdbcTemplate.query(
                "SELECT source_id, chunk_index, vector_id, content_hash FROM source_vector_state ORDER BY source_id, chunk_index",
                (rs, rowNum) -> new SourceVectorState(
                        rs.getString("source_id"),
                        rs.getInt("chunk_index"),
                        rs.getObject("vector_id", UUID.class),
                        rs.getString("content_hash")));
    }

    public void replaceVectorStates(String sourceId, List<SourceVectorState> states) {
        jdbcTemplate.update("DELETE FROM source_vector_state WHERE source_id = ?", sourceId);
        if (states.isEmpty()) return;
        jdbcTemplate.batchUpdate(
                "INSERT INTO source_vector_state (source_id, chunk_index, vector_id, content_hash) VALUES (?, ?, ?, ?)",
                states,
                states.size(),
                (statement, state) -> {
                    statement.setString(1, sourceId);
                    statement.setInt(2, state.chunkIndex());
                    statement.setObject(3, state.vectorId());
                    statement.setString(4, state.contentHash());
                });
    }

    public void deleteVectorStates(String sourceId) {
        jdbcTemplate.update("DELETE FROM source_vector_state WHERE source_id = ?", sourceId);
    }
}
