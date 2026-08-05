package com.megabike.sef.infrastructure.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SefPurchaseInvoiceStatusChangeDto(
		@JsonProperty("EventId")
		Integer eventId,
		@JsonProperty("Date")
		String date,
		@JsonProperty("NewInvoiceStatus")
		SefPurchaseInvoiceStatus newInvoiceStatus,
		@JsonProperty("PurchaseInvoiceId")
		Long purchaseInvoiceId,
		@JsonProperty("Comment")
		String comment,
		@JsonProperty("CirInvoiceId")
		String cirInvoiceId,
		@JsonProperty("SubscriptionKey")
		String subscriptionKey,
		@JsonProperty("StornoNumber")
		String stornoNumber,
		@JsonProperty("IsSigned")
		Boolean isSigned,
		@JsonProperty("IsAutoAssigned")
		Boolean isAutoAssigned
) {
}
