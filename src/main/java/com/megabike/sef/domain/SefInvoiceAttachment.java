package com.megabike.sef.domain;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sef_invoice_attachment")
public class SefInvoiceAttachment {

	@Id
	@Column(name = "id", nullable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "invoice_id", nullable = false)
	private SefInvoice invoice;

	@Column(name = "sef_attachment_id", length = 120)
	private String sefAttachmentId;

	@Column(name = "filename", nullable = false, length = 300)
	private String filename;

	@Column(name = "content_type", length = 120)
	private String contentType;

	@Column(name = "sha256_hash", length = 64)
	private String sha256Hash;

	@Basic(fetch = FetchType.LAZY)
	@Column(name = "content")
	private byte[] content;

	@Column(name = "downloaded_at")
	private Instant downloadedAt;

	@Column(name = "raw_payload_json", columnDefinition = "jsonb")
	@JdbcTypeCode(SqlTypes.JSON)
	private String rawPayloadJson;

	protected SefInvoiceAttachment() {
	}

	public SefInvoiceAttachment(UUID id, SefInvoice invoice, String filename) {
		this.id = id;
		this.invoice = invoice;
		this.filename = filename;
	}

	public UUID getId() { return id; }
	public SefInvoice getInvoice() { return invoice; }
	public String getSefAttachmentId() { return sefAttachmentId; }
	public String getFilename() { return filename; }
	public String getContentType() { return contentType; }
	public String getSha256Hash() { return sha256Hash; }
	public byte[] getContent() { return content; }
	public Instant getDownloadedAt() { return downloadedAt; }
	public String getRawPayloadJson() { return rawPayloadJson; }
}
