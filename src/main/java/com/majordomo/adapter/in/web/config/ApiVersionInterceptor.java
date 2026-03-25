package com.majordomo.adapter.in.web.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Spring MVC interceptor that propagates the {@code X-API-Version} header on every response.
 *
 * <p>If the incoming request does not include the header, the interceptor defaults to version
 * {@value #DEFAULT_VERSION}. This ensures clients can always rely on the response header to
 * identify which API version handled their request.</p>
 */
@Component
public class ApiVersionInterceptor implements HandlerInterceptor {

    private static final String DEFAULT_VERSION = "1";

    /**
     * Reads the {@code X-API-Version} request header and echoes it (or the default) back on
     * the response before the handler is invoked.
     *
     * @param request  the current HTTP request
     * @param response the current HTTP response
     * @param handler  the chosen handler to execute
     * @return {@code true} to continue processing the handler chain
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String version = request.getHeader(OpenApiConfig.API_VERSION_HEADER);
        if (version == null) {
            version = DEFAULT_VERSION;
        }
        response.setHeader(OpenApiConfig.API_VERSION_HEADER, version);
        return true;
    }
}
