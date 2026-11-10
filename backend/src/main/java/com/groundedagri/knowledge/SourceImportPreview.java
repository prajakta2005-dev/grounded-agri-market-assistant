package com.groundedagri.knowledge;

public record SourceImportPreview(
        String suggestedTitle,
        String content,
        String sourceType,
        String fileName,
        String url
) {}
