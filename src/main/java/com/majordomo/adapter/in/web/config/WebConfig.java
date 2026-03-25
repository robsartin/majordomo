package com.majordomo.adapter.in.web.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC configuration for the Majordomo web layer.
 *
 * <p>Registers application-wide interceptors. Currently applies
 * {@link ApiVersionInterceptor} to all {@code /api/**} routes so that every API
 * response carries the {@code X-API-Version} header.</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final ApiVersionInterceptor apiVersionInterceptor;

    /**
     * Constructs a {@code WebConfig} with the API version interceptor to register.
     *
     * @param apiVersionInterceptor the interceptor that echoes the API version header
     */
    public WebConfig(ApiVersionInterceptor apiVersionInterceptor) {
        this.apiVersionInterceptor = apiVersionInterceptor;
    }

    /**
     * Registers the {@link ApiVersionInterceptor} for all paths matching {@code /api/**}.
     *
     * @param registry the interceptor registry provided by Spring MVC
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(apiVersionInterceptor)
                .addPathPatterns("/api/**");
    }
}
