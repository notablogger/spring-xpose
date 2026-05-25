package io.github.notablogger.springxpose.config;

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

    /**
     * OpenAPI / Swagger UI metadata.
     */
    private Api api = new Api();

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public String getRestBasePath() { return restBasePath; }
    public void setRestBasePath(String restBasePath) { this.restBasePath = restBasePath; }

    public Api getApi() { return api; }
    public void setApi(Api api) { this.api = api; }

    public static class Api {
        /** Display title shown in Swagger UI. */
        private String title = "spring-xpose API";

        /** Short description shown in Swagger UI. */
        private String description = "Auto-generated REST API powered by spring-xpose";

        /** API version string shown in Swagger UI. */
        private String version = "1.0.0";

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
    }
}
