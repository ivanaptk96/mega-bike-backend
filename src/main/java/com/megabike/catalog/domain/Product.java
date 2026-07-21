package com.megabike.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "product")
public class Product {

	@Id
	@Column(name = "id", nullable = false)
	private UUID id;

	@Column(name = "product_code", nullable = false, unique = true, length = 50)
	private String productCode;

	@Column(name = "external_id", length = 120)
	private String externalId;

	@Column(name = "barcode", unique = true, length = 64)
	private String barcode;

	@Column(name = "name", nullable = false, length = 240)
	private String name;

	@Column(name = "description", length = 2000)
	private String description;

	@Column(name = "brand_name", length = 160)
	private String brandName;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "category_id", nullable = false)
	private Category category;

	@Enumerated(EnumType.STRING)
	@Column(name = "unit", nullable = false, length = 40)
	private ProductUnit unit;

	@Column(name = "purchase_price", nullable = false, precision = 12, scale = 2)
	private BigDecimal purchasePrice;

	@Column(name = "retail_price", nullable = false, precision = 12, scale = 2)
	private BigDecimal retailPrice;

	@Column(name = "vat_rate", nullable = false, precision = 5, scale = 2)
	private BigDecimal vatRate;

	@Column(name = "retail_price_includes_vat", nullable = false)
	private boolean retailPriceIncludesVat;

	@Column(name = "active", nullable = false)
	private boolean active;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at")
	private Instant updatedAt;

	@Version
	@Column(name = "version", nullable = false)
	private long version;

	protected Product() {
	}

	public Product(
			UUID id,
			String productCode,
			String externalId,
			String barcode,
			String name,
			String description,
			String brandName,
			Category category,
			ProductUnit unit,
			BigDecimal purchasePrice,
			BigDecimal retailPrice,
			BigDecimal vatRate,
			boolean retailPriceIncludesVat,
			boolean active,
			Instant createdAt
	) {
		this.id = id;
		this.productCode = productCode;
		this.externalId = externalId;
		this.barcode = barcode;
		this.name = name;
		this.description = description;
		this.brandName = brandName;
		this.category = category;
		this.unit = unit;
		this.purchasePrice = purchasePrice;
		this.retailPrice = retailPrice;
		this.vatRate = vatRate;
		this.retailPriceIncludesVat = retailPriceIncludesVat;
		this.active = active;
		this.createdAt = createdAt;
	}

	public void update(
			String productCode,
			String externalId,
			String barcode,
			String name,
			String description,
			String brandName,
			Category category,
			ProductUnit unit,
			BigDecimal purchasePrice,
			BigDecimal retailPrice,
			BigDecimal vatRate,
			boolean retailPriceIncludesVat,
			boolean active,
			Instant updatedAt
	) {
		this.productCode = productCode;
		this.externalId = externalId;
		this.barcode = barcode;
		this.name = name;
		this.description = description;
		this.brandName = brandName;
		this.category = category;
		this.unit = unit;
		this.purchasePrice = purchasePrice;
		this.retailPrice = retailPrice;
		this.vatRate = vatRate;
		this.retailPriceIncludesVat = retailPriceIncludesVat;
		this.active = active;
		this.updatedAt = updatedAt;
	}

	public UUID getId() {
		return id;
	}

	public String getProductCode() {
		return productCode;
	}

	public String getExternalId() {
		return externalId;
	}

	public String getBarcode() {
		return barcode;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public String getBrandName() {
		return brandName;
	}

	public Category getCategory() {
		return category;
	}

	public ProductUnit getUnit() {
		return unit;
	}

	public BigDecimal getPurchasePrice() {
		return purchasePrice;
	}

	public BigDecimal getRetailPrice() {
		return retailPrice;
	}

	public BigDecimal getVatRate() {
		return vatRate;
	}

	public boolean isRetailPriceIncludesVat() {
		return retailPriceIncludesVat;
	}

	public boolean isActive() {
		return active;
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
