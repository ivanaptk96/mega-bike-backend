package com.megabike.sef.infrastructure.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SefPurchaseInvoicesDto(
		@JsonProperty("PurchaseInvoiceIds")
		List<Long> purchaseInvoiceIds
) {
}
