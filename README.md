# spring-xpose

[![Maven Central](https://img.shields.io/maven-central/v/io.github.notablogger/spring-xpose-starter?label=Maven%20Central)](https://central.sonatype.com/search?q=io.github.notablogger)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Build](https://github.com/notablogger/spring-xpose/actions/workflows/ci.yml/badge.svg)](https://github.com/notablogger/spring-xpose/actions/workflows/ci.yml)
[![PR Build](https://github.com/notablogger/spring-xpose/actions/workflows/pr.yml/badge.svg)](https://github.com/notablogger/spring-xpose/actions/workflows/pr.yml)

> **Annotate your JPA entity once. Get a fully working REST API at compile time.**

spring-xpose reads `@ExposeEntity` on your JPA entity at compile time and generates — as real, readable `.java` files — a Spring Data repository, a DTO, a MapStruct mapper, a `@RestController` with full OpenAPI documentation, and a `SecurityFilterChain` scoped to that entity's path. No runtime magic, no reflection, no proxies. Just generated code you can open, read, and debug.

---

## Why spring-xpose?

Writing REST APIs for JPA entities is repetitive work:

1. Write a `@Repository`
2. Write a DTO and a mapper
3. Write a `@RestController` with 5 methods, validation, error handling
4. Wire up `SecurityFilterChain` per resource
5. Add OpenAPI annotations to every method
6. Repeat for every entity

spring-xpose eliminates steps 1–5 entirely.

| | spring-xpose | Spring Data REST | Manual |
|---|---|---|---|
| Compile-time generation | ✅ | ❌ (runtime) | N/A |
| DTO + mapper generated | ✅ | ❌ | Manual |
| Per-entity security | ✅ | ❌ | Manual |
| OpenAPI out of the box | ✅ | Partial | Manual |
| Debuggable generated code | ✅ | ❌ | N/A |
| Field exclusion from API | ✅ (`ignoredFields`) | ❌ | Manual |
| Role-based read/write split | ✅ | ❌ | Manual |

---

## Requirements

- Java 21+
- Spring Boot 3.3.x
- Spring Data JPA
- Gradle or Maven

---

## Installation

spring-xpose follows the same pattern as **Lombok** and **MapStruct** — a few coordinates, one version. The annotation processor runs only at compile time; it never ends up in your application JAR.

### Gradle (Groovy DSL)

```groovy
dependencies {
    // 1. Runtime: autoconfiguration, serializer, security wiring, MapStruct runtime
    implementation 'io.github.notablogger:spring-xpose-starter:0.1.3'

    // 2. Compile-time: generates repository, DTO, mapper, controller, security configurer
    annotationProcessor 'io.github.notablogger:spring-xpose-processor:0.1.3'

    // 3. Compile-time: @ExposeEntity annotation and enums (not in your JAR)
    compileOnly 'io.github.notablogger:spring-xpose-annotations:0.1.3'

    // 4. Required so the processor can read @Id, @ManyToOne etc. at build time
    annotationProcessor 'jakarta.persistence:jakarta.persistence-api:3.1.0'

    // 5. MapStruct processor — compiles the generated *Mapper interfaces
    annotationProcessor 'org.mapstruct:mapstruct-processor:1.5.5.Final'
}
```

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("io.github.notablogger:spring-xpose-starter:0.1.3")
    annotationProcessor("io.github.notablogger:spring-xpose-processor:0.1.3")
    compileOnly("io.github.notablogger:spring-xpose-annotations:0.1.3")
    annotationProcessor("jakarta.persistence:jakarta.persistence-api:3.1.0")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.5.5.Final")
}
```

### Maven

```xml
<dependencies>
    <dependency>
        <groupId>io.github.notablogger</groupId>
        <artifactId>spring-xpose-starter</artifactId>
        <version>0.1.3</version>
    </dependency>
    <dependency>
        <groupId>io.github.notablogger</groupId>
        <artifactId>spring-xpose-annotations</artifactId>
        <version>0.1.3</version>
        <scope>provided</scope>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
                <annotationProcessorPaths>
                    <path>
                        <groupId>io.github.notablogger</groupId>
                        <artifactId>spring-xpose-processor</artifactId>
                        <version>0.1.3</version>
                    </path>
                    <path>
                        <groupId>jakarta.persistence</groupId>
                        <artifactId>jakarta.persistence-api</artifactId>
                        <version>3.1.0</version>
                    </path>
                    <path>
                        <groupId>org.mapstruct</groupId>
                        <artifactId>mapstruct-processor</artifactId>
                        <version>1.5.5.Final</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```

---

## Quick Start

### Step 1 — Add dependencies

Use the snippets above for your build tool.

### Step 2 — Annotate your entity

```java
import io.github.notablogger.springxpose.annotation.*;

@Entity
@ExposeEntity(path = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    private String name;

    @Positive
    private Double price;

    // getters / setters
}
```

### Step 3 — Build

```bash
./gradlew build
```

### Step 4 — Call the API

```bash
curl http://localhost:8080/api/products
# → []

curl -X POST http://localhost:8080/api/products \
  -H "Content-Type: application/json" \
  -d '{"name":"Laptop","price":999.99}'
# → {"id":1,"name":"Laptop","price":999.99}
```

That's it. No controller, repository, DTO, or mapper written by hand.

### What was generated

Under `build/generated/sources/annotationProcessor/java/main/` you will find **six** files per entity:

```
ProductRepository.java         ← extends JpaRepository<Product, Long>
ProductDto.java                ← clean API response shape (id + non-ignored scalars/relations)
ProductRequestDto.java         ← request body for CREATE/UPDATE (no id, always Long <rel>Id)
ProductMapper.java             ← MapStruct interface: toDto(), toDtoList(), toEntity()
ProductController.java         ← @RestController at /api/products, returns ProductDto
ProductSecurityConfigurer.java ← SecurityFilterChain scoped to /api/products/**
```

These are real `.java` files — open them in your IDE, set breakpoints, read them.

---

## `@ExposeEntity` Reference

### Full example

```java
@Entity
@ExposeEntity(
    path          = "orders",
    expose        = {Operation.FIND_ALL, Operation.FIND_BY_ID,
                     Operation.CREATE, Operation.UPDATE, Operation.DELETE},
    relationMode  = RelationMode.IDS_FOR_LIST_OBJECT_FOR_SINGLE,
    authType      = AuthType.BASIC,
    readRoles     = {"CUSTOMER", "ADMIN"},
    writeRoles    = {"ADMIN"},
    ignoredFields = {"internalNote", "auditTrail"},  // hidden from both response AND request DTO
    customMapper  = MyOrderMapper.class              // optional — omit for MapStruct auto-impl
)
public class Order { ... }
```

### Attributes

| Attribute | Type | Default | Description |
|---|---|---|---|
| `path` | `String` | entity name pluralised | URL segment — `"products"` → `/api/products` |
| `expose` | `Operation[]` | all five | Which HTTP operations to generate |
| `relationMode` | `RelationMode` | `IDS_FOR_LIST_OBJECT_FOR_SINGLE` | How related entities appear in the **response** DTO |
| `authType` | `AuthType` | `NONE` | Authentication mechanism |
| `roles` | `String[]` | `{}` | Roles required for all operations |
| `readRoles` | `String[]` | `{}` | Roles for GET requests (overrides `roles`) |
| `writeRoles` | `String[]` | `{}` | Roles for POST/PUT/DELETE (overrides `roles`) |
| `ignoredFields` | `String[]` | `{}` | Fields excluded from **both** the response DTO and the request DTO |
| `customMapper` | `Class<?>` | `void.class` | Optional custom Spring bean to use instead of the MapStruct-generated mapper |

### `expose` — Operations

| Value | HTTP Method | Path | Response |
|---|---|---|---|
| `FIND_ALL` | `GET` | `/api/{path}` | `200 OK` — returns `List<EntityDto>` |
| `FIND_BY_ID` | `GET` | `/api/{path}/{id}` | `200 OK` / `404 Not Found` |
| `CREATE` | `POST` | `/api/{path}` | `201 Created` — `@RequestBody` is `EntityRequestDto` |
| `UPDATE` | `PUT` | `/api/{path}/{id}` | `200 OK` / `404 Not Found` — `@RequestBody` is `EntityRequestDto` |
| `DELETE` | `DELETE` | `/api/{path}/{id}` | `204 No Content` / `404 Not Found` |

### `authType` — Authentication

| Value | Generated security | Swagger UI |
|---|---|---|
| `NONE` | `permitAll()` — fully public | No lock |
| `BASIC` | HTTP Basic authentication | 🔒 username / password |
| `OAUTH2` | JWT Bearer token | 🔒 Bearer token |

### `relationMode` — Relation serialisation in the DTO

Controls how `@ManyToOne` / `@OneToOne` fields are represented in the generated **response** DTO.  
`@OneToMany` / `@ManyToMany` collection fields are **always excluded** from both DTOs to prevent circular references.  
In the **request** DTO, relations are **always** represented as `Long <field>Id` regardless of `relationMode`.

| Mode | List endpoint (`GET /api/products`) | Single endpoint (`GET /api/products/1`) |
|---|---|---|
| `IDS_FOR_LIST_OBJECT_FOR_SINGLE` | `"categoryId": 3` | `"category": {"id":3,"name":"Books"}` |
| `ALWAYS_IDS` | `"categoryId": 3` | `"categoryId": 3` |
| `ALWAYS_OBJECT` | `"category": {"id":3,...}` | `"category": {"id":3,...}` |

### `ignoredFields` — Hiding fields from the API

Fields listed in `ignoredFields` are **excluded from both the response DTO and the request DTO**. The entity retains them for persistence — they are never exposed in API responses and cannot be set via the API.
```java
@ExposeEntity(
    path = "users",
    ignoredFields = {"passwordHash", "internalScore"}
)
public class User {
    @Id private Long id;
    private String email;
    private String passwordHash;   // stored in DB, never returned or accepted by the API
    private String internalScore;  // same
}
```
`UserDto` contains `id` + `email`. `UserRequestDto` contains `email` only (no `id`, no ignored fields).
---
## Request DTO
For entities with `CREATE` or `UPDATE` operations, spring-xpose generates a **`<Entity>RequestDto`** as the `@RequestBody` type. This cleanly separates what clients _send_ from what the API _returns_.
| | `<Entity>Dto` (response) | `<Entity>RequestDto` (request body) |
|---|---|---|
| `id` field | ✅ included | ❌ excluded — ID comes from the path variable |
| `ignoredFields` | ❌ excluded | ❌ excluded |
| `@NotBlank`, `@Positive`, etc. | not copied | ✅ copied from entity fields |
| `@ManyToOne` / `@OneToOne` | depends on `relationMode` | always `Long <field>Id` |
| `@OneToMany` / `@ManyToMany` | ❌ always excluded | ❌ always excluded |
**Example** — `Product` with a `@ManyToOne Category`:
```json
// POST /api/products  ← clients send ProductRequestDto
{ "name": "Laptop", "price": 999.99, "categoryId": 3 }
// GET /api/products/1  → server returns ProductDto (ALWAYS_OBJECT mode)
{ "id": 1, "name": "Laptop", "price": 999.99, "category": { "id": 3, "name": "Electronics" } }
```
The controller resolves each `<field>Id` to the JPA entity via `EntityManager.getReference()` — a proxy load that satisfies the FK constraint without an extra `SELECT`.
The generated mapper interface includes `toEntity(RequestDto)` alongside the existing `toDto()` and `toDtoList()`.
---
## Custom Mapper (Optional)
By default, spring-xpose generates a MapStruct `@Mapper(componentModel = "spring")` implementation automatically. When you need custom mapping logic — field transforms, security-aware hiding, or enrichment from other services — you can supply your own Spring bean.
### How it works
1. spring-xpose **always generates the `<Entity>Mapper` interface** as a contract (methods: `toDto`, `toDtoList`, `toEntity`).
2. When `customMapper` is set, the `@Mapper` annotation is **omitted** so MapStruct skips auto-generation.
3. Spring injects your `@Component` bean wherever the controller declares `<Entity>Mapper mapper`.
### Example
```java
// 1. Implement the generated interface
@Component
public class ProductMapper implements
    io.github.notablogger.springxpose.sample.rest.entity.generated.ProductMapper {
    @Override
    public ProductDto toDto(Product entity) {
        ProductDto dto = new ProductDto();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        // custom: hide price for internal products
        if (!Boolean.TRUE.equals(entity.getInternal())) {
            dto.setPrice(entity.getPrice());
        }
        return dto;
    }
    @Override
    public List<ProductDto> toDtoList(List<Product> entities) {
        return entities.stream().map(this::toDto).toList();
    }
    @Override
    public Product toEntity(ProductRequestDto requestDto) {
        Product p = new Product();
        p.setName(requestDto.getName());
        p.setPrice(requestDto.getPrice());
        return p;
    }
}
// 2. Reference it in the annotation — no other changes needed
@Entity
@ExposeEntity(path = "products", customMapper = ProductMapper.class)
public class Product { ... }
```
> **Note:** Your class must implement the **generated** `<Entity>Mapper` interface (in the `<entity-package>.generated` package). Spring wires it automatically because it is a `@Component` satisfying the `<Entity>Mapper` type required by the generated controller.
The sample project (`spring-xpose-sample-rest`) contains `CustomCategoryMapper` — a working demonstration that upper-cases category names in responses.
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

---

## Configuration Reference

All properties live under the `spring-xpose` prefix.

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

## Modules

| Module | Artifact | Scope | Purpose |
|---|---|---|---|
| `annotations` | `spring-xpose-annotations` | `compileOnly` | `@ExposeEntity` and supporting enums |
| `processor` | `spring-xpose-processor` | `annotationProcessor` | APT — generates DTO, mapper, controller, repository, security configurer |
| `starter` | `spring-xpose-starter` | `implementation` | Spring Boot autoconfiguration, MapStruct runtime, `RelationAwareSerializer` |

---

## Sample Project

→ **[spring-xpose-sample-rest](https://github.com/notablogger/spring-xpose-sample-rest)** — a runnable Spring Boot app demonstrating DTOs, `ignoredFields`, all three auth types, relation modes, validation, and Swagger UI.

---


## Documentation

Technical docs live under [`docs/tech/`](docs/tech/):

| Document | What's in it |
|---|---|
| [`architecture.md`](docs/tech/architecture.md) | Module layout, compile-time data-flow diagram, full Mermaid class diagram, runtime request lifecycle, security filter chain design, key design decisions |
| [`generator-guide.md`](docs/tech/generator-guide.md) | How the APT pipeline works, per-generator responsibilities, step-by-step guide for adding a new generator or `@ExposeEntity` attribute, testing patterns |
| [`generated-artifacts.md`](docs/tech/generated-artifacts.md) | Per-file reference for all six generated artifacts (Repository, Dto, RequestDto, Mapper, Controller, SecurityConfigurer) with field inclusion rules and error response table |
| [`branch-rules.md`](docs/tech/branch-rules.md) | Branch naming convention, commit message format, GitHub branch protection settings, PR merge strategy |

---

## Contributing

Contributions are welcome. Please read [`CONTRIBUTING.md`](CONTRIBUTING.md) before opening a PR — it covers:

- Branch naming and commit convention (`feat/`, `fix/`, `docs/`, etc.)
- Development setup and how to run tests
- How to add a new `@ExposeEntity` attribute or generator
- PR process and what "done" looks like

**Quick links:**
- 🐛 [Report a bug](https://github.com/notablogger/spring-xpose/issues/new?template=bug_report.yml)
- ✨ [Request a feature](https://github.com/notablogger/spring-xpose/issues/new?template=feature_request.yml)
- 💬 [Start a discussion](https://github.com/notablogger/spring-xpose/discussions)

### CI / PR checks

Every PR runs the [`PR Build` workflow](https://github.com/notablogger/spring-xpose/actions/workflows/pr.yml):

1. **Branch name** — must match `type/kebab-case-description`
2. **PR title** — must follow Conventional Commits (`type(scope): description`)
3. **Tests** — `./gradlew :processor:test`
4. **Build check** — all JARs assemble cleanly
5. **Gate** — all four checks must pass before merge is allowed

---

## Building from Source

```bash
git clone https://github.com/notablogger/spring-xpose.git
cd spring-xpose
./gradlew build
```

Run tests:

```bash
./gradlew test
```

---

## Roadmap

- [x] REST — compile-time controller + DTO + mapper generation
- [x] Per-entity security (NONE / BASIC / OAUTH2)
- [x] Relation-aware DTO serialisation
- [x] `ignoredFields` — hide entity fields from the API
- [x] OpenAPI / Swagger UI with auth scheme
- [x] Request DTO — separate request/response shapes with validation annotation propagation
- [x] Custom mapper — plug in your own Spring bean via `customMapper = YourMapper.class`
- [ ] GraphQL — compile-time DataFetcher wiring

---

## License

Apache 2.0 — see [LICENSE](LICENSE)

