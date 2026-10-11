package com.megabike.supplier.api;

import com.megabike.supplier.domain.SupplierClassification;
import jakarta.validation.constraints.NotNull;

public record SupplierClassificationRequest(
		@NotNull SupplierClassification classification
) {
}
