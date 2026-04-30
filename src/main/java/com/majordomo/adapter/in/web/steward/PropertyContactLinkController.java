package com.majordomo.adapter.in.web.steward;

import com.majordomo.adapter.in.web.FormBindingHelper;
import com.majordomo.adapter.in.web.config.OrgContext;
import com.majordomo.application.identity.OrganizationAccessService;
import com.majordomo.domain.model.EntityNotFoundException;
import com.majordomo.domain.model.EntityType;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.concierge.Contact;
import com.majordomo.domain.model.steward.Property;
import com.majordomo.domain.model.steward.PropertyContact;
import com.majordomo.domain.port.in.steward.ManagePropertyUseCase;
import com.majordomo.domain.port.out.concierge.ContactRepository;
import com.majordomo.domain.port.out.steward.PropertyContactRepository;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;
import java.util.UUID;

/**
 * Web routes for linking and unlinking contacts on a property's detail page.
 * Sibling to {@link PropertyPageController}, kept separate to keep that class
 * under the file-length budget.
 */
@Controller
@RequestMapping("/properties")
public class PropertyContactLinkController {

    private final ManagePropertyUseCase propertyUseCase;
    private final ContactRepository contactRepository;
    private final PropertyContactRepository propertyContactRepository;
    private final OrganizationAccessService organizationAccessService;

    /**
     * Constructs the property↔contact link controller.
     *
     * @param propertyUseCase           inbound port for property lookups
     * @param contactRepository         outbound port for contact reads
     * @param propertyContactRepository outbound port for property–contact rows
     * @param organizationAccessService verifies caller has access to a given organization
     */
    public PropertyContactLinkController(ManagePropertyUseCase propertyUseCase,
                                         ContactRepository contactRepository,
                                         PropertyContactRepository propertyContactRepository,
                                         OrganizationAccessService organizationAccessService) {
        this.propertyUseCase = propertyUseCase;
        this.contactRepository = contactRepository;
        this.propertyContactRepository = propertyContactRepository;
        this.organizationAccessService = organizationAccessService;
    }

    /**
     * Links a contact to a property.
     *
     * @param id         property UUID
     * @param contactId  the contact to link
     * @param role       role classification (defaults to OTHER if blank/unknown)
     * @param notes      optional free-form notes
     * @param orgContext authenticated user + organization (presence ensures
     *                   the caller is authenticated and has an org)
     * @return redirect to the property detail page
     */
    @PostMapping("/{id}/contacts")
    public String link(@PathVariable UUID id,
                       @RequestParam UUID contactId,
                       @RequestParam(required = false) String role,
                       @RequestParam(required = false) String notes,
                       OrgContext orgContext) {
        Property property = propertyUseCase.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(EntityType.PROPERTY.name(), id));
        organizationAccessService.verifyAccess(property.getOrganizationId());
        Contact contact = contactRepository.findById(contactId)
                .orElseThrow(() -> new EntityNotFoundException(EntityType.CONTACT.name(), contactId));
        organizationAccessService.verifyAccess(contact.getOrganizationId());

        PropertyContact link = new PropertyContact();
        link.setId(UuidFactory.newId());
        link.setPropertyId(id);
        link.setContactId(contactId);
        link.setRole(FormBindingHelper.parseRole(role));
        link.setNotes(FormBindingHelper.blankToNull(notes));
        propertyContactRepository.save(link);
        return "redirect:/properties/" + id;
    }

    /**
     * Unlinks (soft-deletes) a contact association.
     *
     * @param id         property UUID
     * @param linkId     PropertyContact row UUID
     * @param orgContext authenticated user + organization
     * @return redirect to the property detail page
     */
    @PostMapping("/{id}/contacts/{linkId}/unlink")
    public String unlink(@PathVariable UUID id,
                         @PathVariable UUID linkId,
                         OrgContext orgContext) {
        Property property = propertyUseCase.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(EntityType.PROPERTY.name(), id));
        organizationAccessService.verifyAccess(property.getOrganizationId());
        propertyContactRepository.findById(linkId).ifPresent(link -> {
            if (link.getPropertyId().equals(id)) {
                link.setArchivedAt(Instant.now());
                propertyContactRepository.save(link);
            }
        });
        return "redirect:/properties/" + id;
    }

}
