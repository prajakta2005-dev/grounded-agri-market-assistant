package com.groundedagri.knowledge;

import jakarta.validation.constraints.NotBlank;

public record SourcePreviewUrlRequest(@NotBlank String url) {}
