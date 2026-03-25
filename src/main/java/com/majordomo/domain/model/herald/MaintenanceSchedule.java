package com.majordomo.domain.model.herald;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Defines a recurring maintenance task for a property, specifying the responsible contact,
 * recurrence {@link Frequency}, and the next due date.
 */
public class MaintenanceSchedule {

    private UUID id;
    private UUID propertyId;
    private UUID contactId;
    @NotBlank
    private String description;
    @NotNull
    private Frequency frequency;
    private Integer customIntervalDays;
    @NotNull
    private LocalDate nextDue;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant archivedAt;
    private Instant notificationSentAt;
    private BigDecimal estimatedCost;

    public MaintenanceSchedule() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getPropertyId() { return propertyId; }
    public void setPropertyId(UUID propertyId) { this.propertyId = propertyId; }

    public UUID getContactId() { return contactId; }
    public void setContactId(UUID contactId) { this.contactId = contactId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Frequency getFrequency() { return frequency; }
    public void setFrequency(Frequency frequency) { this.frequency = frequency; }

    public Integer getCustomIntervalDays() { return customIntervalDays; }
    public void setCustomIntervalDays(Integer customIntervalDays) { this.customIntervalDays = customIntervalDays; }

    public LocalDate getNextDue() { return nextDue; }
    public void setNextDue(LocalDate nextDue) { this.nextDue = nextDue; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getArchivedAt() { return archivedAt; }
    public void setArchivedAt(Instant archivedAt) { this.archivedAt = archivedAt; }

    public Instant getNotificationSentAt() { return notificationSentAt; }
    public void setNotificationSentAt(Instant notificationSentAt) { this.notificationSentAt = notificationSentAt; }

    public BigDecimal getEstimatedCost() { return estimatedCost; }
    public void setEstimatedCost(BigDecimal estimatedCost) { this.estimatedCost = estimatedCost; }
}
