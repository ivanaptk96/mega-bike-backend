package com.megabike.inventory.domain;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface GoodsReceiptRepository extends JpaRepository<GoodsReceipt, UUID> {
	Optional<GoodsReceipt> findByInvoiceId(UUID invoiceId);
}
