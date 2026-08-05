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
@Table(name = "sef_invoice_line")
public class SefInvoiceLine {

	@Id
	@Column(name = "id", nullable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "invoice_id", nullable = false)
	private SefInvoice invoice;

	@Column(name = "line_number", nullable = false)
	private int lineNumber;

	@Column(name = "external_line_id", length = 120)
	private String externalLineId;

	@Column(name = "item_name", nullable = false, length = 500)
	private String itemName;

	@Column(name = "item_description", length = 4000)
	private String itemDescription;

	@Column(name = "seller_item_id", length = 120)
	private String sellerItemId;

	@Column(name = "buyer_item_id", length = 120)
	private String buyerItemId;

	@Column(name = "unit_code", length = 40)
	private String unitCode;

	@Column(name = "quantity", precision = 14, scale = 3)
	private BigDecimal quantity;

	@Column(name = "unit_price", precision = 14, scale = 4)
	private BigDecimal unitPrice;

	@Column(name = "discount_amount", precision = 14, scale = 2)
	private BigDecimal discountAmount;

	@Column(name = "line_amount_without_vat", precision = 14, scale = 2)
	private BigDecimal lineAmountWithoutVat;

	@Column(name = "vat_category", length = 40)
	private String vatCategory;

	@Column(name = "vat_rate", precision = 5, scale = 2)
	private BigDecimal vatRate;

	@Column(name = "vat_amount", precision = 14, scale = 2)
	private BigDecimal vatAmount;

	@Column(name = "raw_payload_json", columnDefinition = "jsonb")
	@JdbcTypeCode(SqlTypes.JSON)
	private String rawPayloadJson;

	protected SefInvoiceLine() {
	}

	public SefInvoiceLine(UUID id, SefInvoice invoice, int lineNumber, String itemName) {
		this.id = id;
		this.invoice = invoice;
		this.lineNumber = lineNumber;
		this.itemName = itemName;
	}

	public SefInvoiceLine(
			UUID id,
			SefInvoice invoice,
			int lineNumber,
			String externalLineId,
			String itemName,
			String itemDescription,
			String sellerItemId,
			String buyerItemId,
			String unitCode,
			BigDecimal quantity,
			BigDecimal unitPrice,
			BigDecimal discountAmount,
			BigDecimal lineAmountWithoutVat,
			String vatCategory,
			BigDecimal vatRate,
			BigDecimal vatAmount,
			String rawPayloadJson
	) {
		this.id = id;
		this.invoice = invoice;
		this.lineNumber = lineNumber;
		this.externalLineId = externalLineId;
		this.itemName = itemName;
		this.itemDescription = itemDescription;
		this.sellerItemId = sellerItemId;
		this.buyerItemId = buyerItemId;
		this.unitCode = unitCode;
		this.quantity = quantity;
		this.unitPrice = unitPrice;
		this.discountAmount = discountAmount;
		this.lineAmountWithoutVat = lineAmountWithoutVat;
		this.vatCategory = vatCategory;
		this.vatRate = vatRate;
		this.vatAmount = vatAmount;
		this.rawPayloadJson = rawPayloadJson;
	}

	public UUID getId() { return id; }
	public SefInvoice getInvoice() { return invoice; }
	public int getLineNumber() { return lineNumber; }
	public String getExternalLineId() { return externalLineId; }
	public String getItemName() { return itemName; }
	public String getItemDescription() { return itemDescription; }
	public String getSellerItemId() { return sellerItemId; }
	public String getBuyerItemId() { return buyerItemId; }
	public String getUnitCode() { return unitCode; }
	public BigDecimal getQuantity() { return quantity; }
	public BigDecimal getUnitPrice() { return unitPrice; }
	public BigDecimal getDiscountAmount() { return discountAmount; }
	public BigDecimal getLineAmountWithoutVat() { return lineAmountWithoutVat; }
	public String getVatCategory() { return vatCategory; }
	public BigDecimal getVatRate() { return vatRate; }
	public BigDecimal getVatAmount() { return vatAmount; }
	public String getRawPayloadJson() { return rawPayloadJson; }
}
