package com.majordomo.adapter.in.web.steward;

import com.majordomo.adapter.in.web.config.OAuth2UserService;
import com.majordomo.adapter.in.web.config.SecurityConfig;
import com.majordomo.application.identity.CurrentOrganizationResolver;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.identity.User;
import com.majordomo.domain.model.steward.Property;
import com.majordomo.domain.model.steward.PropertyStatus;
import com.majordomo.domain.port.in.ManageAttachmentUseCase;
import com.majordomo.domain.port.in.concierge.ManageContactUseCase;
import com.majordomo.domain.port.in.herald.ManageScheduleUseCase;
import com.majordomo.domain.port.in.steward.ManagePropertyUseCase;
import com.majordomo.domain.port.out.identity.ApiKeyRepository;
import com.majordomo.domain.port.out.identity.MembershipRepository;
import com.majordomo.domain.port.out.identity.UserRepository;
import com.majordomo.domain.port.out.steward.PropertyContactRepository;
import com.majordomo.domain.port.out.steward.PropertyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Slice tests for {@link PropertyPageController#list}: list + filter at {@code /properties}.
 */
@WebMvcTest(PropertyPageController.class)
@Import(SecurityConfig.class)
class PropertyPageListTest {

    @Autowired MockMvc mvc;

    @MockitoBean ManagePropertyUseCase propertyUseCase;
    @MockitoBean ManageScheduleUseCase scheduleUseCase;
    @MockitoBean ManageContactUseCase contactUseCase;
    @MockitoBean ManageAttachmentUseCase attachmentUseCase;
    @MockitoBean PropertyContactRepository propertyContactRepository;
    @MockitoBean PropertyRepository propertyRepository;
    @MockitoBean com.majordomo.domain.port.out.herald.ServiceRecordRepository serviceRecordRepository;
    @MockitoBean CurrentOrganizationResolver currentOrg;
    @MockitoBean com.majordomo.application.identity.OrganizationAccessService organizationAccessService;
    @MockitoBean UserRepository userRepository;
    @MockitoBean MembershipRepository membershipRepository;
    @MockitoBean ApiKeyRepository apiKeyRepository;
    @MockitoBean OAuth2UserService oAuth2UserService;

    private static final UUID ORG_ID = UuidFactory.newId();

    @BeforeEach
    void seedAuth() {
        User user = new User(UuidFactory.newId(), "robsartin", "rob@example.com");
        when(currentOrg.resolve(any(UserDetails.class)))
                .thenReturn(new CurrentOrganizationResolver.Resolved(user, ORG_ID));
    }

    /** Renders properties sorted by name with category, status, and location. */
    @Test
    @WithMockUser
    void listRendersPropertiesSortedByName() throws Exception {
        when(propertyRepository.findByOrganizationId(ORG_ID)).thenReturn(List.of(
                property("Beach House", "vacation", PropertyStatus.ACTIVE, "Cape Cod", null),
                property("Apartment", "rental", PropertyStatus.IN_SERVICE, "Boston", null)));

        MvcResult result = mvc.perform(get("/properties"))
                .andExpect(status().isOk())
                .andExpect(view().name("properties"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("Beach House").contains("Apartment");
        assertThat(body).contains("vacation").contains("rental");
        assertThat(body).contains("ACTIVE").contains("IN_SERVICE");
        // Apartment sorts before Beach House alphabetically.
        int apt = body.indexOf("Apartment");
        int beach = body.indexOf("Beach House");
        assertThat(apt).isPositive().isLessThan(beach);
    }

    /** Archived properties are filtered out. */
    @Test
    @WithMockUser
    void listSkipsArchivedProperties() throws Exception {
        when(propertyRepository.findByOrganizationId(ORG_ID)).thenReturn(List.of(
                property("Active", "rental", PropertyStatus.ACTIVE, "x", null),
                property("Archived old", "rental", PropertyStatus.DISPOSED, "y",
                        Instant.parse("2025-01-01T00:00:00Z"))));

        MvcResult result = mvc.perform(get("/properties"))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("Active").doesNotContain("Archived old");
    }

    /** Free-text query narrows results across name + description. */
    @Test
    @WithMockUser
    void searchFiltersAcrossNameAndDescription() throws Exception {
        Property cabin = property("Cabin", "vacation", PropertyStatus.ACTIVE, "Maine", null);
        cabin.setDescription("Lakeside retreat with HVAC");
        Property apartment = property("Apartment", "rental", PropertyStatus.ACTIVE, "Boston", null);
        apartment.setDescription("City rental");
        when(propertyRepository.findByOrganizationId(ORG_ID)).thenReturn(List.of(cabin, apartment));

        // Match against description.
        MvcResult lakeResult = mvc.perform(get("/properties").param("q", "lakeside"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(lakeResult.getResponse().getContentAsString())
                .contains("Cabin").doesNotContain("Apartment");

        // Match against name.
        MvcResult aptResult = mvc.perform(get("/properties").param("q", "apartment"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(aptResult.getResponse().getContentAsString())
                .contains("Apartment").doesNotContain("Cabin");
    }

    /** Category filter narrows results (case-insensitive exact match). */
    @Test
    @WithMockUser
    void filterByCategoryNarrowsResults() throws Exception {
        when(propertyRepository.findByOrganizationId(ORG_ID)).thenReturn(List.of(
                property("Cabin", "vacation", PropertyStatus.ACTIVE, "Maine", null),
                property("Apartment", "rental", PropertyStatus.ACTIVE, "Boston", null)));

        MvcResult result = mvc.perform(get("/properties").param("category", "rental"))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("Apartment").doesNotContain("Cabin");
    }

    /** Empty state renders when no properties match. */
    @Test
    @WithMockUser
    void emptyStateRendersWhenNoMatches() throws Exception {
        when(propertyRepository.findByOrganizationId(ORG_ID)).thenReturn(List.of());

        mvc.perform(get("/properties"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "No properties match")));
    }

    /** User without an organization redirects home. */
    @Test
    @WithMockUser
    void redirectsHomeWhenNoOrganization() throws Exception {
        User lonely = new User(UuidFactory.newId(), "lonely", "l@example.com");
        when(currentOrg.resolve(any(UserDetails.class)))
                .thenReturn(new CurrentOrganizationResolver.Resolved(lonely, null));

        mvc.perform(get("/properties"))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/"));
    }

    /** Unauthenticated request redirects to login. */
    @Test
    void unauthenticatedRedirectsToLogin() throws Exception {
        mvc.perform(get("/properties"))
                .andExpect(status().is3xxRedirection());
    }

    private static Property property(String name, String category, PropertyStatus status,
                                     String location, Instant archivedAt) {
        Property p = new Property();
        p.setId(UuidFactory.newId());
        p.setOrganizationId(ORG_ID);
        p.setName(name);
        p.setCategory(category);
        p.setStatus(status);
        p.setLocation(location);
        p.setArchivedAt(archivedAt);
        return p;
    }
}
