package com.megabike.sef.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SefInvoicePaymentRepository extends JpaRepository<SefInvoicePayment, UUID> {

	void deleteByInvoiceId(UUID invoiceId);

	List<SefInvoicePayment> findByInvoiceIdOrderByIdAsc(UUID invoiceId);
}
