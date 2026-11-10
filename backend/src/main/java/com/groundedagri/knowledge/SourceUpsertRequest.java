package com.groundedagri.knowledge;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SourceUpsertRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 200) String organization,
        @Size(max = 100) String documentDate,
        @Size(max = 2048) String url,
        @Size(max = 300) String crops,
        @NotBlank @Size(max = 40) String category,
        @NotBlank @Size(max = 40) String cropFilter,
        @NotBlank @Size(max = 80) String regionFilter,
        @NotBlank @Size(max = 300000) String content,
        @NotBlank String sourceType,
        @Size(max = 200) String fileName
) {}
