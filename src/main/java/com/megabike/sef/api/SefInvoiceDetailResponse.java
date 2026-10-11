package com.megabike.sef.api;

import com.megabike.sef.domain.SefInvoiceDocumentType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SefInvoiceDetailResponse(
		SefInvoiceResponse invoice,
		List<LineResponse> lines,
		List<VatSummaryResponse> vatSummaries,
		List<PaymentResponse> payments,
		List<DocumentResponse> documents
) {
	public record LineResponse(
			UUID id,
			int lineNumber,
			String externalLineId,
			String itemName,
			String itemDescription,
			String sellerItemId,
			String buyerItemId,
			String unitCode,
			BigDecimal quantity,
			BigDecimal unitPrice,
			BigDecimal discountAmount,
			BigDecimal lineAmountWithoutVat,
			String vatCategory,
			BigDecimal vatRate,
			BigDecimal vatAmount
	) {
	}

	public record VatSummaryResponse(
			UUID id,
			String vatCategory,
			BigDecimal vatRate,
			BigDecimal taxableAmount,
			BigDecimal vatAmount,
			String exemptionReasonCode,
			String exemptionReason
	) {
	}

	public record PaymentResponse(
			UUID id,
			String paymentMeansCode,
			String paymentId,
			String accountNumber,
			String accountName,
			String modelNumber,
			String referenceNumber,
			BigDecimal amount,
			String currency,
			Instant dueAt
	) {
	}

	public record DocumentResponse(
			UUID id,
			SefInvoiceDocumentType documentType,
			String filename,
			String contentType,
			long size,
			String sha256Hash,
			Instant downloadedAt
	) {
	}
}
