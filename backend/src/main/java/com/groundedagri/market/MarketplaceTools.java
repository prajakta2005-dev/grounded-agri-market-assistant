package com.groundedagri.market;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class MarketplaceTools {
    private final MarketplaceService marketplace;

    public MarketplaceTools(MarketplaceService marketplace) {
        this.marketplace = marketplace;
    }

    @Tool(description = "Search available sample produce lots by crop or location. Results are prototype inventory, never live market prices.")
    public String searchAvailableProduce(
            @ToolParam(description = "Crop name or variety to search for") String crop,
            @ToolParam(description = "Optional town or district") String location) {
        return marketplace.search(crop, location).toString();
    }

    @Tool(description = "Prepare a draft produce listing. This does not publish the listing; the farmer reviews it in the marketplace.")
    public String prepareProduceListing(
            @ToolParam(description = "Crop name") String crop,
            @ToolParam(description = "Variety, or 'Unspecified'") String variety,
            @ToolParam(description = "Quantity in kilograms") double quantityKg,
            @ToolParam(description = "Asking price in INR per kilogram") double pricePerKg,
            @ToolParam(description = "Town or district") String location,
            @ToolParam(description = "Harvest date in YYYY-MM-DD format") String harvestDate) {
        ProduceListing draft = marketplace.prepareListing(crop, variety, quantityKg, pricePerKg,
                location, harvestDate, "You");
        return "Draft prepared for review: " + draft;
    }

    @Tool(description = "Prepare an order draft from an available sample lot. This does not contact the seller, reserve stock, or take payment.")
    public String prepareOrderDraft(
            @ToolParam(description = "ID of a produce lot returned by searchAvailableProduce") String listingId,
            @ToolParam(description = "Order quantity in kilograms") double quantityKg) {
        PreparedOrder draft = marketplace.prepareOrder(new OrderDraft(listingId, quantityKg), "You");
        return "Order draft prepared for review: " + draft;
    }
}
