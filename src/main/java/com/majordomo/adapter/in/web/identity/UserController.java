package com.majordomo.adapter.in.web.identity;

import com.majordomo.application.identity.OrganizationAccessService;
import com.majordomo.domain.model.identity.User;
import com.majordomo.domain.port.in.identity.ManageUserUseCase;
import com.majordomo.domain.port.out.identity.UserRepository;

import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

/**
 * REST controller for user management within organizations.
 */
@RestController
@RequestMapping("/api/organizations")
@Tag(name = "Identity", description = "User management")
public class UserController {

    private final ManageUserUseCase manageUserUseCase;
    private final UserRepository userRepository;
    private final OrganizationAccessService organizationAccessService;

    /**
     * Constructs the controller with required dependencies.
     *
     * @param manageUserUseCase         the inbound port for user management
     * @param userRepository            the port for looking up the authenticated caller
     * @param organizationAccessService the service for verifying organization membership
     */
    public UserController(ManageUserUseCase manageUserUseCase,
                          UserRepository userRepository,
                          OrganizationAccessService organizationAccessService) {
        this.manageUserUseCase = manageUserUseCase;
        this.userRepository = userRepository;
        this.organizationAccessService = organizationAccessService;
    }

    /**
     * Creates a new user and adds them to the specified organization.
     * Only OWNER or ADMIN of the organization can perform this action.
     *
     * @param orgId     the organization UUID
     * @param request   the user creation request body
     * @param principal the authenticated user's details
     * @return {@code 201 Created} with the new user, or {@code 403} if not authorized
     */
    @PostMapping("/{orgId}/users")
    public ResponseEntity<User> createUser(
            @PathVariable UUID orgId,
            @Valid @RequestBody CreateUserRequest request,
            @AuthenticationPrincipal UserDetails principal) {
        organizationAccessService.verifyAccess(orgId);
        var caller = userRepository.findByUsername(principal.getUsername())
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated user not found"));

        var user = manageUserUseCase.createUser(
                request.username(), request.email(), request.password(),
                orgId, caller.getId());

        return ResponseEntity
                .created(URI.create("/api/organizations/" + orgId
                        + "/users/" + user.getId()))
                .body(user);
    }
}
