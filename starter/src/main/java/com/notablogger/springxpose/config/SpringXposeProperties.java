package com.notablogger.springxpose.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spring-xpose")
public class SpringXposeProperties {

    /**
     * API mode: REST or GRAPHQL. Defaults to REST.
     */
    private String mode = "REST";

    /**
     * Base path prefix for REST controllers.
     */
    private String restBasePath = "/api";

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public String getRestBasePath() { return restBasePath; }
    public void setRestBasePath(String restBasePath) { this.restBasePath = restBasePath; }
}

