package com.megabike.sef.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SefInvoiceDocumentRepository extends JpaRepository<SefInvoiceDocument, UUID> {

	Optional<SefInvoiceDocument> findByInvoiceIdAndDocumentType(UUID invoiceId, SefInvoiceDocumentType documentType);
}
