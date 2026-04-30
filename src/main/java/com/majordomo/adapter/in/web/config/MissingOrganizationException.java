package com.majordomo.adapter.in.web.config;

/**
 * Thrown by {@link OrgContextArgumentResolver} when the authenticated user has
 * no organization membership. Caught by {@code GlobalExceptionHandler} and
 * mapped to a redirect home — handlers don't need to special-case this.
 */
public class MissingOrganizationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs the exception with a constant message; the redirect handler
     * doesn't surface the message but logs it for debugging.
     */
    public MissingOrganizationException() {
        super("Authenticated user has no organization membership");
    }
}
