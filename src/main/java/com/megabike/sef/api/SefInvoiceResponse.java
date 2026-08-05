package com.megabike.sef.api;

import com.megabike.sef.domain.SefInvoiceDirection;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SefInvoiceResponse(
		UUID id,
		Long sefInvoiceId,
		UUID supplierId,
		String supplierName,
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
