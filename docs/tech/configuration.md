# Configuration Reference — spring-xpose

All properties live under the `spring-xpose` prefix in `application.yml`.

```yaml
spring-xpose:
  mode: REST                        # REST or GRAPHQL (default: REST)
  rest-base-path: /api              # prefix for all generated controllers (default: /api)
  api:
    title: "My API"                 # Swagger UI title
    description: "My description"   # Swagger UI description
    version: "1.0.0"               # Swagger UI version
```

---

## Swagger UI (Optional)

Add springdoc to your dependencies:

```groovy
implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0'
```

All generated controllers are annotated with `@Tag`, `@Operation`, and `@ApiResponse`.  
Secured endpoints automatically show a 🔒 lock icon in Swagger UI.

Configure the API title, description, and version in `application.yml`:

```yaml
spring-xpose:
  api:
    title: "My App API"
    description: "Auto-generated CRUD API"
    version: "1.0.0"
```

