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
import java.util.UUID;

@Entity
@Table(name = "sef_invoice_vat_summary")
public class SefInvoiceVatSummary {

	@Id
	@Column(name = "id", nullable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "invoice_id", nullable = false)
	private SefInvoice invoice;

	@Column(name = "vat_category", nullable = false, length = 40)
	private String vatCategory;

	@Column(name = "vat_rate", precision = 5, scale = 2)
	private BigDecimal vatRate;

	@Column(name = "taxable_amount", precision = 14, scale = 2)
	private BigDecimal taxableAmount;

	@Column(name = "vat_amount", precision = 14, scale = 2)
	private BigDecimal vatAmount;

	@Column(name = "exemption_reason_code", length = 80)
	private String exemptionReasonCode;

	@Column(name = "exemption_reason", length = 500)
	private String exemptionReason;

	@Column(name = "raw_payload_json", columnDefinition = "jsonb")
	@JdbcTypeCode(SqlTypes.JSON)
	private String rawPayloadJson;

	protected SefInvoiceVatSummary() {
	}

	public SefInvoiceVatSummary(UUID id, SefInvoice invoice, String vatCategory) {
		this.id = id;
		this.invoice = invoice;
		this.vatCategory = vatCategory;
	}

	public SefInvoiceVatSummary(
			UUID id,
			SefInvoice invoice,
			String vatCategory,
			BigDecimal vatRate,
			BigDecimal taxableAmount,
			BigDecimal vatAmount,
			String exemptionReasonCode,
			String exemptionReason,
			String rawPayloadJson
	) {
		this.id = id;
		this.invoice = invoice;
		this.vatCategory = vatCategory;
		this.vatRate = vatRate;
		this.taxableAmount = taxableAmount;
		this.vatAmount = vatAmount;
		this.exemptionReasonCode = exemptionReasonCode;
		this.exemptionReason = exemptionReason;
		this.rawPayloadJson = rawPayloadJson;
	}

	public UUID getId() { return id; }
	public SefInvoice getInvoice() { return invoice; }
	public String getVatCategory() { return vatCategory; }
	public BigDecimal getVatRate() { return vatRate; }
	public BigDecimal getTaxableAmount() { return taxableAmount; }
	public BigDecimal getVatAmount() { return vatAmount; }
	public String getExemptionReasonCode() { return exemptionReasonCode; }
	public String getExemptionReason() { return exemptionReason; }
	public String getRawPayloadJson() { return rawPayloadJson; }
}
