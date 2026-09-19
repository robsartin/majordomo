package com.majordomo.adapter.in.web.librarian;

import com.majordomo.adapter.in.web.config.OAuth2UserService;
import com.majordomo.adapter.in.web.config.SecurityConfig;
import com.majordomo.application.identity.CurrentOrganizationResolver;
import com.majordomo.domain.model.Page;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.identity.Membership;
import com.majordomo.domain.model.identity.User;
import com.majordomo.domain.model.librarian.Book;
import com.majordomo.domain.model.librarian.BookFilter;
import com.majordomo.domain.model.librarian.BookStatus;
import com.majordomo.domain.model.librarian.Confidence;
import com.majordomo.domain.model.librarian.EnrichmentCandidate;
import com.majordomo.domain.port.in.librarian.ListBooksUseCase;
import com.majordomo.domain.port.in.librarian.ReviewEnrichmentUseCase;
import com.majordomo.domain.port.out.identity.ApiKeyRepository;
import com.majordomo.domain.port.out.librarian.BookRepository;
import com.majordomo.domain.port.out.librarian.EnrichmentCandidateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(LibrarianPageController.class)
@Import(SecurityConfig.class)
class LibrarianPageControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean ListBooksUseCase listBooks;
    @MockitoBean ReviewEnrichmentUseCase review;
    @MockitoBean BookRepository books;
    @MockitoBean EnrichmentCandidateRepository candidates;
    @MockitoBean CurrentOrganizationResolver currentOrg;
    @MockitoBean ApiKeyRepository apiKeyRepository;
    @MockitoBean OAuth2UserService oAuth2UserService;

    private static final UUID ORG_ID = UuidFactory.newId();
    private static final UUID OTHER_ORG = UuidFactory.newId();

    @BeforeEach
    void resolveOrg() {
        var user = new User(UuidFactory.newId(), "robsartin", "rob@example.com");
        var membership = new Membership();
        membership.setUserId(user.getId());
        membership.setOrganizationId(ORG_ID);
        when(currentOrg.resolve(any(org.springframework.security.core.userdetails.UserDetails.class)))
                .thenReturn(new CurrentOrganizationResolver.Resolved(user, ORG_ID));
        // The catalog header shows how many matches await review, so every
        // /librarian render touches the queue.
        when(review.pending(any(UUID.class), any(), anyInt()))
                .thenReturn(new Page<>(List.of(), null, false));
    }

    private Book book(UUID orgId, String title) {
        var b = new Book();
        b.setId(UuidFactory.newId());
        b.setOrganizationId(orgId);
        b.setTitle(title);
        b.setAuthors(List.of("Martin Fowler"));
        b.setStatus(BookStatus.OWNED);
        b.setConfidence(Confidence.HIGH);
        b.setRating(5);
        return b;
    }

    private EnrichmentCandidate candidate(UUID bookId) {
        return new EnrichmentCandidate(UuidFactory.newId(), bookId, "OPEN_LIBRARY", "/works/OL1W",
                0.72, Confidence.MEDIUM, Map.of("publisher", "Addison-Wesley"),
                Instant.now(), null, null);
    }

    @Test
    @WithMockUser(username = "robsartin")
    void librarianPage_rendersTheCatalog() throws Exception {
        when(listBooks.list(eq(ORG_ID), any(BookFilter.class), any(), anyInt()))
                .thenReturn(new Page<>(List.of(book(ORG_ID, "Refactoring")), null, false));

        mvc.perform(get("/librarian"))
                .andExpect(status().isOk())
                .andExpect(view().name("librarian"))
                .andExpect(model().attributeExists("books"))
                .andExpect(content().string(containsString("Refactoring")));
    }

    @Test
    @WithMockUser(username = "robsartin")
    void librarianPage_passesFilterCriteriaThrough() throws Exception {
        when(listBooks.list(eq(ORG_ID), any(BookFilter.class), any(), anyInt()))
                .thenReturn(new Page<>(List.of(), null, false));

        mvc.perform(get("/librarian").param("confidence", "LOW").param("q", "networks"))
                .andExpect(status().isOk());

        verify(listBooks).list(eq(ORG_ID),
                eq(new BookFilter(null, Confidence.LOW, "networks")), any(), anyInt());
    }

    @Test
    @WithMockUser(username = "robsartin")
    void bookDetail_rendersTheBook() throws Exception {
        Book b = book(ORG_ID, "Clean Architecture");
        when(books.findById(b.getId())).thenReturn(Optional.of(b));
        when(candidates.findByBookId(b.getId())).thenReturn(List.of());

        mvc.perform(get("/librarian/books/" + b.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("librarian-book"))
                .andExpect(content().string(containsString("Clean Architecture")));
    }

    @Test
    @WithMockUser(username = "robsartin")
    void bookDetail_is404ForABookInAnotherOrganization() throws Exception {
        Book theirs = book(OTHER_ORG, "Not Yours");
        when(books.findById(theirs.getId())).thenReturn(Optional.of(theirs));

        mvc.perform(get("/librarian/books/" + theirs.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "robsartin")
    void reviewQueue_rendersPendingCandidates() throws Exception {
        Book b = book(ORG_ID, "Refactoring");
        when(review.pending(eq(ORG_ID), any(), anyInt()))
                .thenReturn(new Page<>(List.of(candidate(b.getId())), null, false));
        when(books.findById(b.getId())).thenReturn(Optional.of(b));

        mvc.perform(get("/librarian/review"))
                .andExpect(status().isOk())
                .andExpect(view().name("librarian-review"))
                .andExpect(model().attributeExists("rows"));
    }

    @Test
    @WithMockUser(username = "robsartin")
    void accept_delegatesAndRedirectsBackToTheQueue() throws Exception {
        UUID candidateId = UuidFactory.newId();

        mvc.perform(post("/librarian/review/" + candidateId + "/accept").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/librarian/review"));

        verify(review).accept(candidateId, ORG_ID);
    }

    @Test
    @WithMockUser(username = "robsartin")
    void reject_delegatesAndRedirectsBackToTheQueue() throws Exception {
        UUID candidateId = UuidFactory.newId();

        mvc.perform(post("/librarian/review/" + candidateId + "/reject").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/librarian/review"));

        verify(review).reject(candidateId, ORG_ID);
    }

    @Test
    void accept_withoutCsrfIsRejectedAndChangesNothing() throws Exception {
        UUID candidateId = UuidFactory.newId();

        mvc.perform(post("/librarian/review/" + candidateId + "/accept"))
                .andExpect(status().is4xxClientError());

        verify(review, never()).accept(any(), any());
    }
}
