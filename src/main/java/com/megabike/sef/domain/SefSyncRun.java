package com.megabike.sef.domain;

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
@Table(name = "sef_sync_run")
public class SefSyncRun {

	@Id
	@Column(name = "id", nullable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "connection_id", nullable = false)
	private SefConnection connection;

	@Enumerated(EnumType.STRING)
	@Column(name = "direction", nullable = false, length = 20)
	private SefInvoiceDirection direction;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 30)
	private SefSyncStatus status;

	@Column(name = "date_from")
	private Instant dateFrom;

	@Column(name = "date_to")
	private Instant dateTo;

	@Column(name = "started_at", nullable = false)
	private Instant startedAt;

	@Column(name = "finished_at")
	private Instant finishedAt;

	@Column(name = "invoice_count", nullable = false)
	private int invoiceCount;

	@Column(name = "document_count", nullable = false)
	private int documentCount;

	@Column(name = "error_message", length = 1000)
	private String errorMessage;

	protected SefSyncRun() {
	}

	public SefSyncRun(UUID id, SefConnection connection, SefInvoiceDirection direction, Instant dateFrom, Instant dateTo, Instant startedAt) {
		this.id = id;
		this.connection = connection;
		this.direction = direction;
		this.status = SefSyncStatus.RUNNING;
		this.dateFrom = dateFrom;
		this.dateTo = dateTo;
		this.startedAt = startedAt;
	}

	public void succeed(int invoiceCount, int documentCount, Instant finishedAt) {
		this.status = SefSyncStatus.SUCCEEDED;
		this.invoiceCount = invoiceCount;
		this.documentCount = documentCount;
		this.finishedAt = finishedAt;
	}

	public void fail(String errorMessage, Instant finishedAt) {
		this.status = SefSyncStatus.FAILED;
		this.errorMessage = errorMessage;
		this.finishedAt = finishedAt;
	}

	public UUID getId() { return id; }
	public SefConnection getConnection() { return connection; }
	public SefInvoiceDirection getDirection() { return direction; }
	public SefSyncStatus getStatus() { return status; }
	public Instant getDateFrom() { return dateFrom; }
	public Instant getDateTo() { return dateTo; }
	public Instant getStartedAt() { return startedAt; }
	public Instant getFinishedAt() { return finishedAt; }
	public int getInvoiceCount() { return invoiceCount; }
	public int getDocumentCount() { return documentCount; }
	public String getErrorMessage() { return errorMessage; }
}
