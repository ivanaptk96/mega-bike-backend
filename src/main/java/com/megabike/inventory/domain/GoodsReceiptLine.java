package com.megabike.inventory.domain;

import com.megabike.catalog.domain.Product;
import com.megabike.sef.domain.SefInvoiceLine;
import jakarta.persistence.*;
import lombok.Getter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "goods_receipt_line")
@Getter
public class GoodsReceiptLine {
	@Id private UUID id;
	@ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "receipt_id", nullable = false) private GoodsReceipt receipt;
	@Column(name = "line_number", nullable = false) private int lineNumber;
	@Column(name = "item_name", nullable = false, length = 500) private String itemName;
	@Column(name = "seller_item_id", length = 120) private String sellerItemId;
	@Column(name = "unit_code", length = 40) private String unitCode;
	@Column(name = "expected_quantity", nullable = false, precision = 14, scale = 3) private BigDecimal expectedQuantity;
	@Column(name = "received_quantity", nullable = false, precision = 14, scale = 3) private BigDecimal receivedQuantity;
	@Column(name = "purchase_price", precision = 14, scale = 4) private BigDecimal purchasePrice;
	@Column(name = "vat_rate", precision = 5, scale = 2) private BigDecimal vatRate;
	@Column(name = "retail_price", precision = 12, scale = 2) private BigDecimal retailPrice;
	@ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "product_id") private Product product;
	@Enumerated(EnumType.STRING) @Column(nullable = false) private ReceiptStatus status;
	@Column(name = "approved_by", length = 320) private String approvedBy;
	@Column(name = "reserved_product_code", unique = true, length = 50) private String reservedProductCode;
	@Column(name = "approved_at") private Instant approvedAt;
	protected GoodsReceiptLine() {}
	public GoodsReceiptLine(GoodsReceipt receipt, SefInvoiceLine source, String reservedProductCode) {
		this.reservedProductCode = reservedProductCode;
		id = UUID.randomUUID(); this.receipt = receipt; lineNumber = source.getLineNumber(); itemName = source.getItemName();
		sellerItemId = source.getSellerItemId(); unitCode = source.getUnitCode(); expectedQuantity = source.getQuantity();
		receivedQuantity = BigDecimal.ZERO; purchasePrice = source.getUnitPrice(); vatRate = source.getVatRate(); status = ReceiptStatus.PENDING;
	}
	public void receive(Product product, BigDecimal quantity, BigDecimal price, String actor, Instant now) {
		this.product = product; receivedQuantity = receivedQuantity.add(quantity); retailPrice = price;
		status = receivedQuantity.compareTo(expectedQuantity) == 0 ? ReceiptStatus.RECEIVED : ReceiptStatus.PARTIAL;
		approvedBy = actor; approvedAt = now;
	}
}
