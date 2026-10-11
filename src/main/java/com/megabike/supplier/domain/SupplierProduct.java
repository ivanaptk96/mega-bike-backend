package com.megabike.supplier.domain;

import com.megabike.catalog.domain.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "supplier_product")
public class SupplierProduct {

	@Id
	@Column(name = "id", nullable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "supplier_id", nullable = false)
	private Supplier supplier;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "product_id")
	private Product product;

	@Column(name = "supplier_code", nullable = false, length = 120)
	private String supplierCode;

	@Column(name = "supplier_barcode", length = 80)
	private String supplierBarcode;

	@Column(name = "supplier_product_name", nullable = false, length = 300)
	private String supplierProductName;

	@Column(name = "brand_name", length = 160)
	private String brandName;

	@Column(name = "category_text", length = 300)
	private String categoryText;

	@Column(name = "description", length = 4000)
	private String description;

	@Column(name = "image_url", length = 1000)
	private String imageUrl;

	@Column(name = "active", nullable = false)
	private boolean active;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at")
	private Instant updatedAt;

	@Version
	@Column(name = "version", nullable = false)
	private long version;

	protected SupplierProduct() {
	}

	public SupplierProduct(UUID id, Supplier supplier, Product product, String supplierCode, String supplierBarcode, String supplierProductName, String brandName, String categoryText, String description, String imageUrl, boolean active, Instant createdAt) {
		this.id = id;
		this.supplier = supplier;
		this.product = product;
		this.supplierCode = supplierCode;
		this.supplierBarcode = supplierBarcode;
		this.supplierProductName = supplierProductName;
		this.brandName = brandName;
		this.categoryText = categoryText;
		this.description = description;
		this.imageUrl = imageUrl;
		this.active = active;
		this.createdAt = createdAt;
	}

	public void update(Product product, String supplierCode, String supplierBarcode, String supplierProductName, String brandName, String categoryText, String description, String imageUrl, boolean active, Instant updatedAt) {
		this.product = product;
		this.supplierCode = supplierCode;
		this.supplierBarcode = supplierBarcode;
		this.supplierProductName = supplierProductName;
		this.brandName = brandName;
		this.categoryText = categoryText;
		this.description = description;
		this.imageUrl = imageUrl;
		this.active = active;
		this.updatedAt = updatedAt;
	}

	public void linkProduct(Product product, Instant now) { this.product = product; this.updatedAt = now; }

	public UUID getId() { return id; }
	public Supplier getSupplier() { return supplier; }
	public Product getProduct() { return product; }
	public String getSupplierCode() { return supplierCode; }
	public String getSupplierBarcode() { return supplierBarcode; }
	public String getSupplierProductName() { return supplierProductName; }
	public String getBrandName() { return brandName; }
	public String getCategoryText() { return categoryText; }
	public String getDescription() { return description; }
	public String getImageUrl() { return imageUrl; }
	public boolean isActive() { return active; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }
	public long getVersion() { return version; }
}
