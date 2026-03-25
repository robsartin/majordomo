package com.majordomo.domain.model.steward;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Represents a managed asset or property item owned by an organization.
 * Properties may be nested hierarchically via {@code parentId} and track acquisition,
 * warranty, and lifecycle status information.
 */
public class Property {

    private UUID id;
    private UUID organizationId;
    private UUID parentId;
    @NotBlank
    private String name;
    private String description;
    private String serialNumber;
    private String modelNumber;
    private String manufacturer;
    private String category;
    private String location;
    private PropertyStatus status;
    private LocalDate acquiredOn;
    private LocalDate warrantyExpiresOn;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant archivedAt;
    private BigDecimal purchasePrice;
    private Instant warrantyNotificationSentAt;

    public Property() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }

    public UUID getParentId() { return parentId; }
    public void setParentId(UUID parentId) { this.parentId = parentId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }

    public String getModelNumber() { return modelNumber; }
    public void setModelNumber(String modelNumber) { this.modelNumber = modelNumber; }

    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public PropertyStatus getStatus() { return status; }
    public void setStatus(PropertyStatus status) { this.status = status; }

    public LocalDate getAcquiredOn() { return acquiredOn; }
    public void setAcquiredOn(LocalDate acquiredOn) { this.acquiredOn = acquiredOn; }

    public LocalDate getWarrantyExpiresOn() { return warrantyExpiresOn; }
    public void setWarrantyExpiresOn(LocalDate warrantyExpiresOn) { this.warrantyExpiresOn = warrantyExpiresOn; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getArchivedAt() { return archivedAt; }
    public void setArchivedAt(Instant archivedAt) { this.archivedAt = archivedAt; }

    /** Returns the purchase price of the property. */
    public BigDecimal getPurchasePrice() { return purchasePrice; }

    /** Sets the purchase price of the property. */
    public void setPurchasePrice(BigDecimal purchasePrice) { this.purchasePrice = purchasePrice; }

    /** Returns the timestamp when the warranty expiration notification was sent, or null if not yet sent. */
    public Instant getWarrantyNotificationSentAt() { return warrantyNotificationSentAt; }

    /** Sets the timestamp when the warranty expiration notification was sent. */
    public void setWarrantyNotificationSentAt(Instant warrantyNotificationSentAt) {
        this.warrantyNotificationSentAt = warrantyNotificationSentAt;
    }
}
