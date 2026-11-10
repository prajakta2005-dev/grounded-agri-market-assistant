package com.groundedagri.knowledge;

import java.util.UUID;

public record SourceVectorState(
        String sourceId,
        int chunkIndex,
        UUID vectorId,
        String contentHash
) {}
