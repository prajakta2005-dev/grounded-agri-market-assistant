package com.groundedagri.knowledge;

public record SourceDocumentEntry(
        SourceDocument document,
        String category,
        String cropFilter,
        String regionFilter,
        boolean managedByUser
) {}
