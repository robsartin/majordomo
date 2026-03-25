package com.majordomo.domain.model.herald;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Records a completed service or maintenance event performed on a property.
 * May optionally reference the {@link MaintenanceSchedule} that prompted the work.
 */
public class ServiceRecord {

    private UUID id;
    private UUID propertyId;
    private UUID contactId;
    private UUID scheduleId;
    @NotNull
    private LocalDate performedOn;
    @NotBlank
    private String description;
    private String notes;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant archivedAt;
    private BigDecimal cost;

    public ServiceRecord() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getPropertyId() { return propertyId; }
    public void setPropertyId(UUID propertyId) { this.propertyId = propertyId; }

    public UUID getContactId() { return contactId; }
    public void setContactId(UUID contactId) { this.contactId = contactId; }

    public UUID getScheduleId() { return scheduleId; }
    public void setScheduleId(UUID scheduleId) { this.scheduleId = scheduleId; }

    public LocalDate getPerformedOn() { return performedOn; }
    public void setPerformedOn(LocalDate performedOn) { this.performedOn = performedOn; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getArchivedAt() { return archivedAt; }
    public void setArchivedAt(Instant archivedAt) { this.archivedAt = archivedAt; }

    /** Returns the cost of the service. */
    public BigDecimal getCost() { return cost; }

    /** Sets the cost of the service. */
    public void setCost(BigDecimal cost) { this.cost = cost; }
}
