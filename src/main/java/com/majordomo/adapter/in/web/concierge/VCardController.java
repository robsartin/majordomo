package com.majordomo.adapter.in.web.concierge;

import com.majordomo.domain.model.concierge.Contact;
import com.majordomo.domain.port.in.concierge.ManageContactUseCase;

import ezvcard.Ezvcard;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for vCard import and export of contacts.
 *
 * <p>Provides endpoints to export contacts as {@code .vcf} files and to import contacts
 * from uploaded {@code .vcf} files. Acts as an inbound adapter in the hexagonal architecture,
 * delegating to {@link ManageContactUseCase}.</p>
 */
@RestController
@RequestMapping("/api/contacts")
@Tag(name = "Concierge", description = "Contact management")
public class VCardController {

    private static final Logger LOG = LoggerFactory.getLogger(VCardController.class);
    private static final String VCARD_MEDIA_TYPE = "text/vcard";

    private final ManageContactUseCase contactUseCase;

    /**
     * Constructs a {@code VCardController} with the given contact use case.
     *
     * @param contactUseCase the inbound port for contact management
     */
    public VCardController(ManageContactUseCase contactUseCase) {
        this.contactUseCase = contactUseCase;
    }

    /**
     * Exports a single contact as a vCard {@code .vcf} file.
     *
     * @param id the UUID of the contact to export
     * @return the vCard file as a downloadable attachment, or {@code 404} if not found
     */
    @GetMapping(value = "/{id}/vcard", produces = VCARD_MEDIA_TYPE)
    @Operation(summary = "Export a single contact as vCard")
    public ResponseEntity<String> exportSingle(@PathVariable UUID id) {
        return contactUseCase.findById(id)
                .map(contact -> {
                    var vcard = VCardMapper.toVCard(contact);
                    var vcf = Ezvcard.write(vcard).go();
                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION,
                                    "attachment; filename=\"contact.vcf\"")
                            .contentType(MediaType.parseMediaType(VCARD_MEDIA_TYPE))
                            .body(vcf);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Exports all contacts for an organization as a multi-entry vCard {@code .vcf} file.
     *
     * @param organizationId the UUID of the organization whose contacts to export
     * @return the vCard file containing all contacts as a downloadable attachment
     */
    @GetMapping(value = "/export", produces = VCARD_MEDIA_TYPE)
    @Operation(summary = "Export all contacts for an organization as vCard")
    public ResponseEntity<String> exportAll(@RequestParam UUID organizationId) {
        var contacts = contactUseCase.findByOrganizationId(organizationId);
        var vcards = contacts.stream()
                .map(VCardMapper::toVCard)
                .toList();
        var vcf = Ezvcard.write(vcards).go();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"contacts.vcf\"")
                .contentType(MediaType.parseMediaType(VCARD_MEDIA_TYPE))
                .body(vcf);
    }

    /**
     * Imports contacts from an uploaded vCard {@code .vcf} file.
     *
     * <p>When {@code skipDuplicates} is {@code true}, any vCard entry whose first email
     * already exists among the organization's contacts will be skipped.</p>
     *
     * @param organizationId the UUID of the organization to import contacts into
     * @param file           the uploaded {@code .vcf} file
     * @param skipDuplicates whether to skip entries with matching email addresses
     * @return the list of created contacts
     * @throws IOException if the uploaded file cannot be read or parsed
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import contacts from a vCard file")
    public ResponseEntity<List<Contact>> importVCard(
            @RequestParam UUID organizationId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean skipDuplicates) throws IOException {

        var vcards = Ezvcard.parse(file.getInputStream()).all();
        LOG.info("Parsed {} vCard entries for organization {}", vcards.size(), organizationId);

        Set<String> existingEmails = Set.of();
        if (skipDuplicates) {
            existingEmails = contactUseCase.findByOrganizationId(organizationId).stream()
                    .filter(c -> c.getEmails() != null && !c.getEmails().isEmpty())
                    .flatMap(c -> c.getEmails().stream())
                    .map(String::toLowerCase)
                    .collect(Collectors.toSet());
        }

        var created = new ArrayList<Contact>();
        for (var vc : vcards) {
            var contact = VCardMapper.fromVCard(vc, organizationId);

            if (skipDuplicates && contact.getEmails() != null
                    && !contact.getEmails().isEmpty()) {
                var firstEmail = contact.getEmails().get(0).toLowerCase();
                if (existingEmails.contains(firstEmail)) {
                    LOG.debug("Skipping duplicate contact with email {}", firstEmail);
                    continue;
                }
            }

            created.add(contactUseCase.create(contact));
        }

        LOG.info("Imported {} contacts for organization {}", created.size(), organizationId);
        return ResponseEntity.ok(created);
    }
}
