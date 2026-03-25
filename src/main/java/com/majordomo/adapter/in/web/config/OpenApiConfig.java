package com.majordomo.adapter.in.web.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.parameters.Parameter;

import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String API_VERSION_HEADER = "X-API-Version";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Majordomo API")
                        .description("Personal information and property management system")
                        .version("1"));
    }

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

    @Bean
    public GroupedOpenApi conciergeApi() {
        return GroupedOpenApi.builder()
                .group("concierge")
                .displayName("The Concierge (Contacts)")
                .pathsToMatch("/api/contacts/**")
                .build();
    }

    @Bean
    public GroupedOpenApi stewardApi() {
        return GroupedOpenApi.builder()
                .group("steward")
                .displayName("The Steward (Property)")
                .pathsToMatch("/api/properties/**")
                .build();
    }

    @Bean
    public GroupedOpenApi heraldApi() {
        return GroupedOpenApi.builder()
                .group("herald")
                .displayName("The Herald (Scheduling)")
                .pathsToMatch("/api/schedules/**")
                .build();
    }
}
