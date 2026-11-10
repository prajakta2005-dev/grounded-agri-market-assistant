package com.groundedagri.market;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ListingDraft(
        @NotBlank String crop,
        String variety,
        @NotNull @DecimalMin("1.0") Double quantityKg,
        @NotNull @DecimalMin("0.01") Double pricePerKg,
        @NotBlank String location,
        @NotBlank String harvestDate,
        String seller
) {}
