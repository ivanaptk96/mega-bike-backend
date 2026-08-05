package com.megabike.inventory.domain;

import com.megabike.catalog.domain.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_item")
public class InventoryItem {

	@Id
	@Column(name = "id", nullable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "product_id", nullable = false)
	private Product product;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "stock_location_id", nullable = false)
	private StockLocation stockLocation;

	@Column(name = "quantity_on_hand", nullable = false, precision = 12, scale = 3)
	private BigDecimal quantityOnHand;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at")
	private Instant updatedAt;

	@Version
	@Column(name = "version", nullable = false)
	private long version;

	protected InventoryItem() {
	}

	public InventoryItem(UUID id, Product product, StockLocation stockLocation, BigDecimal quantityOnHand, Instant createdAt) {
		this.id = id;
		this.product = product;
		this.stockLocation = stockLocation;
		this.quantityOnHand = quantityOnHand;
		this.createdAt = createdAt;
	}

	public void adjust(BigDecimal quantityDelta, Instant updatedAt) {
		this.quantityOnHand = this.quantityOnHand.add(quantityDelta);
		this.updatedAt = updatedAt;
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

	public BigDecimal getQuantityOnHand() {
		return quantityOnHand;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public long getVersion() {
		return version;
	}
}
