package com.megabike.sef.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sef_connection")
public class SefConnection {

	@Id
	@Column(name = "id", nullable = false)
	private UUID id;

	@Column(name = "company_tax_id", nullable = false, length = 20)
	private String companyTaxId;

	@Column(name = "company_registration_number", length = 30)
	private String companyRegistrationNumber;

	@Column(name = "company_name", nullable = false, length = 240)
	private String companyName;

	@Column(name = "api_key_secret", nullable = false, length = 1000)
	private String apiKeySecret;

	@Column(name = "enabled", nullable = false)
	private boolean enabled;

	@Column(name = "last_purchase_sync_at")
	private Instant lastPurchaseSyncAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at")
	private Instant updatedAt;

	@Version
	@Column(name = "version", nullable = false)
	private long version;

	protected SefConnection() {
	}

	public SefConnection(UUID id, String companyTaxId, String companyRegistrationNumber, String companyName, String apiKeySecret, boolean enabled, Instant createdAt) {
		this.id = id;
		this.companyTaxId = companyTaxId;
		this.companyRegistrationNumber = companyRegistrationNumber;
		this.companyName = companyName;
		this.apiKeySecret = apiKeySecret;
		this.enabled = enabled;
		this.createdAt = createdAt;
	}

	public void markPurchaseSynced(Instant lastPurchaseSyncAt, Instant updatedAt) {
		this.lastPurchaseSyncAt = lastPurchaseSyncAt;
		this.updatedAt = updatedAt;
	}

	public void update(String companyTaxId, String companyRegistrationNumber, String companyName, String apiKeySecret, boolean enabled, Instant updatedAt) {
		this.companyTaxId = companyTaxId;
		this.companyRegistrationNumber = companyRegistrationNumber;
		this.companyName = companyName;
		this.apiKeySecret = apiKeySecret;
		this.enabled = enabled;
		this.updatedAt = updatedAt;
	}

	public UUID getId() { return id; }
	public String getCompanyTaxId() { return companyTaxId; }
	public String getCompanyRegistrationNumber() { return companyRegistrationNumber; }
	public String getCompanyName() { return companyName; }
	public String getApiKeySecret() { return apiKeySecret; }
	public boolean isEnabled() { return enabled; }
	public Instant getLastPurchaseSyncAt() { return lastPurchaseSyncAt; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }
	public long getVersion() { return version; }
}
