package com.megabike.inventory.domain;

import com.megabike.sef.domain.SefInvoice;
import jakarta.persistence.*;
import lombok.Getter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "goods_receipt")
@Getter
public class GoodsReceipt {
	@Id private UUID id;
	@OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "invoice_id", nullable = false, unique = true) private SefInvoice invoice;
	@ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "stock_location_id", nullable = false) private StockLocation stockLocation;
	@Enumerated(EnumType.STRING) @Column(nullable = false) private ReceiptStatus status;
	@Column(name = "created_at", nullable = false) private Instant createdAt;
	@Column(name = "updated_at", nullable = false) private Instant updatedAt;
	@Version private long version;
	protected GoodsReceipt() {}
	public GoodsReceipt(SefInvoice invoice, StockLocation location, Instant now) {
		id = UUID.randomUUID(); this.invoice = invoice; stockLocation = location;
		status = ReceiptStatus.PENDING; createdAt = now; updatedAt = now;
	}
	public void updateStatus(ReceiptStatus status, Instant now) { this.status = status; updatedAt = now; }
}
