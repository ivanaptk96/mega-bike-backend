package com.megabike.inventory.api;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record StockAdjustmentRequest(
		@NotNull
		UUID productId,

		@NotNull
		UUID stockLocationId,

		@NotNull
		@Digits(integer = 9, fraction = 3)
		BigDecimal quantityDelta,

		@NotBlank
		@Size(max = 500)
		String reason,

		@Size(max = 160)
		String referenceText
) {
}
