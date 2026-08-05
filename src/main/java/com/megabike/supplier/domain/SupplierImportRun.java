package com.megabike.supplier.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "supplier_import_run")
public class SupplierImportRun {

	@Id
	@Column(name = "id", nullable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "supplier_id", nullable = false)
	private Supplier supplier;

	@Enumerated(EnumType.STRING)
	@Column(name = "source_type", nullable = false, length = 40)
	private SupplierImportSourceType sourceType;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 40)
	private SupplierImportStatus status;

	@Column(name = "original_filename", nullable = false, length = 300)
	private String originalFilename;

	@Column(name = "content_type", length = 120)
	private String contentType;

	@Column(name = "sha256_hash", nullable = false, length = 64)
	private String sha256Hash;

	@Column(name = "uploaded_by", nullable = false, length = 320)
	private String uploadedBy;

	@Column(name = "started_at", nullable = false)
	private Instant startedAt;

	@Column(name = "finished_at", nullable = false)
	private Instant finishedAt;

	@Column(name = "total_rows", nullable = false)
	private int totalRows;

	@Column(name = "created_supplier_products", nullable = false)
	private int createdSupplierProducts;

	@Column(name = "updated_supplier_products", nullable = false)
	private int updatedSupplierProducts;

	@Column(name = "price_snapshots", nullable = false)
	private int priceSnapshots;

	@Column(name = "issues_count", nullable = false)
	private int issuesCount;

	protected SupplierImportRun() {
	}

	public SupplierImportRun(UUID id, Supplier supplier, SupplierImportSourceType sourceType, SupplierImportStatus status, String originalFilename, String contentType, String sha256Hash, String uploadedBy, Instant startedAt, Instant finishedAt, int totalRows, int createdSupplierProducts, int updatedSupplierProducts, int priceSnapshots, int issuesCount) {
		this.id = id;
		this.supplier = supplier;
		this.sourceType = sourceType;
		this.status = status;
		this.originalFilename = originalFilename;
		this.contentType = contentType;
		this.sha256Hash = sha256Hash;
		this.uploadedBy = uploadedBy;
		this.startedAt = startedAt;
		this.finishedAt = finishedAt;
		this.totalRows = totalRows;
		this.createdSupplierProducts = createdSupplierProducts;
		this.updatedSupplierProducts = updatedSupplierProducts;
		this.priceSnapshots = priceSnapshots;
		this.issuesCount = issuesCount;
	}

	public UUID getId() { return id; }
	public Supplier getSupplier() { return supplier; }
	public SupplierImportSourceType getSourceType() { return sourceType; }
	public SupplierImportStatus getStatus() { return status; }
	public String getOriginalFilename() { return originalFilename; }
	public String getContentType() { return contentType; }
	public String getSha256Hash() { return sha256Hash; }
	public String getUploadedBy() { return uploadedBy; }
	public Instant getStartedAt() { return startedAt; }
	public Instant getFinishedAt() { return finishedAt; }
	public int getTotalRows() { return totalRows; }
	public int getCreatedSupplierProducts() { return createdSupplierProducts; }
	public int getUpdatedSupplierProducts() { return updatedSupplierProducts; }
	public int getPriceSnapshots() { return priceSnapshots; }
	public int getIssuesCount() { return issuesCount; }
}
