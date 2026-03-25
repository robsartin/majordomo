package com.majordomo.domain.model.concierge;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A person or company in the organization's address book, modeled after the vCard standard.
 * Contacts may serve as vendors, service providers, or other roles linked to managed properties.
 */
public class Contact {

    private UUID id;
    private UUID organizationId;
    @NotBlank
    private String formattedName;
    private String familyName;
    private String givenName;
    private List<String> nicknames;
    private List<String> emails;
    private List<String> telephones;
    private List<String> urls;
    private String organization;
    private String title;
    private String notes;
    private List<Address> addresses;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant archivedAt;

    public Contact() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }

    public String getFormattedName() { return formattedName; }
    public void setFormattedName(String formattedName) { this.formattedName = formattedName; }

    public String getFamilyName() { return familyName; }
    public void setFamilyName(String familyName) { this.familyName = familyName; }

    public String getGivenName() { return givenName; }
    public void setGivenName(String givenName) { this.givenName = givenName; }

    public List<String> getNicknames() { return nicknames; }
    public void setNicknames(List<String> nicknames) { this.nicknames = nicknames; }

    public List<String> getEmails() { return emails; }
    public void setEmails(List<String> emails) { this.emails = emails; }

    public List<String> getTelephones() { return telephones; }
    public void setTelephones(List<String> telephones) { this.telephones = telephones; }

    public List<String> getUrls() { return urls; }
    public void setUrls(List<String> urls) { this.urls = urls; }

    public String getOrganization() { return organization; }
    public void setOrganization(String organization) { this.organization = organization; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public List<Address> getAddresses() { return addresses; }
    public void setAddresses(List<Address> addresses) { this.addresses = addresses; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getArchivedAt() { return archivedAt; }
    public void setArchivedAt(Instant archivedAt) { this.archivedAt = archivedAt; }
}
