package com.megabike.supplier.api;

import com.megabike.supplier.domain.SupplierImportSourceType;
import com.megabike.supplier.domain.SupplierImportStatus;

import java.time.Instant;
import java.util.UUID;

public record SupplierImportRunResponse(
		UUID id,
		UUID supplierId,
		String supplierCode,
		String supplierName,
		SupplierImportSourceType sourceType,
		SupplierImportStatus status,
		String originalFilename,
		String contentType,
		String sha256Hash,
		String uploadedBy,
		Instant startedAt,
		Instant finishedAt,
		int totalRows,
		int createdSupplierProducts,
		int updatedSupplierProducts,
		int priceSnapshots,
		int issuesCount
) {
}
