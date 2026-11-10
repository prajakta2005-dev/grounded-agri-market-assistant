package com.groundedagri.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class KnowledgeBase {
    private static final Logger logger = LoggerFactory.getLogger(KnowledgeBase.class);
    private static final int MAX_SOURCES = 4;

    private static final Set<String> MARKET_TERMS = Set.of(
            "market", "marketplace", "price", "prices", "available", "produce", "stock", "sell", "selling",
            "buy", "buying", "order", "orders", "listing", "listings", "mandi");

    private final SourceDocumentRepository sourceRepository;
    private final VectorStore vectorStore;

    public KnowledgeBase(SourceDocumentRepository sourceRepository, VectorStore vectorStore) {
        this.sourceRepository = sourceRepository;
        this.vectorStore = vectorStore;
    }

    public List<SourceDocument> all() {
        return sourceRepository.findAll();
    }

    public List<SourceDocument> search(String query) {
        if (query == null || query.isBlank()) {
            return all().stream().limit(3).toList();
        }

        try {
            SearchRequest.Builder request = SearchRequest.builder().query(query).topK(16);
            String filter = filterExpression(query);
            if (!filter.isBlank()) request.filterExpression(filter);

            List<String> sourceIds = new ArrayList<>();
            for (Document result : vectorStore.similaritySearch(request.build())) {
                Object sourceId = result.getMetadata().get("source_id");
                if (sourceId != null) sourceIds.add(sourceId.toString());
                if (new LinkedHashSet<>(sourceIds).size() == MAX_SOURCES) break;
            }
            List<String> uniqueIds = new ArrayList<>(new LinkedHashSet<>(sourceIds));
            List<SourceDocument> matches = sourceRepository.findByIdsInOrder(uniqueIds);
            if (!matches.isEmpty()) return matches;
        } catch (Exception exception) {
            logger.warn("Semantic source retrieval failed ({}); using database keyword fallback",
                    exception.getClass().getSimpleName());
        }

        return keywordSearch(query);
    }

    private String filterExpression(String query) {
        String normalized = query.toLowerCase(Locale.ROOT);
        List<String> filters = new ArrayList<>();
        boolean marketQuestion = MARKET_TERMS.stream().anyMatch(normalized::contains);
        if (marketQuestion) {
            filters.add("category in ['market_reference', 'market_inventory']");
        } else {
            String crop = findCrop(normalized);
            if (crop != null) filters.add("crop in ['" + crop + "', 'all']");
        }

        String region = findRegion(normalized);
        if (region != null) filters.add("region in ['" + region + "', 'global']");
        return String.join(" && ", filters);
    }

    private String findCrop(String query) {
        if (containsAny(query, "rice", "paddy", "ponni")) return "rice";
        if (containsAny(query, "maize", "corn")) return "maize";
        if (query.contains("tomato")) return "tomato";
        return null;
    }

    private String findRegion(String query) {
        if (containsAny(query, "tamil nadu", "coimbatore", "thanjavur", "erode", "dindigul")) {
            return "tamil_nadu";
        }
        if (query.contains("india")) return "india";
        return null;
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) return true;
        }
        return false;
    }

    private List<SourceDocument> keywordSearch(String query) {
        Set<String> terms = Arrays.stream(query.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(term -> term.length() > 2)
                .collect(java.util.stream.Collectors.toSet());
        if (terms.isEmpty()) return all().stream().limit(3).toList();
        return sourceRepository.findAllEntries().stream()
                .map(entry -> new ScoredSource(entry.document(), score(entry, terms)))
                .filter(scored -> scored.score() > 0)
                .sorted((first, second) -> Integer.compare(second.score(), first.score()))
                .limit(MAX_SOURCES)
                .map(ScoredSource::document)
                .toList();
    }

    private int score(SourceDocumentEntry entry, Set<String> terms) {
        String title = entry.document().title().toLowerCase(Locale.ROOT);
        String crops = entry.document().crops().toLowerCase(Locale.ROOT);
        String content = entry.document().content().toLowerCase(Locale.ROOT);
        int score = 0;
        for (String term : terms) {
            if (term.length() <= 2) continue;
            if (title.contains(term)) score += 4;
            if (crops.contains(term)) score += 5;
            if (content.contains(term)) score++;
        }
        return score;
    }

    private record ScoredSource(SourceDocument document, int score) {}
}
