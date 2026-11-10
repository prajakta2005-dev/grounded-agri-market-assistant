package com.groundedagri.api;

import com.groundedagri.knowledge.KnowledgeBase;
import com.groundedagri.knowledge.SourceDocument;
import com.groundedagri.market.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api")
@Tag(name = "Marketplace", description = "Search sample inventory and prepare listing or order drafts")
public class MarketplaceController {
    private final MarketplaceService marketplace;
    private final KnowledgeBase knowledgeBase;

    public MarketplaceController(MarketplaceService marketplace, KnowledgeBase knowledgeBase) {
        this.marketplace = marketplace;
        this.knowledgeBase = knowledgeBase;
    }

    @GetMapping("/market/listings")
    @Operation(summary = "Search available produce", description = "Search the prototype inventory by crop/variety and optional town or district. Sample values are not live market quotes.")
    public List<ProduceListing> search(@RequestParam(defaultValue = "") String query,
                                       @RequestParam(defaultValue = "") String location) {
        return marketplace.search(query, location);
    }

    @PostMapping("/market/listings")
    @Operation(summary = "Prepare a produce listing draft", description = "Saves a review draft in PostgreSQL. It does not publish the listing.")
    public ProduceListing prepareListing(@Valid @RequestBody ListingDraft draft) {
        return marketplace.prepareListing(draft);
    }

    @PostMapping("/market/orders")
    @Operation(summary = "Prepare an order draft", description = "Prepares an indicative order only; it does not reserve stock, contact the seller, or take payment.")
    public PreparedOrder prepareOrder(@Valid @RequestBody OrderDraft draft) {
        return marketplace.prepareOrder(draft, "You");
    }

    @GetMapping("/sources")
    @Operation(summary = "List source documents", description = "Returns the source library or a simple keyword-retrieved subset.", tags = {"Sources"})
    public List<SourceDocument> sources(@RequestParam(defaultValue = "") String query) {
        return query.isBlank() ? knowledgeBase.all() : knowledgeBase.search(query);
    }
}
