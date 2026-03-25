package com.majordomo.adapter.in.web.config;

import org.slf4j.MDC;

/**
 * Provides static access to the current request's correlation ID.
 */
public final class CorrelationIdContext {

    private CorrelationIdContext() {
    }

    /**
     * Returns the correlation ID for the current request.
     *
     * @return the correlation ID, or null if not in a request context
     */
    public static String current() {
        return MDC.get(CorrelationIdFilter.MDC_KEY);
    }
}
