package com.majordomo.adapter.in.web.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

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
    private final OrgContextArgumentResolver orgContextArgumentResolver;

    /**
     * Constructs a {@code WebConfig} with the interceptors and argument
     * resolvers to register.
     *
     * @param apiVersionInterceptor      the interceptor that echoes the API version header
     * @param orgContextArgumentResolver resolves {@link OrgContext} on web handlers
     *                                   (no-ops when the application layer isn't loaded)
     */
    public WebConfig(ApiVersionInterceptor apiVersionInterceptor,
                     OrgContextArgumentResolver orgContextArgumentResolver) {
        this.apiVersionInterceptor = apiVersionInterceptor;
        this.orgContextArgumentResolver = orgContextArgumentResolver;
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

    /**
     * Registers the {@link OrgContextArgumentResolver} so any web handler can
     * declare an {@link OrgContext} parameter and have it auto-injected.
     *
     * @param resolvers the argument-resolver list provided by Spring MVC
     */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(orgContextArgumentResolver);
    }
}
