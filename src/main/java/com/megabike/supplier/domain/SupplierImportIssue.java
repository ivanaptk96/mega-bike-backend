package com.megabike.supplier.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "supplier_import_issue")
public class SupplierImportIssue {

	@Id
	@Column(name = "id", nullable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "import_run_id", nullable = false)
	private SupplierImportRun importRun;

	@Column(name = "row_number", nullable = false)
	private int rowNumber;

	@Column(name = "code", nullable = false, length = 80)
	private String code;

	@Column(name = "message", nullable = false, length = 500)
	private String message;

	@Column(name = "raw_row", length = 4000)
	private String rawRow;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected SupplierImportIssue() {
	}

	public SupplierImportIssue(UUID id, SupplierImportRun importRun, int rowNumber, String code, String message, String rawRow, Instant createdAt) {
		this.id = id;
		this.importRun = importRun;
		this.rowNumber = rowNumber;
		this.code = code;
		this.message = message;
		this.rawRow = rawRow;
		this.createdAt = createdAt;
	}

	public UUID getId() { return id; }
	public SupplierImportRun getImportRun() { return importRun; }
	public int getRowNumber() { return rowNumber; }
	public String getCode() { return code; }
	public String getMessage() { return message; }
	public String getRawRow() { return rawRow; }
	public Instant getCreatedAt() { return createdAt; }
}
