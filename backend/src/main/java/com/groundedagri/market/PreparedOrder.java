package com.groundedagri.market;

public record PreparedOrder(
        String id,
        String listingId,
        String crop,
        double quantityKg,
        double indicativeTotal,
        String buyer,
        String status,
        String note
) {}
