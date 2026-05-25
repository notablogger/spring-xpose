# spring-xpose-boot-starter

A Spring Boot auto-configuration library that generates REST (and GraphQL) controllers, security configurations, and relation-aware serializers at **compile time** from a single `@ExposeEntity` annotation.

## Modules

| Module | Artifact | Purpose |
|--------|----------|---------|
| `annotations` | `spring-xpose-annotations` | `@ExposeEntity` and supporting enums |
| `processor` | `spring-xpose-processor` | Compile-time annotation processor (JavaPoet-based code generation) |
| `starter` | `spring-xpose-starter` | Spring Boot auto-configuration, `SerializationContext`, `RelationAwareSerializer` |

## Quick Start

### 1. Add dependencies

```groovy
dependencies {
    implementation 'io.github.spring-xpose:spring-xpose-starter:0.1.0-SNAPSHOT'
    annotationProcessor 'io.github.spring-xpose:spring-xpose-processor:0.1.0-SNAPSHOT'
    compileOnly 'io.github.spring-xpose:spring-xpose-annotations:0.1.0-SNAPSHOT'
}
```

### 2. Annotate your entity

```java
@Entity
@ExposeEntity(
    path = "products",
    expose = {Operation.FIND_ALL, Operation.FIND_BY_ID, Operation.CREATE, Operation.UPDATE, Operation.DELETE},
    relationMode = RelationMode.IDS_FOR_LIST_OBJECT_FOR_SINGLE,
    authType = AuthType.BASIC,
    readRoles = {"USER", "ADMIN"},
    writeRoles = {"ADMIN"}
)
public class Product {
    @Id @GeneratedValue private Long id;
    private String name;
    private Double price;
    // getters/setters...
}
```

### 3. That's it

The processor generates at compile time:
- `ProductController` — `@RestController` at `/api/products` with the selected operations
- `ProductSecurityConfigurer` — `@Configuration` with a `SecurityFilterChain` scoped to `/api/products/**`

## `@ExposeEntity` Reference

| Attribute | Default | Description |
|-----------|---------|-------------|
| `path` | entity name pluralized | REST base path segment |
| `expose` | all five operations | Which CRUD operations to generate |
| `relationMode` | `IDS_FOR_LIST_OBJECT_FOR_SINGLE` | How relations are serialized |
| `authType` | `NONE` | `NONE`, `BASIC`, or `OAUTH2` |
| `roles` | `{}` | Roles for all operations |
| `readRoles` | `{}` | Roles for GET operations (overrides `roles`) |
| `writeRoles` | `{}` | Roles for POST/PUT/DELETE (overrides `roles`) |

## Relation Modes

| Mode | List (`GET /api/products`) | Single (`GET /api/products/1`) |
|------|---------------------------|-------------------------------|
| `IDS_FOR_LIST_OBJECT_FOR_SINGLE` | `"category": 3` | `"category": {"id":3,"name":"Books"}` |
| `ALWAYS_IDS` | `"category": 3` | `"category": 3` |
| `ALWAYS_OBJECT` | full object | full object |

## Building

```bash
./gradlew build
```

Then visit:
- `GET  http://localhost:8080/api/products`
- `POST http://localhost:8080/api/products` with `{"name":"Widget","price":9.99}`
- `GET  http://localhost:8080/api/categories`
- `http://localhost:8080/h2-console` (H2 web console)

## Publishing

Artifacts are published to GitHub Packages on every GitHub Release via the `publish.yml` workflow.

## License

Apache 2.0

