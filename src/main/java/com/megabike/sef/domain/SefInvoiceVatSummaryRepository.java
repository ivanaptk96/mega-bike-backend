package com.megabike.sef.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SefInvoiceVatSummaryRepository extends JpaRepository<SefInvoiceVatSummary, UUID> {

	void deleteByInvoiceId(UUID invoiceId);

	List<SefInvoiceVatSummary> findByInvoiceIdOrderByVatRateAsc(UUID invoiceId);
}
