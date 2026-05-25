package io.github.springxpose.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
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
        return new OpenAPI()
            .info(new Info()
                .title("spring-xpose API")
                .description("Auto-generated REST API powered by spring-xpose")
                .version("1.0.0"));
    }
}

