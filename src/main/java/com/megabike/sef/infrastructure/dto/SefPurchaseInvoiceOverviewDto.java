package com.megabike.sef.infrastructure.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SefPurchaseInvoiceOverviewDto(
		@JsonProperty("InvoiceId")
		Long invoiceId,
		@JsonProperty("GlobUniqId")
		String globUniqId,
		@JsonProperty("DocumentNumber")
		String documentNumber,
		@JsonProperty("DocumentType")
		String documentType,
		@JsonProperty("CirInvoiceId")
		String cirInvoiceId,
		@JsonProperty("Status")
		SefPurchaseInvoiceStatus status,
		@JsonProperty("SupplierName")
		String supplierName,
		@JsonProperty("SupplierRegistrationNumber")
		String supplierRegistrationNumber,
		@JsonProperty("SupplierVatRegistrationNumber")
		String supplierVatRegistrationNumber,
		@JsonProperty("Amount")
		BigDecimal amount,
		@JsonProperty("SumWithoutVat")
		BigDecimal sumWithoutVat,
		@JsonProperty("VatAmount")
		BigDecimal vatAmount,
		@JsonProperty("RoundingAmount")
		BigDecimal roundingAmount,
		@JsonProperty("Currency")
		String currency,
		@JsonProperty("DeliveryDate")
		Instant deliveryDate,
		@JsonProperty("PrepaymentDate")
		Instant prepaymentDate,
		@JsonProperty("DueDate")
		Instant dueDate,
		@JsonProperty("DateOfReduction")
		Instant dateOfReduction,
		@JsonProperty("SentDate")
		Instant sentDate
) {
}
