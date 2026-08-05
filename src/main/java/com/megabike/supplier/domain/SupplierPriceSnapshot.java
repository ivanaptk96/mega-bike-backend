package com.megabike.supplier.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "supplier_price_snapshot")
public class SupplierPriceSnapshot {

	@Id
	@Column(name = "id", nullable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "supplier_product_id", nullable = false)
	private SupplierProduct supplierProduct;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "import_run_id", nullable = false)
	private SupplierImportRun importRun;

	@Column(name = "purchase_price", nullable = false, precision = 12, scale = 2)
	private BigDecimal purchasePrice;

	@Column(name = "currency", nullable = false, length = 3)
	private String currency;

	@Column(name = "vat_included", nullable = false)
	private boolean vatIncluded;

	@Column(name = "available_quantity", precision = 12, scale = 3)
	private BigDecimal availableQuantity;

	@Column(name = "captured_at", nullable = false)
	private Instant capturedAt;

	@Column(name = "source", nullable = false, length = 120)
	private String source;

	protected SupplierPriceSnapshot() {
	}

	public SupplierPriceSnapshot(UUID id, SupplierProduct supplierProduct, SupplierImportRun importRun, BigDecimal purchasePrice, String currency, boolean vatIncluded, BigDecimal availableQuantity, Instant capturedAt, String source) {
		this.id = id;
		this.supplierProduct = supplierProduct;
		this.importRun = importRun;
		this.purchasePrice = purchasePrice;
		this.currency = currency;
		this.vatIncluded = vatIncluded;
		this.availableQuantity = availableQuantity;
		this.capturedAt = capturedAt;
		this.source = source;
	}
}
