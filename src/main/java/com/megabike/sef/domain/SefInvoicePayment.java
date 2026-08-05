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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sef_invoice_payment")
public class SefInvoicePayment {

	@Id
	@Column(name = "id", nullable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "invoice_id", nullable = false)
	private SefInvoice invoice;

	@Column(name = "payment_means_code", length = 80)
	private String paymentMeansCode;

	@Column(name = "payment_id", length = 120)
	private String paymentId;

	@Column(name = "account_number", length = 120)
	private String accountNumber;

	@Column(name = "account_name", length = 240)
	private String accountName;

	@Column(name = "model_number", length = 20)
	private String modelNumber;

	@Column(name = "reference_number", length = 120)
	private String referenceNumber;

	@Column(name = "amount", precision = 14, scale = 2)
	private BigDecimal amount;

	@Column(name = "currency", length = 3)
	private String currency;

	@Column(name = "due_at")
	private Instant dueAt;

	@Column(name = "raw_payload_json", columnDefinition = "jsonb")
	@JdbcTypeCode(SqlTypes.JSON)
	private String rawPayloadJson;

	protected SefInvoicePayment() {
	}

	public SefInvoicePayment(UUID id, SefInvoice invoice) {
		this.id = id;
		this.invoice = invoice;
	}

	public SefInvoicePayment(
			UUID id,
			SefInvoice invoice,
			String paymentMeansCode,
			String paymentId,
			String accountNumber,
			String accountName,
			String modelNumber,
			String referenceNumber,
			BigDecimal amount,
			String currency,
			Instant dueAt,
			String rawPayloadJson
	) {
		this.id = id;
		this.invoice = invoice;
		this.paymentMeansCode = paymentMeansCode;
		this.paymentId = paymentId;
		this.accountNumber = accountNumber;
		this.accountName = accountName;
		this.modelNumber = modelNumber;
		this.referenceNumber = referenceNumber;
		this.amount = amount;
		this.currency = currency;
		this.dueAt = dueAt;
		this.rawPayloadJson = rawPayloadJson;
	}

	public UUID getId() { return id; }
	public SefInvoice getInvoice() { return invoice; }
	public String getPaymentMeansCode() { return paymentMeansCode; }
	public String getPaymentId() { return paymentId; }
	public String getAccountNumber() { return accountNumber; }
	public String getAccountName() { return accountName; }
	public String getModelNumber() { return modelNumber; }
	public String getReferenceNumber() { return referenceNumber; }
	public BigDecimal getAmount() { return amount; }
	public String getCurrency() { return currency; }
	public Instant getDueAt() { return dueAt; }
	public String getRawPayloadJson() { return rawPayloadJson; }
}
