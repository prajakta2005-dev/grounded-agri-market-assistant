package com.groundedagri.knowledge;

public record ManagedSourceDocument(
        SourceDocument document,
        String category,
        String cropFilter,
        String regionFilter,
        boolean managedByUser,
        boolean indexed
) {}
