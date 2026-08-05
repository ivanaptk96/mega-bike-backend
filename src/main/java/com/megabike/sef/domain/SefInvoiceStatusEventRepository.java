package com.megabike.sef.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SefInvoiceStatusEventRepository extends JpaRepository<SefInvoiceStatusEvent, UUID> {
}
