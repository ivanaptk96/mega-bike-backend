package com.megabike.sef.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SefInvoiceDocumentRepository extends JpaRepository<SefInvoiceDocument, UUID> {

	Optional<SefInvoiceDocument> findByInvoiceIdAndDocumentType(UUID invoiceId, SefInvoiceDocumentType documentType);

	Optional<SefInvoiceDocument> findByIdAndInvoiceId(UUID id, UUID invoiceId);

	List<SefInvoiceDocument> findByInvoiceIdOrderByDocumentTypeAsc(UUID invoiceId);
}
