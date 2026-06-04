# Comparison: Spring Data REST vs Spring Xpose

This page compares the features and capabilities offered by **Spring Data REST** and **Spring Xpose** based on their functionality and purposes.

---

## Feature Comparison Table

| Feature                 | Details                                    | Supported by Spring Xpose | Supported by Spring Data REST |
|-------------------------|--------------------------------------------|---------------------------|-----------------------------|
| Boilerplate Reduction   | Reduces repetitive coding for CRUD operations by auto-generating code. | Yes                       | Partial (only basic endpoints generated) |
| API Generation          | Automatically generates REST endpoints with OpenAPI documentation.        | Yes                       | Yes (basic HAL-compliant APIs) |
| Query Derivation        | Automatically generates queries from method names.                      | Yes                       | Yes |
| Security Integration    | Automatically generates security filter chains for each entity endpoint. | Yes                       | No (manual configuration required) |
| Relationship Handling   | Manages nested and relation fields intelligently in APIs.                | Yes                       | Partial (HAL navigation required) |
| Debuggability           | Generated code is readable and maintainable Java files.                 | Yes                       | N/A (runtime-generated proxies only) |
| Projections and DTOs    | Automatically creates DTOs and mapping layers.                          | Yes                       | No (manual projections required) |
| OpenAPI Documentation   | Automatically generates OpenAPI documentation for endpoints.             | Yes                       | No (requires manual Swagger/OpenAPI setup) |
| Flexibility and Customization | Allows fine-tuning features through annotations.                     | Yes                       | No (limited customization options) |

---

## Details
### Spring Xpose
Spring Xpose is a productivity framework built on top of **Spring Data**, designed to greatly reduce repetitive coding by automatically generating CRUD APIs, mappings, and security configurations directly at compile-time. It also provides:

- **Annotation-based API generation**: Annotate entities with `@ExposeEntity` to generate a complete set of REST endpoints.
- **Security and OpenAPI integration**: Explores robust capabilities to secure APIs and document them seamlessly.
- **Readable Code**: All generated code is maintainable and fully debuggable.

### Spring Data REST
Spring Data REST is a project that exposes repositories as REST APIs automatically. It shines in:

- **Rapid API Exposure**: Automatically exposes repositories as HAL-compliant RESTful APIs.
- **Relationship Navigation**: Navigates entity relationships through HATEOAS links.
- **Event Hooks**: Allows customization using event hooks like `@RepositoryEventHandler`.

However, it requires manual setup for security, OpenAPI documentation, and DTO handling.

---

This is a basic comparison and can be expanded with further integration details.