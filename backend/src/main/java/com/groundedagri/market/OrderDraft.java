package com.groundedagri.market;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OrderDraft(@NotBlank String listingId, @NotNull @DecimalMin("1.0") Double quantityKg) {}
