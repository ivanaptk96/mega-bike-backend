package com.megabike.sef.domain;

import jakarta.persistence.Basic;
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
@Table(name = "sef_invoice_document")
public class SefInvoiceDocument {

	@Id
	@Column(name = "id", nullable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "invoice_id", nullable = false)
	private SefInvoice invoice;

	@Enumerated(EnumType.STRING)
	@Column(name = "document_type", nullable = false, length = 40)
	private SefInvoiceDocumentType documentType;

	@Column(name = "filename", nullable = false, length = 300)
	private String filename;

	@Column(name = "content_type", nullable = false, length = 120)
	private String contentType;

	@Column(name = "sha256_hash", nullable = false, length = 64)
	private String sha256Hash;

	@Basic(fetch = FetchType.LAZY)
	@Column(name = "content", nullable = false)
	private byte[] content;

	@Column(name = "downloaded_at", nullable = false)
	private Instant downloadedAt;

	protected SefInvoiceDocument() {
	}

	public SefInvoiceDocument(UUID id, SefInvoice invoice, SefInvoiceDocumentType documentType, String filename, String contentType, String sha256Hash, byte[] content, Instant downloadedAt) {
		this.id = id;
		this.invoice = invoice;
		this.documentType = documentType;
		this.filename = filename;
		this.contentType = contentType;
		this.sha256Hash = sha256Hash;
		this.content = content;
		this.downloadedAt = downloadedAt;
	}

	public UUID getId() { return id; }
	public SefInvoice getInvoice() { return invoice; }
	public SefInvoiceDocumentType getDocumentType() { return documentType; }
	public String getFilename() { return filename; }
	public String getContentType() { return contentType; }
	public String getSha256Hash() { return sha256Hash; }
	public byte[] getContent() { return content; }
	public Instant getDownloadedAt() { return downloadedAt; }
}
