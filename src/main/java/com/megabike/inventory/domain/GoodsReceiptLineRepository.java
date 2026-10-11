package com.megabike.inventory.domain;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface GoodsReceiptLineRepository extends JpaRepository<GoodsReceiptLine, UUID> {
	List<GoodsReceiptLine> findByReceiptIdOrderByLineNumberAsc(UUID receiptId);
}
