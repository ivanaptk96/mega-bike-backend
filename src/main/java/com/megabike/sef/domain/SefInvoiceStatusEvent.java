package com.megabike.sef.domain;

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
@Table(name = "sef_invoice_status_event")
public class SefInvoiceStatusEvent {

	@Id
	@Column(name = "id", nullable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "invoice_id", nullable = false)
	private SefInvoice invoice;

	@Column(name = "sef_event_id")
	private Integer sefEventId;

	@Column(name = "status", nullable = false, length = 80)
	private String status;

	@Column(name = "comment", length = 1000)
	private String comment;

	@Column(name = "event_at")
	private Instant eventAt;

	@Column(name = "subscription_key", length = 120)
	private String subscriptionKey;

	@Column(name = "raw_payload_json", columnDefinition = "jsonb")
	@JdbcTypeCode(SqlTypes.JSON)
	private String rawPayloadJson;

	protected SefInvoiceStatusEvent() {
	}

	public SefInvoiceStatusEvent(UUID id, SefInvoice invoice, String status) {
		this.id = id;
		this.invoice = invoice;
		this.status = status;
	}

	public UUID getId() { return id; }
	public SefInvoice getInvoice() { return invoice; }
	public Integer getSefEventId() { return sefEventId; }
	public String getStatus() { return status; }
	public String getComment() { return comment; }
	public Instant getEventAt() { return eventAt; }
	public String getSubscriptionKey() { return subscriptionKey; }
	public String getRawPayloadJson() { return rawPayloadJson; }
}
