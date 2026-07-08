package com.majordomo.adapter.in.web.herald;

import com.majordomo.adapter.in.web.config.OrgContext;
import com.majordomo.application.herald.CalendarTokenService;
import com.majordomo.domain.model.herald.CalendarToken;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.UUID;

/**
 * Account UI for the calendar subscription feed (#286). Lets the user mint a feed
 * token (its full subscription URL is shown once, since only the hash is stored)
 * and revoke existing ones. Sibling to the public feed at
 * {@link HeraldCalendarController}.
 */
@Controller
@RequestMapping("/account/calendar")
public class AccountCalendarPageController {

    private final CalendarTokenService tokens;

    /**
     * Constructs the controller.
     *
     * @param tokens calendar token service
     */
    public AccountCalendarPageController(CalendarTokenService tokens) {
        this.tokens = tokens;
    }

    /**
     * Lists the user's active feed tokens.
     *
     * @param orgContext authenticated user + organization
     * @param model      Thymeleaf model
     * @return the {@code account-calendar} template
     */
    @GetMapping
    public String list(OrgContext orgContext, Model model) {
        List<CalendarToken> tokenList = tokens.listActive(orgContext.user().getId());
        model.addAttribute("tokens", tokenList);
        model.addAttribute("username", orgContext.username());
        return "account-calendar";
    }

    /**
     * Mints a new feed token and flashes its one-time subscription URL.
     *
     * @param orgContext authenticated user + organization
     * @param redirect   redirect attributes (flash for the one-shot URL)
     * @return redirect back to the list
     */
    @PostMapping
    public String create(OrgContext orgContext, RedirectAttributes redirect) {
        String raw = tokens.issue(orgContext.user().getId(), orgContext.organizationId());
        String url = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/herald/calendar/" + raw + ".ics")
                .toUriString();
        redirect.addFlashAttribute("feedUrl", url);
        return "redirect:/account/calendar";
    }

    /**
     * Revokes a feed token (owner-scoped; foreign tokens are ignored by the service).
     *
     * @param id         the token id
     * @param orgContext authenticated user + organization
     * @return redirect back to the list
     */
    @PostMapping("/{id}/revoke")
    public String revoke(@PathVariable UUID id, OrgContext orgContext) {
        tokens.revoke(id, orgContext.user().getId());
        return "redirect:/account/calendar";
    }
}
