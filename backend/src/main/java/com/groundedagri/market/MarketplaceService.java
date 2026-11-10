package com.groundedagri.market;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

@Service
public class MarketplaceService {
    private static final String ORDER_NOTE = "Draft only. Confirm availability and final terms with the seller.";
    private final MarketplaceRepository repository;

    public MarketplaceService(MarketplaceRepository repository) {
        this.repository = repository;
    }

    public List<ProduceListing> search(String query, String location) {
        String normalizedQuery = query == null ? "" : query.trim();
        String normalizedLocation = location == null ? "" : location.trim();
        return repository.searchAvailable(normalizedQuery, normalizedLocation);
    }

    public ProduceListing prepareListing(ListingDraft draft) {
        if (draft.crop() == null || draft.crop().isBlank() || draft.location() == null || draft.location().isBlank()) {
            throw new IllegalArgumentException("Crop and location are required to prepare a listing.");
        }
        BigDecimal quantity = money(draft.quantityKg(), "Quantity");
        BigDecimal price = money(draft.pricePerKg(), "Asking price");
        if (quantity.signum() <= 0 || price.signum() <= 0) {
            throw new IllegalArgumentException("Quantity and asking price must be greater than zero.");
        }
        LocalDate harvestDate = parseDate(draft.harvestDate());
        LocalDate availableDate = LocalDate.now();
        ProduceListing listing = new ProduceListing("draft-" + UUID.randomUUID(), draft.crop().trim(),
                blankAs(draft.variety(), "Unspecified"), quantity.doubleValue(), price.doubleValue(),
                draft.location().trim(), harvestDate.toString(), availableDate.toString(),
                blankAs(draft.seller(), "You"), "DRAFT", false);
        repository.insertListing(listing);
        return listing;
    }

    public ProduceListing prepareListing(String crop, String variety, double quantityKg, double pricePerKg,
                                         String location, String harvestDate, String seller) {
        return prepareListing(new ListingDraft(crop, variety, quantityKg, pricePerKg, location,
                harvestDate, seller));
    }

    @Transactional
    public PreparedOrder prepareOrder(OrderDraft draft, String buyer) {
        BigDecimal quantity = money(draft.quantityKg(), "Order quantity");
        if (quantity.signum() <= 0) {
            throw new IllegalArgumentException("Order quantity must be greater than zero.");
        }
        ProduceListing listing = repository.findAvailableByIdForUpdate(draft.listingId())
                .orElseThrow(() -> new IllegalArgumentException("That produce lot is no longer available."));
        BigDecimal listedQuantity = BigDecimal.valueOf(listing.quantityKg()).setScale(2, RoundingMode.HALF_UP);
        if (quantity.compareTo(listedQuantity) > 0) {
            throw new IllegalArgumentException("Requested quantity exceeds the listed amount of "
                    + listedQuantity.stripTrailingZeros().toPlainString() + " kg.");
        }
        BigDecimal total = quantity.multiply(BigDecimal.valueOf(listing.pricePerKg()))
                .setScale(2, RoundingMode.HALF_UP);
        PreparedOrder order = new PreparedOrder("order-" + UUID.randomUUID(), listing.id(), listing.crop(),
                quantity.doubleValue(), total.doubleValue(), blankAs(buyer, "You"), "PREPARED", ORDER_NOTE);
        repository.insertOrder(order);
        return order;
    }

    private BigDecimal money(Double value, String field) {
        if (value == null || !Double.isFinite(value)) {
            throw new IllegalArgumentException(field + " must be a valid number.");
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException | NullPointerException exception) {
            throw new IllegalArgumentException("Harvest date must use YYYY-MM-DD format.");
        }
    }

    private String blankAs(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
