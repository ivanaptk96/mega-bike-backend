package com.megabike.sef.api;

import com.megabike.sef.domain.SefInvoiceDirection;
import com.megabike.supplier.domain.SupplierClassification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SefInvoiceResponse(
		UUID id,
		UUID connectionId,
		Long sefInvoiceId,
		UUID supplierId,
		String supplierName,
		String supplierDisplayName,
		SupplierClassification supplierClassification,
		SefInvoiceDirection direction,
		String globalUniqueId,
		String cirInvoiceId,
		String invoiceNumber,
		String documentType,
		String status,
		String counterpartyName,
		String counterpartyRegistrationNumber,
		String counterpartyVatRegistrationNumber,
		String currency,
		BigDecimal amountWithoutVat,
		BigDecimal vatAmount,
		BigDecimal totalAmount,
		Instant sentAt,
		Instant dueAt,
		Instant importedAt,
		Instant updatedAt
) {
}
