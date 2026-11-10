package com.groundedagri.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SourceVectorIngestionRunner implements ApplicationRunner {
    private static final Logger logger = LoggerFactory.getLogger(SourceVectorIngestionRunner.class);

    private final SourceDocumentRepository sourceRepository;
    private final SourceVectorIndexer indexer;
    private final boolean enabled;

    public SourceVectorIngestionRunner(SourceDocumentRepository sourceRepository,
                                       SourceVectorIndexer indexer,
                                       @Value("${app.rag.index-on-startup:true}") boolean enabled) {
        this.sourceRepository = sourceRepository;
        this.indexer = indexer;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;
        List<SourceDocumentEntry> entries = sourceRepository.findAllEntries();
        int indexed = indexer.indexAll(entries);
        logger.info("Source vector index ready: {} source(s) refreshed, {} source(s) total", indexed, entries.size());
    }
}
