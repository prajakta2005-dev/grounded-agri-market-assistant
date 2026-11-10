package com.groundedagri.market;

public record ProduceListing(
        String id,
        String crop,
        String variety,
        double quantityKg,
        double pricePerKg,
        String location,
        String harvestDate,
        String availableDate,
        String seller,
        String status,
        boolean demoData
) {}
