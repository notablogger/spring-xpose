package io.github.notablogger.springxpose.config;

import io.github.notablogger.springxpose.exception.SpringXposeExceptionHandler;
import io.github.notablogger.springxpose.serializer.RelationAwareSerializer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(SpringXposeProperties.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SpringXposeAutoConfiguration {

    /**
     * Exposes the RelationAwareSerializer as a bean so generated controllers
     * can inject and invoke it selectively per field — no global Jackson override.
     */
    @Bean
    @ConditionalOnMissingBean
    public RelationAwareSerializer relationAwareSerializer() {
        return new RelationAwareSerializer();
    }

    @Bean
    @ConditionalOnMissingBean
    public SpringXposeExceptionHandler springXposeExceptionHandler() {
        return new SpringXposeExceptionHandler();
    }
}
