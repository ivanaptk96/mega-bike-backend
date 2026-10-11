package com.megabike.inventory.domain;

import com.megabike.catalog.domain.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_movement")
public class StockMovement {

	@Id
	@Column(name = "id", nullable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "product_id", nullable = false)
	private Product product;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "stock_location_id", nullable = false)
	private StockLocation stockLocation;

	@Enumerated(EnumType.STRING)
	@Column(name = "movement_type", nullable = false, length = 40)
	private MovementType movementType;

	@Column(name = "quantity_delta", nullable = false, precision = 12, scale = 3)
	private BigDecimal quantityDelta;

	@Column(name = "quantity_after", nullable = false, precision = 12, scale = 3)
	private BigDecimal quantityAfter;

	@Column(name = "reason", nullable = false, length = 500)
	private String reason;

	@Column(name = "reference_text", length = 160)
	private String referenceText;

	@Column(name = "created_by", nullable = false, length = 320)
	private String createdBy;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "receipt_line_id")
	private UUID receiptLineId;

	@Column(name = "receipt_retail_price", precision = 12, scale = 2)
	private BigDecimal receiptRetailPrice;

	public void recordReceipt(UUID lineId, BigDecimal retailPrice) {
		this.receiptLineId = lineId;
		this.receiptRetailPrice = retailPrice;
	}

	protected StockMovement() {
	}

	public StockMovement(
			UUID id,
			Product product,
			StockLocation stockLocation,
			MovementType movementType,
			BigDecimal quantityDelta,
			BigDecimal quantityAfter,
			String reason,
			String referenceText,
			String createdBy,
			Instant createdAt
	) {
		this.id = id;
		this.product = product;
		this.stockLocation = stockLocation;
		this.movementType = movementType;
		this.quantityDelta = quantityDelta;
		this.quantityAfter = quantityAfter;
		this.reason = reason;
		this.referenceText = referenceText;
		this.createdBy = createdBy;
		this.createdAt = createdAt;
	}

	public UUID getId() {
		return id;
	}

	public Product getProduct() {
		return product;
	}

	public StockLocation getStockLocation() {
		return stockLocation;
	}

	public MovementType getMovementType() {
		return movementType;
	}

	public BigDecimal getQuantityDelta() {
		return quantityDelta;
	}

	public BigDecimal getQuantityAfter() {
		return quantityAfter;
	}

	public String getReason() {
		return reason;
	}

	public String getReferenceText() {
		return referenceText;
	}

	public String getCreatedBy() {
		return createdBy;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
