package com.megabike.inventory.api;
import com.megabike.inventory.domain.ReceiptStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
public record GoodsReceiptResponse(UUID invoiceId, UUID connectionId, String invoiceNumber, String supplierName,
		ReceiptStatus status, UUID stockLocationId, String stockLocationName, long version, List<Line> lines) {
	public record Line(UUID id, int lineNumber, String itemName, String sellerItemId, String unitCode,
			BigDecimal expectedQuantity, BigDecimal receivedQuantity, BigDecimal purchasePrice, BigDecimal vatRate,
			BigDecimal retailPrice, UUID productId, String productName, ReceiptStatus status, String approvedBy, Instant approvedAt, String productCode, String imageUrl) {}
	public record Summary(UUID invoiceId, String invoiceNumber, String supplierName, ReceiptStatus status) {}
	public record Location(UUID id, String code, String name) {}
}
