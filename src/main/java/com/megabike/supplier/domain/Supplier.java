package com.megabike.supplier.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "supplier")
public class Supplier {

	@Id
	@Column(name = "id", nullable = false)
	private UUID id;

	@Column(name = "code", nullable = false, unique = true, length = 50)
	private String code;

	@Column(name = "name", nullable = false, length = 200)
	private String name;

	@Column(name = "display_name", length = 200)
	private String displayName;

	@Column(name = "registration_number", length = 40)
	private String registrationNumber;

	@Column(name = "vat_registration_number", length = 40)
	private String vatRegistrationNumber;

	@Enumerated(EnumType.STRING)
	@Column(name = "classification", nullable = false, length = 30)
	private SupplierClassification classification = SupplierClassification.UNCLASSIFIED;

	@Column(name = "public_url", length = 500)
	private String publicUrl;

	@Column(name = "b2b_url", length = 500)
	private String b2bUrl;

	@Column(name = "default_currency", nullable = false, length = 3)
	private String defaultCurrency;

	@Column(name = "active", nullable = false)
	private boolean active;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at")
	private Instant updatedAt;

	@Version
	@Column(name = "version", nullable = false)
	private long version;

	protected Supplier() {
	}

	public Supplier(UUID id, String code, String name, String publicUrl, String b2bUrl, String defaultCurrency, boolean active, Instant createdAt) {
		this(id, code, name, null, null, publicUrl, b2bUrl, defaultCurrency, active, createdAt);
	}

	public Supplier(UUID id, String code, String name, String registrationNumber, String vatRegistrationNumber, String publicUrl, String b2bUrl, String defaultCurrency, boolean active, Instant createdAt) {
		this.id = id;
		this.code = code;
		this.name = name;
		this.registrationNumber = registrationNumber;
		this.vatRegistrationNumber = vatRegistrationNumber;
		this.publicUrl = publicUrl;
		this.b2bUrl = b2bUrl;
		this.defaultCurrency = defaultCurrency;
		this.active = active;
		this.createdAt = createdAt;
	}

	public void update(String code, String name, String publicUrl, String b2bUrl, String defaultCurrency, boolean active, Instant updatedAt) {
		update(code, name, this.registrationNumber, this.vatRegistrationNumber, publicUrl, b2bUrl, defaultCurrency, active, updatedAt);
	}

	public void update(String code, String name, String registrationNumber, String vatRegistrationNumber, String publicUrl, String b2bUrl, String defaultCurrency, boolean active, Instant updatedAt) {
		this.code = code;
		this.name = name;
		this.registrationNumber = registrationNumber;
		this.vatRegistrationNumber = vatRegistrationNumber;
		this.publicUrl = publicUrl;
		this.b2bUrl = b2bUrl;
		this.defaultCurrency = defaultCurrency;
		this.active = active;
		this.updatedAt = updatedAt;
	}

	public void updateClassification(SupplierClassification classification, Instant updatedAt) {
		this.classification = classification;
		this.updatedAt = updatedAt;
	}

	public void updateDisplayName(String displayName, Instant updatedAt) {
		this.displayName = displayName;
		this.updatedAt = updatedAt;
	}

	public UUID getId() { return id; }
	public String getCode() { return code; }
	public String getName() { return name; }
	public String getDisplayName() { return displayName; }
	public String getDisplayNameOrName() { return displayName == null || displayName.isBlank() ? name : displayName; }
	public String getRegistrationNumber() { return registrationNumber; }
	public String getVatRegistrationNumber() { return vatRegistrationNumber; }
	public SupplierClassification getClassification() { return classification; }
	public String getPublicUrl() { return publicUrl; }
	public String getB2bUrl() { return b2bUrl; }
	public String getDefaultCurrency() { return defaultCurrency; }
	public boolean isActive() { return active; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }
	public long getVersion() { return version; }
}
