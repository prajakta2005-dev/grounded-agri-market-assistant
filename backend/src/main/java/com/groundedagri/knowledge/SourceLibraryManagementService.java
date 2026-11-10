package com.groundedagri.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class SourceLibraryManagementService {
    private static final Logger logger = LoggerFactory.getLogger(SourceLibraryManagementService.class);
    private static final int MAX_CONTENT_CHARS = 180_000;
    private static final Set<String> CATEGORIES = Set.of(
            "crop_guidance", "crop_forecast", "market_reference", "market_inventory");
    private static final Set<String> CROPS = Set.of("rice", "maize", "tomato", "all", "market");
    private static final Set<String> REGIONS = Set.of("tamil_nadu", "india", "global");

    private final SourceDocumentRepository sourceRepository;
    private final SourceVectorIndexer indexer;
    private final SourceContentImporter importer;

    public SourceLibraryManagementService(SourceDocumentRepository sourceRepository,
                                          SourceVectorIndexer indexer,
                                          SourceContentImporter importer) {
        this.sourceRepository = sourceRepository;
        this.indexer = indexer;
        this.importer = importer;
    }

    public List<ManagedSourceDocument> list() {
        return sourceRepository.findAllEntries().stream()
                .map(entry -> new ManagedSourceDocument(entry.document(), entry.category(), entry.cropFilter(),
                        entry.regionFilter(), entry.managedByUser(), indexer.isIndexed(entry)))
                .toList();
    }

    public SourceImportPreview previewPdf(byte[] bytes, String fileName) {
        return importer.previewPdf(bytes, fileName);
    }

    public SourceImportPreview previewUrl(String url) {
        return importer.previewUrl(url);
    }

    public ManagedSourceDocument create(SourceUpsertRequest request) {
        validate(request);
        String id = "user-" + UUID.randomUUID();
        SourceDocumentEntry entry = entry(id, request, true);
        sourceRepository.insertManaged(entry);
        indexBestEffort(entry);
        return getManaged(id);
    }

    public ManagedSourceDocument update(String id, SourceUpsertRequest request) {
        validate(request);
        SourceDocumentEntry existing = getEntry(id);
        if (!existing.managedByUser()) {
            throw new IllegalArgumentException("Seeded sources are read-only. Add a managed copy to edit this material.");
        }
        SourceDocumentEntry updated = entry(id, request, true);
        if (!sourceRepository.updateManaged(updated)) {
            throw new IllegalArgumentException("The managed source could not be updated.");
        }
        indexBestEffort(updated);
        return getManaged(id);
    }

    public ManagedSourceDocument reindex(String id) {
        SourceDocumentEntry entry = getEntry(id);
        try {
            indexer.index(entry);
        } catch (Exception exception) {
            logger.warn("Could not re-index managed source {} ({})", id, exception.getClass().getSimpleName());
            throw new IllegalStateException("The source is saved, but indexing failed. Check the embedding service and retry.");
        }
        return getManaged(id);
    }

    @Transactional
    public void delete(String id) {
        SourceDocumentEntry entry = getEntry(id);
        if (!entry.managedByUser()) {
            throw new IllegalArgumentException("Seeded sources are read-only and cannot be deleted.");
        }
        indexer.remove(entry);
        if (!sourceRepository.deleteManaged(id)) {
            throw new IllegalArgumentException("The managed source could not be deleted.");
        }
    }

    private void indexBestEffort(SourceDocumentEntry entry) {
        try {
            indexer.index(entry);
        } catch (Exception exception) {
            logger.warn("Source {} was saved but not indexed ({})", entry.document().id(),
                    exception.getClass().getSimpleName());
        }
    }

    private SourceDocumentEntry getEntry(String id) {
        SourceDocumentEntry entry = sourceRepository.findEntryById(id);
        if (entry == null) throw new IllegalArgumentException("Source not found.");
        return entry;
    }

    private ManagedSourceDocument getManaged(String id) {
        SourceDocumentEntry entry = getEntry(id);
        return new ManagedSourceDocument(entry.document(), entry.category(), entry.cropFilter(),
                entry.regionFilter(), entry.managedByUser(), indexer.isIndexed(entry));
    }

    private SourceDocumentEntry entry(String id, SourceUpsertRequest request, boolean managed) {
        String url = normalizeUrl(request.url());
        String documentDate = blankAs(request.documentDate(), "Undated");
        String accessedDate = LocalDate.now().toString();
        String crops = blankAs(request.crops(), "General agriculture");
        String sourceType = request.sourceType().trim().toUpperCase(Locale.ROOT);
        String fileName = blankAs(request.fileName(), "");
        SourceDocument document = new SourceDocument(id, request.title().trim(), request.organization().trim(),
                documentDate, accessedDate, url, crops, request.content().trim(), sourceType, fileName);
        return new SourceDocumentEntry(document, request.category().trim(), request.cropFilter().trim(),
                request.regionFilter().trim(), managed);
    }

    private void validate(SourceUpsertRequest request) {
        if (request == null) throw new IllegalArgumentException("Source metadata is required.");
        if (request.content() == null || request.content().trim().length() < 80) {
            throw new IllegalArgumentException("Review the extracted text and keep at least 80 useful characters.");
        }
        if (request.content().length() > MAX_CONTENT_CHARS) {
            throw new IllegalArgumentException("This source is too long. Split it into sections before importing.");
        }
        if (!CATEGORIES.contains(request.category())) {
            throw new IllegalArgumentException("Choose a supported source category.");
        }
        if (!CROPS.contains(request.cropFilter())) {
            throw new IllegalArgumentException("Choose a supported crop filter.");
        }
        if (!REGIONS.contains(request.regionFilter())) {
            throw new IllegalArgumentException("Choose a supported region filter.");
        }
        String sourceType = request.sourceType() == null ? "" : request.sourceType().toUpperCase(Locale.ROOT);
        if (!Set.of("LINK", "UPLOAD").contains(sourceType)) {
            throw new IllegalArgumentException("Source type must be LINK or UPLOAD.");
        }
        normalizeUrl(request.url());
    }

    private String normalizeUrl(String value) {
        String url = value == null ? "" : value.trim();
        if (url.isEmpty()) return "";
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (uri.getHost() == null || uri.getUserInfo() != null
                    || !("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))) {
                throw new IllegalArgumentException("Source links must use HTTP or HTTPS and must not contain login details.");
            }
            return uri.toString();
        } catch (IllegalArgumentException exception) {
            if (exception.getMessage() != null && exception.getMessage().startsWith("Source links")) throw exception;
            throw new IllegalArgumentException("Enter a valid source link.");
        }
    }

    private String blankAs(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
