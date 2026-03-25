package com.majordomo.adapter.in.web.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class ApiVersionInterceptor implements HandlerInterceptor {

    private static final String DEFAULT_VERSION = "1";

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
