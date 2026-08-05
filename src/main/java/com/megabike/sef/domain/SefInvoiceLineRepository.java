package com.megabike.sef.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SefInvoiceLineRepository extends JpaRepository<SefInvoiceLine, UUID> {

	void deleteByInvoiceId(UUID invoiceId);
}
