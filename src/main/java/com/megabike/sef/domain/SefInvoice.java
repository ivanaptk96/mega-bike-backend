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
import jakarta.persistence.Version;
import com.megabike.supplier.domain.Supplier;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sef_invoice")
public class SefInvoice {

	@Id
	@Column(name = "id", nullable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "connection_id", nullable = false)
	private SefConnection connection;

	@Column(name = "sef_invoice_id", nullable = false)
	private Long sefInvoiceId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "supplier_id")
	private Supplier supplier;

	@Enumerated(EnumType.STRING)
	@Column(name = "direction", nullable = false, length = 20)
	private SefInvoiceDirection direction;

	@Column(name = "global_unique_id", length = 120)
	private String globalUniqueId;

	@Column(name = "cir_invoice_id", length = 120)
	private String cirInvoiceId;

	@Column(name = "invoice_number", length = 200)
	private String invoiceNumber;

	@Column(name = "document_type", length = 80)
	private String documentType;

	@Column(name = "status", nullable = false, length = 80)
	private String status;

	@Column(name = "counterparty_name", length = 500)
	private String counterpartyName;

	@Column(name = "counterparty_registration_number", length = 40)
	private String counterpartyRegistrationNumber;

	@Column(name = "counterparty_vat_registration_number", length = 40)
	private String counterpartyVatRegistrationNumber;

	@Column(name = "currency", length = 3)
	private String currency;

	@Column(name = "amount_without_vat", precision = 14, scale = 2)
	private BigDecimal amountWithoutVat;

	@Column(name = "vat_amount", precision = 14, scale = 2)
	private BigDecimal vatAmount;

	@Column(name = "total_amount", precision = 14, scale = 2)
	private BigDecimal totalAmount;

	@Column(name = "sent_at")
	private Instant sentAt;

	@Column(name = "due_at")
	private Instant dueAt;

	@Column(name = "raw_payload_json", columnDefinition = "jsonb")
	@JdbcTypeCode(SqlTypes.JSON)
	private String rawPayloadJson;

	@Column(name = "imported_at", nullable = false)
	private Instant importedAt;

	@Column(name = "updated_at")
	private Instant updatedAt;

	@Version
	@Column(name = "version", nullable = false)
	private long version;

	protected SefInvoice() {
	}

	public SefInvoice(UUID id, SefConnection connection, Long sefInvoiceId, SefInvoiceDirection direction, Instant importedAt) {
		this.id = id;
		this.connection = connection;
		this.sefInvoiceId = sefInvoiceId;
		this.direction = direction;
		this.status = "Unknown";
		this.importedAt = importedAt;
	}

	public void applyPurchaseOverview(
			Supplier supplier,
			String globalUniqueId,
			String cirInvoiceId,
			String invoiceNumber,
			String documentType,
			String status,
			String counterpartyName,
			String counterpartyRegistrationNumber,
			String counterpartyVatRegistrationNumber,
			String currency,
			BigDecimal amountWithoutVat,
			BigDecimal vatAmount,
			BigDecimal totalAmount,
			Instant sentAt,
			Instant dueAt,
			String rawPayloadJson,
			Instant updatedAt
	) {
		this.supplier = supplier;
		this.globalUniqueId = globalUniqueId;
		this.cirInvoiceId = cirInvoiceId;
		this.invoiceNumber = invoiceNumber;
		this.documentType = documentType;
		this.status = status;
		this.counterpartyName = counterpartyName;
		this.counterpartyRegistrationNumber = counterpartyRegistrationNumber;
		this.counterpartyVatRegistrationNumber = counterpartyVatRegistrationNumber;
		this.currency = currency;
		this.amountWithoutVat = amountWithoutVat;
		this.vatAmount = vatAmount;
		this.totalAmount = totalAmount;
		this.sentAt = sentAt;
		this.dueAt = dueAt;
		this.rawPayloadJson = rawPayloadJson;
		this.updatedAt = updatedAt;
	}

	public UUID getId() { return id; }
	public SefConnection getConnection() { return connection; }
	public Long getSefInvoiceId() { return sefInvoiceId; }
	public Supplier getSupplier() { return supplier; }
	public SefInvoiceDirection getDirection() { return direction; }
	public String getGlobalUniqueId() { return globalUniqueId; }
	public String getCirInvoiceId() { return cirInvoiceId; }
	public String getInvoiceNumber() { return invoiceNumber; }
	public String getDocumentType() { return documentType; }
	public String getStatus() { return status; }
	public String getCounterpartyName() { return counterpartyName; }
	public String getCounterpartyRegistrationNumber() { return counterpartyRegistrationNumber; }
	public String getCounterpartyVatRegistrationNumber() { return counterpartyVatRegistrationNumber; }
	public String getCurrency() { return currency; }
	public BigDecimal getAmountWithoutVat() { return amountWithoutVat; }
	public BigDecimal getVatAmount() { return vatAmount; }
	public BigDecimal getTotalAmount() { return totalAmount; }
	public Instant getSentAt() { return sentAt; }
	public Instant getDueAt() { return dueAt; }
	public String getRawPayloadJson() { return rawPayloadJson; }
	public Instant getImportedAt() { return importedAt; }
	public Instant getUpdatedAt() { return updatedAt; }
	public long getVersion() { return version; }
}
