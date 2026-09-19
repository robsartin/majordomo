package com.majordomo.adapter.out.persistence.steward;

import com.majordomo.IntegrationTest;
import com.majordomo.adapter.out.extraction.PdfBoxTextExtractionAdapter;
import com.majordomo.adapter.out.persistence.attachment.AttachmentEntity;
import com.majordomo.adapter.out.persistence.attachment.JpaAttachmentRepository;
import com.majordomo.domain.model.EntityType;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.attachment.ExtractionStatus;
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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property search over the text inside attached documents (#298).
 *
 * <p>The generated tsvector column and its GIN index are Postgres-specific, so
 * this runs against a real database. Every query is scoped to a fresh
 * organization id so other tests' data cannot affect exact-count assertions.
 */
@IntegrationTest
class AttachmentContentSearchIntegrationTest {

    @Autowired
    private PropertyRepository properties;

    @Autowired
    private OrganizationRepository organizations;

    @Autowired
    private JpaAttachmentRepository attachments;

    /**
     * The point of the whole issue: a manual is findable by what it says, not
     * only by what it is called.
     *
     * <p>The filename is deliberately meaningless — a camera's own name for a
     * scan. If it carried the search words, the filename index added in #291
     * could satisfy this on its own and the test would pass without the feature
     * it exists to check.
     */
    @Test
    void findsAPropertyByWordsInsideAnAttachedDocument() {
        UUID org = newOrg();
        Property furnace = property(org, "Utility room");
        attachExtracted(furnace.getId(), "scan-0042.pdf",
                "Carrier 58STA gas furnace. Ignition lockout after three failed trials.");

        Property garage = property(org, "Garage");
        attachExtracted(garage.getId(), "scan-0043.pdf", "Tins of paint and a roller tray.");

        List<Property> hits = properties.search(org, "ignition lockout", null, null, null, 20);

        assertThat(hits).extracting(Property::getId).containsExactly(furnace.getId());
    }

    /** Documents still awaiting extraction simply do not match yet. */
    @Test
    void doesNotMatchAnAttachmentWhoseTextHasNotBeenExtractedYet() {
        UUID org = newOrg();
        Property furnace = property(org, "Utility room");
        attachPending(furnace.getId(), "scan-0044.pdf");

        assertThat(properties.search(org, "ignition", null, null, null, 20)).isEmpty();
    }

    /**
     * Postgres refuses a tsvector over 1MB and the search column is generated,
     * so an oversized document would make the row impossible to insert. This
     * writes the extractor's cap in its worst case — every word distinct — and
     * requires that Postgres both accepts it and indexes it.
     */
    @Test
    void acceptsAndIndexesTextAtTheExtractorsCap() {
        UUID org = newOrg();
        Property furnace = property(org, "Utility room");
        attachExtracted(furnace.getId(), "scan-0045.pdf", distinctWordsFillingTheCap());

        List<Property> hits = properties.search(org, "lexeme424242", null, null, null, 20);

        assertThat(hits).extracting(Property::getId).containsExactly(furnace.getId());
    }

    /** Worst case for tsvector size: no repeated lexemes to collapse. */
    private static String distinctWordsFillingTheCap() {
        String text = IntStream.range(0, 40_000)
                .mapToObj(i -> "lexeme" + (424242 + i))
                .collect(Collectors.joining(" "));
        return text.substring(0, Math.min(text.length(), PdfBoxTextExtractionAdapter.MAX_TEXT_CHARS));
    }

    private UUID newOrg() {
        UUID id = UuidFactory.newId();
        organizations.save(new Organization(id, "org-" + id));
        return id;
    }

    private Property property(UUID orgId, String name) {
        Property p = new Property();
        p.setId(UuidFactory.newId());
        p.setOrganizationId(orgId);
        p.setName(name);
        p.setStatus(PropertyStatus.ACTIVE);
        return properties.save(p);
    }

    private void attachExtracted(UUID propertyId, String filename, String text) {
        AttachmentEntity a = attachment(propertyId, filename);
        a.setExtractionStatus(ExtractionStatus.EXTRACTED);
        a.setExtractedText(text);
        a.setTextExtractedAt(Instant.now());
        attachments.save(a);
    }

    private void attachPending(UUID propertyId, String filename) {
        attachments.save(attachment(propertyId, filename));
    }

    private static AttachmentEntity attachment(UUID propertyId, String filename) {
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
        return a;
    }
}
