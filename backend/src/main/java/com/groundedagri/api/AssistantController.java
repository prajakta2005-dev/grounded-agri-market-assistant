package com.groundedagri.api;

import com.groundedagri.knowledge.KnowledgeBase;
import com.groundedagri.knowledge.SourceDocument;
import com.groundedagri.market.MarketplaceTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestClientResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/assistant")
@Tag(name = "Assistant", description = "Source-grounded crop questions and photo symptom triage")
public class AssistantController {
    private static final Logger logger = LoggerFactory.getLogger(AssistantController.class);
    private static final String SYSTEM_PROMPT = """
            You are Grounded Agri-Market Assistant for farmers. Answer using only the supplied source context and results from your marketplace tools.
            Clearly distinguish sourced facts from uncertainty. Never invent dates, market prices, listings, diagnoses, pesticide labels, or local conditions.
            If the sources do not support an answer, say what is missing and suggest contacting a local agricultural extension officer or plant clinic.
            For photos, describe visible signs and offer a short differential, never a definitive diagnosis. Ask for crop, variety, crop stage, location, recent weather, and how symptoms spread when needed.
            Avoid prescribing a pesticide or dose. If a cited document contains a pesticide recommendation, state that it is dated and regional and must be checked against current local registration and the product label by a qualified adviser.
            When using source context, cite the source using its [S#] marker. Marketplace data is prototype sample inventory and not live prices.
            For listing or order actions, prepare a draft only and state clearly that the farmer must review and confirm it.
            """;

    private final ChatClient chatClient;
    private final KnowledgeBase knowledgeBase;
    private final MarketplaceTools marketplaceTools;

    @Value("${spring.ai.openai.api-key:missing-key}")
    private String apiKey;

    public AssistantController(ChatClient.Builder chatClientBuilder, KnowledgeBase knowledgeBase,
                               MarketplaceTools marketplaceTools) {
        this.chatClient = chatClientBuilder.build();
        this.knowledgeBase = knowledgeBase;
        this.marketplaceTools = marketplaceTools;
    }

    @PostMapping(value = "/chat", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Ask about a crop or market", description = "Accepts a question and an optional crop photo. Responses include retrieved source documents.")
    public AssistantReply chat(@RequestPart(name = "question", required = false) String question,
                               @Parameter(description = "Optional JPEG, PNG, or WebP crop photo")
                               @RequestPart(name = "photo", required = false) MultipartFile photo) {
        if (!StringUtils.hasText(apiKey) || apiKey.equals("missing-key")) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Set NVIDIA_API_KEY in the backend environment to enable the assistant.");
        }
        if (!StringUtils.hasText(question) && (photo == null || photo.isEmpty())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Add a question or a crop photo.");
        }
        if (photo != null && !photo.isEmpty() && !isAllowedImage(photo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload a JPEG, PNG, or WebP image under 6 MB.");
        }

        String userQuestion = StringUtils.hasText(question) ? question.trim()
                : "Describe the visible crop symptoms and explain what additional information is needed.";
        List<SourceDocument> sources = knowledgeBase.search(userQuestion);
        String sourceContext = formatSources(sources);
        var request = chatClient.prompt()
                .system(SYSTEM_PROMPT + "\n\nRetrieved source context:\n" + sourceContext)
                .tools(marketplaceTools)
                .user(user -> {
                    user.text(userQuestion);
                    if (photo != null && !photo.isEmpty()) {
                        try {
                            user.media(MediaType.parseMediaType(photo.getContentType()), new NamedImageResource(photo.getBytes(), photo.getOriginalFilename()));
                        } catch (Exception exception) {
                            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read the uploaded image.");
                        }
                    }
                });
        try {
            String answer = request.call().content();
            return new AssistantReply(answer == null ? "I couldn't form an answer from the available sources." : answer,
                    sources, photo != null && !photo.isEmpty());
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            Throwable cause = ex;
            boolean providerStatusLogged = false;
            while (cause != null) {
                if (cause instanceof RestClientResponseException responseException) {
                    logger.warn("Model provider returned HTTP {}", responseException.getStatusCode().value());
                    providerStatusLogged = true;
                    break;
                }
                cause = cause.getCause();
            }
            if (!providerStatusLogged) {
                logger.warn("Model provider request failed ({})", ex.getClass().getSimpleName());
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "The assistant could not reach the configured model. Check the NVIDIA endpoint and model setting.");
        }
    }

    private String formatSources(List<SourceDocument> sources) {
        if (sources.isEmpty()) return "No relevant documents were retrieved. Do not give crop-specific advice without support.";
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < sources.size(); i++) {
            SourceDocument source = sources.get(i);
            result.append("[S").append(i + 1).append("] ").append(source.title())
                    .append(" | ").append(source.organization()).append(" | ")
                    .append(source.documentDate()).append(" | accessed ").append(source.accessedDate())
                    .append(" | ").append(source.url()).append("\n")
                    .append(source.content()).append("\n\n");
        }
        return result.toString();
    }

    private boolean isAllowedImage(MultipartFile photo) {
        if (photo.getSize() > 6L * 1024 * 1024) return false;
        String type = photo.getContentType();
        return type != null && List.of("image/jpeg", "image/png", "image/webp")
                .contains(type.toLowerCase(Locale.ROOT));
    }

    private static final class NamedImageResource extends ByteArrayResource {
        private final String filename;
        private NamedImageResource(byte[] bytes, String filename) {
            super(bytes);
            this.filename = filename == null ? "crop-photo" : filename;
        }
        @Override public String getFilename() { return filename; }
    }
}
