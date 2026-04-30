package com.majordomo.adapter.in.web.config;

import com.majordomo.application.identity.CurrentOrganizationResolver;

import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Optional;

/**
 * Spring MVC argument resolver that produces an {@link OrgContext} for any
 * handler parameter of that type. Resolves the authenticated principal to a
 * user + first-org pair via {@link CurrentOrganizationResolver}; throws
 * {@link MissingOrganizationException} when the user has no membership so the
 * caller doesn't need to null-check on every handler.
 */
@Component
public class OrgContextArgumentResolver implements HandlerMethodArgumentResolver {

    private final Optional<CurrentOrganizationResolver> currentOrg;

    /**
     * Constructs the resolver. {@code currentOrg} is wrapped in {@link Optional}
     * so REST slice tests that don't load the application layer can still
     * autowire the resolver bean — {@link #supportsParameter} returns
     * {@code false} when the application resolver is absent, so Spring falls
     * back to its default argument-handling and never invokes {@code resolveArgument}.
     *
     * @param currentOrg application-layer resolver, present iff the
     *                   application context registers it
     */
    public OrgContextArgumentResolver(Optional<CurrentOrganizationResolver> currentOrg) {
        this.currentOrg = currentOrg;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return currentOrg.isPresent()
                && OrgContext.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Object principal = auth != null ? auth.getPrincipal() : null;
        if (!(principal instanceof UserDetails userDetails)) {
            throw new MissingOrganizationException();
        }
        var resolved = currentOrg.orElseThrow().resolve(userDetails);
        if (resolved.user() == null || resolved.organizationId() == null) {
            throw new MissingOrganizationException();
        }
        return new OrgContext(resolved.user(), resolved.organizationId());
    }
}
