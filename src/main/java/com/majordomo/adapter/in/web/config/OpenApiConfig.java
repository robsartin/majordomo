package com.majordomo.adapter.in.web.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.parameters.Parameter;

import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SpringDoc / OpenAPI 3 configuration for the Majordomo API.
 *
 * <p>Defines the top-level API metadata, injects the {@code X-API-Version} header parameter
 * into every operation via an {@link OperationCustomizer}, and groups endpoints by domain
 * (concierge, steward, herald) for organised Swagger UI navigation.</p>
 */
@Configuration
public class OpenApiConfig {

    /** Name of the HTTP header used to communicate the API version. */
    public static final String API_VERSION_HEADER = "X-API-Version";

    /**
     * Produces the root {@link OpenAPI} bean containing global API metadata.
     *
     * @return an {@link OpenAPI} instance with title, description, and current version
     */
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Majordomo API")
                        .description("Personal information and property management system")
                        .version("1"));
    }

    /**
     * Returns an {@link OperationCustomizer} that appends the optional
     * {@value #API_VERSION_HEADER} header parameter to every API operation in the
     * generated OpenAPI document.
     *
     * @return the operation customizer bean
     */
    @Bean
    public OperationCustomizer apiVersionHeaderCustomizer() {
        return (operation, handlerMethod) -> {
            operation.addParametersItem(new Parameter()
                    .in("header")
                    .name(API_VERSION_HEADER)
                    .description("API version (defaults to latest)")
                    .required(false)
                    .schema(new io.swagger.v3.oas.models.media.IntegerSchema()._default(1)));
            return operation;
        };
    }

    /**
     * Groups the Concierge (contacts) endpoints under a dedicated Swagger UI section.
     *
     * @return a {@link GroupedOpenApi} covering {@code /api/contacts/**}
     */
    @Bean
    public GroupedOpenApi conciergeApi() {
        return GroupedOpenApi.builder()
                .group("concierge")
                .displayName("The Concierge (Contacts)")
                .pathsToMatch("/api/contacts/**")
                .build();
    }

    /**
     * Groups the Steward (properties) endpoints under a dedicated Swagger UI section.
     *
     * @return a {@link GroupedOpenApi} covering {@code /api/properties/**}
     */
    @Bean
    public GroupedOpenApi stewardApi() {
        return GroupedOpenApi.builder()
                .group("steward")
                .displayName("The Steward (Property)")
                .pathsToMatch("/api/properties/**")
                .build();
    }

    /**
     * Groups the Herald (schedules) endpoints under a dedicated Swagger UI section.
     *
     * @return a {@link GroupedOpenApi} covering {@code /api/schedules/**}
     */
    @Bean
    public GroupedOpenApi heraldApi() {
        return GroupedOpenApi.builder()
                .group("herald")
                .displayName("The Herald (Scheduling)")
                .pathsToMatch("/api/schedules/**")
                .build();
    }

    /**
     * Groups the Ledger (finance) endpoints under a dedicated Swagger UI section.
     *
     * @return a {@link GroupedOpenApi} covering {@code /api/ledger/**}
     */
    @Bean
    public GroupedOpenApi ledgerApi() {
        return GroupedOpenApi.builder()
                .group("ledger")
                .displayName("The Ledger (Finance)")
                .pathsToMatch("/api/ledger/**")
                .build();
    }
}
