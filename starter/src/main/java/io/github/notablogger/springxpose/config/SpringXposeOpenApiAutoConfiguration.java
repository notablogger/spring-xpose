package io.github.notablogger.springxpose.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for Swagger / OpenAPI documentation.
 * <p>
 * Only activated when {@code springdoc-openapi} is on the classpath
 * (i.e. the consumer has added {@code springdoc-openapi-starter-webmvc-ui}).
 * Provides a sensible default {@link OpenAPI} bean that can be overridden by
 * the application.
 */
@AutoConfiguration
@ConditionalOnClass(OpenAPI.class)
@EnableConfigurationProperties(SpringXposeProperties.class)
public class SpringXposeOpenApiAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OpenAPI springXposeOpenAPI(SpringXposeProperties properties) {
        SpringXposeProperties.Api api = properties.getApi();
        return new OpenAPI()
            .info(new Info()
                .title(api.getTitle())
                .description(api.getDescription())
                .version(api.getVersion()))
            .components(new Components()
                // HTTP Basic scheme — used by entities with authType = BASIC
                .addSecuritySchemes("basicAuth", new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("basic")
                    .description("HTTP Basic authentication"))
                // Bearer JWT scheme — used by entities with authType = OAUTH2
                .addSecuritySchemes("bearerAuth", new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("JWT Bearer token (OAuth2 resource server)")));
    }
}
