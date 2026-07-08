package com.majordomo.adapter.out.persistence.steward;

import com.majordomo.IntegrationTest;
import com.majordomo.adapter.out.persistence.attachment.AttachmentEntity;
import com.majordomo.adapter.out.persistence.attachment.JpaAttachmentRepository;
import com.majordomo.domain.model.EntityType;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.identity.Organization;
import com.majordomo.domain.model.steward.Property;
import com.majordomo.domain.model.steward.PropertyStatus;
import com.majordomo.domain.port.out.identity.OrganizationRepository;
import com.majordomo.domain.port.out.steward.PropertyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Postgres full-text-search behaviour for property search. FTS (tsvector +
 * to_tsquery) is Postgres-specific, so this must run against a real database
 * via Testcontainers rather than the H2 slice. Every query is scoped to a
 * freshly-generated organization id so data seeded by other tests cannot
 * affect exact-count assertions.
 */
@IntegrationTest
class PropertyFullTextSearchIntegrationTest {

    @Autowired
    private PropertyRepository properties;

    @Autowired
    private OrganizationRepository organizations;

    @Autowired
    private JpaAttachmentRepository attachments;

    private UUID newOrg() {
        UUID id = UuidFactory.newId();
        organizations.save(new Organization(id, "org-" + id));
        return id;
    }

    private Property property(UUID orgId, String name, String description, String manufacturer) {
        Property p = new Property();
        p.setId(UuidFactory.newId());
        p.setOrganizationId(orgId);
        p.setName(name);
        p.setDescription(description);
        p.setManufacturer(manufacturer);
        p.setStatus(PropertyStatus.ACTIVE);
        return properties.save(p);
    }

    private void attachTo(UUID propertyId, String filename) {
        AttachmentEntity a = new AttachmentEntity();
        a.setId(UuidFactory.newId());
        a.setEntityType(EntityType.PROPERTY.name());
        a.setEntityId(propertyId);
        a.setFilename(filename);
        a.setContentType("application/pdf");
        a.setSizeBytes(1024L);
        a.setStoragePath("s/" + filename);
        a.setCreatedAt(Instant.now());
        a.setUpdatedAt(Instant.now());
        a.setPrimary(false);
        a.setSortOrder(0);
        attachments.save(a);
    }

    @Test
    void matchesOnPropertyTextField() {
        UUID org = newOrg();
        Property furnace = property(org, "Furnace", "gas heating unit", "Carrier");
        property(org, "Dishwasher", "kitchen appliance", "Bosch");

        List<Property> hits = properties.search(org, "furnace", null, null, null, 20);

        assertThat(hits).extracting(Property::getId).containsExactly(furnace.getId());
    }

    @Test
    void matchesWithStemming() {
        UUID org = newOrg();
        Property furnace = property(org, "Furnace", "gas heating unit", "Carrier");

        // "heats" stems to "heat", which also matches the stored "heating" —
        // a substring LIKE for "heats" would not.
        List<Property> hits = properties.search(org, "heats", null, null, null, 20);

        assertThat(hits).extracting(Property::getId).containsExactly(furnace.getId());
    }

    @Test
    void matchesOnAttachmentFilename() {
        UUID org = newOrg();
        Property furnace = property(org, "Furnace", "gas heating unit", "Carrier");
        attachTo(furnace.getId(), "furnace-installation-manual.pdf");
        property(org, "Dishwasher", "kitchen appliance", "Bosch");

        List<Property> hits = properties.search(org, "manual", null, null, null, 20);

        assertThat(hits).extracting(Property::getId).containsExactly(furnace.getId());
    }

    @Test
    void noMatchReturnsEmpty() {
        UUID org = newOrg();
        property(org, "Furnace", "gas heating unit", "Carrier");

        assertThat(properties.search(org, "refrigerator", null, null, null, 20)).isEmpty();
    }

    @Test
    void searchIsScopedToOrganization() {
        UUID orgA = newOrg();
        UUID orgB = newOrg();
        property(orgA, "Furnace", "gas heating unit", "Carrier");

        assertThat(properties.search(orgB, "furnace", null, null, null, 20)).isEmpty();
    }

    @Test
    void honoursLimit() {
        UUID org = newOrg();
        property(org, "Furnace one", "heating", "Carrier");
        property(org, "Furnace two", "heating", "Carrier");
        property(org, "Furnace three", "heating", "Carrier");

        assertThat(properties.search(org, "furnace", null, null, null, 2)).hasSize(2);
    }
}
