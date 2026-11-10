package com.groundedagri.api;

import com.groundedagri.knowledge.SourceDocument;
import java.util.List;

public record AssistantReply(String answer, List<SourceDocument> sources, boolean imageReviewed) {}
