package com.megabike.sef.infrastructure.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SefSimplePurchaseInvoiceDto(
		@JsonProperty("InvoiceId")
		Long invoiceId,
		@JsonProperty("Status")
		SefPurchaseInvoiceStatus status,
		@JsonProperty("InvoiceNumber")
		String invoiceNumber,
		@JsonProperty("AccountingDateUtc")
		Instant accountingDateUtc,
		@JsonProperty("InvoiceDateUtc")
		Instant invoiceDateUtc,
		@JsonProperty("InvoiceSentDateUtc")
		Instant invoiceSentDateUtc,
		@JsonProperty("PaymentDateUtc")
		Instant paymentDateUtc,
		@JsonProperty("ReferenceNumber")
		String referenceNumber,
		@JsonProperty("Description")
		String description,
		@JsonProperty("Note")
		String note,
		@JsonProperty("OrderNumber")
		String orderNumber,
		@JsonProperty("Currency")
		String currency,
		@JsonProperty("DiscountPercentage")
		BigDecimal discountPercentage,
		@JsonProperty("DiscountAmount")
		BigDecimal discountAmount,
		@JsonProperty("SumWithoutVat")
		BigDecimal sumWithoutVat,
		@JsonProperty("VatRate")
		BigDecimal vatRate,
		@JsonProperty("VatSum")
		BigDecimal vatSum,
		@JsonProperty("SumWithVat")
		BigDecimal sumWithVat,
		@JsonProperty("Sender")
		String sender,
		@JsonProperty("Receiver")
		String receiver,
		@JsonProperty("InvoiceMessage")
		String invoiceMessage,
		@JsonProperty("AcceptRejectMessage")
		String acceptRejectMessage,
		@JsonProperty("CirInvoiceId")
		String cirInvoiceId,
		@JsonProperty("InvoiceType")
		String invoiceType,
		@JsonProperty("TotalToPay")
		BigDecimal totalToPay,
		@JsonProperty("RoundingAmount")
		BigDecimal roundingAmount
) {
}
