package com.majordomo.adapter.in.web.concierge;

import com.majordomo.application.identity.CurrentOrganizationResolver;
import com.majordomo.application.identity.OrganizationAccessService;
import com.majordomo.domain.model.EntityNotFoundException;
import com.majordomo.domain.model.EntityType;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.concierge.Contact;
import com.majordomo.domain.model.concierge.ContactRole;
import com.majordomo.domain.model.steward.Property;
import com.majordomo.domain.model.steward.PropertyContact;
import com.majordomo.domain.port.in.steward.ManagePropertyUseCase;
import com.majordomo.domain.port.out.concierge.ContactRepository;
import com.majordomo.domain.port.out.steward.PropertyContactRepository;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Web routes for linking and unlinking properties on a contact's detail page.
 * Mirrors {@link com.majordomo.adapter.in.web.steward.PropertyContactLinkController}
 * for the contact-side detail page.
 */
@Controller
@RequestMapping("/contacts")
public class ContactPropertyLinkController {

    private final ContactRepository contactRepository;
    private final ManagePropertyUseCase propertyUseCase;
    private final PropertyContactRepository propertyContactRepository;
    private final CurrentOrganizationResolver currentOrg;
    private final OrganizationAccessService organizationAccessService;

    /**
     * Constructs the contact↔property link controller.
     *
     * @param contactRepository         outbound port for contact reads
     * @param propertyUseCase           inbound port for property lookups
     * @param propertyContactRepository outbound port for property–contact rows
     * @param currentOrg                resolves the authenticated user's organization
     * @param organizationAccessService verifies caller has access to a given organization
     */
    public ContactPropertyLinkController(ContactRepository contactRepository,
                                         ManagePropertyUseCase propertyUseCase,
                                         PropertyContactRepository propertyContactRepository,
                                         CurrentOrganizationResolver currentOrg,
                                         OrganizationAccessService organizationAccessService) {
        this.contactRepository = contactRepository;
        this.propertyUseCase = propertyUseCase;
        this.propertyContactRepository = propertyContactRepository;
        this.currentOrg = currentOrg;
        this.organizationAccessService = organizationAccessService;
    }

    /**
     * Links a property to a contact.
     *
     * @param id         contact UUID
     * @param propertyId property to link
     * @param role       role classification (defaults to OTHER if blank/unknown)
     * @param notes      optional free-form notes
     * @param principal  authenticated user
     * @return redirect to the contact detail page
     */
    @PostMapping("/{id}/properties")
    public String link(@PathVariable UUID id,
                       @RequestParam UUID propertyId,
                       @RequestParam(required = false) String role,
                       @RequestParam(required = false) String notes,
                       @AuthenticationPrincipal UserDetails principal) {
        var ctx = currentOrg.resolve(principal);
        if (ctx.organizationId() == null) {
            return "redirect:/";
        }
        Contact contact = contactRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(EntityType.CONTACT.name(), id));
        organizationAccessService.verifyAccess(contact.getOrganizationId());
        Property property = propertyUseCase.findById(propertyId)
                .orElseThrow(() -> new EntityNotFoundException(EntityType.PROPERTY.name(), propertyId));
        organizationAccessService.verifyAccess(property.getOrganizationId());

        PropertyContact link = new PropertyContact();
        link.setId(UuidFactory.newId());
        link.setPropertyId(propertyId);
        link.setContactId(id);
        link.setRole(parseRole(role));
        link.setNotes(blankToNull(notes));
        propertyContactRepository.save(link);
        return "redirect:/contacts/" + id;
    }

    /**
     * Unlinks (soft-deletes) a property association from this contact.
     *
     * @param id        contact UUID
     * @param linkId    PropertyContact row UUID
     * @param principal authenticated user
     * @return redirect to the contact detail page
     */
    @PostMapping("/{id}/properties/{linkId}/unlink")
    public String unlink(@PathVariable UUID id,
                         @PathVariable UUID linkId,
                         @AuthenticationPrincipal UserDetails principal) {
        var ctx = currentOrg.resolve(principal);
        if (ctx.organizationId() == null) {
            return "redirect:/";
        }
        Contact contact = contactRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(EntityType.CONTACT.name(), id));
        organizationAccessService.verifyAccess(contact.getOrganizationId());
        propertyContactRepository.findById(linkId).ifPresent(link -> {
            if (link.getContactId().equals(id)) {
                link.setArchivedAt(Instant.now());
                propertyContactRepository.save(link);
            }
        });
        return "redirect:/contacts/" + id;
    }

    private static ContactRole parseRole(String role) {
        if (role == null || role.isBlank()) {
            return ContactRole.OTHER;
        }
        try {
            return ContactRole.valueOf(role.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return ContactRole.OTHER;
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
