package com.groundedagri.knowledge;

public record SourceDocument(
        String id,
        String title,
        String organization,
        String documentDate,
        String accessedDate,
        String url,
        String crops,
        String content
) {}
