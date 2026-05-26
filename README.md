# spring-xpose

[![Maven Central](https://img.shields.io/maven-central/v/io.github.notablogger/spring-xpose-starter?label=Maven%20Central)](https://central.sonatype.com/search?q=io.github.notablogger)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Build](https://github.com/notablogger/spring-xpose/actions/workflows/ci.yml/badge.svg)](https://github.com/notablogger/spring-xpose/actions/workflows/ci.yml)

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
    implementation 'io.github.notablogger:spring-xpose-starter:0.1.3'
    annotationProcessor 'io.github.notablogger:spring-xpose-processor:0.1.3'
    compileOnly 'io.github.notablogger:spring-xpose-annotations:0.1.3'
    annotationProcessor 'jakarta.persistence:jakarta.persistence-api:3.1.0'
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

## `@ExposeEntity` — Quick Reference

```java
@Entity
@ExposeEntity(
    path          = "orders",
    expose        = {Operation.FIND_ALL, Operation.FIND_BY_ID, Operation.CREATE, Operation.UPDATE, Operation.DELETE},
    authType      = AuthType.BASIC,
    readRoles     = {"CUSTOMER", "ADMIN"},
    writeRoles    = {"ADMIN"},
    ignoredFields = {"internalNote"},
    customMapper  = MyOrderMapper.class
)
public class Order { ... }
```

| Attribute | Default | Description |
|---|---|---|
| `path` | entity name pluralised | URL segment — `"products"` → `/api/products` |
| `expose` | all five operations | Which HTTP operations to generate |
| `authType` | `NONE` | `NONE`, `BASIC`, or `OAUTH2` |
| `readRoles` / `writeRoles` | `{}` | Role-based access split |
| `ignoredFields` | `{}` | Fields excluded from both response and request DTOs |
| `customMapper` | `void.class` | Bring your own Spring bean mapper |

→ Full attribute reference, operation table, relation modes, request DTO rules, and custom mapper guide: **[`docs/tech/annotation-reference.md`](docs/tech/annotation-reference.md)**

---

## Swagger UI (Optional)

Add springdoc to your dependencies:

```groovy
implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0'
```

All generated controllers are annotated with `@Tag`, `@Operation`, and `@ApiResponse`. Secured endpoints automatically show a 🔒 lock icon in Swagger UI.

→ Full configuration properties: **[`docs/tech/configuration.md`](docs/tech/configuration.md)**

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
| [`annotation-reference.md`](docs/tech/annotation-reference.md) | Full `@ExposeEntity` attribute reference, all operations, auth types, relation modes, request DTO rules, custom mapper guide |
| [`configuration.md`](docs/tech/configuration.md) | All `spring-xpose.*` properties, Swagger UI setup |
| [`architecture.md`](docs/tech/architecture.md) | Module layout, compile-time data-flow diagram, full Mermaid class diagram, runtime request lifecycle, security filter chain design, key design decisions |
| [`generator-guide.md`](docs/tech/generator-guide.md) | How the APT pipeline works, per-generator responsibilities, step-by-step guide for adding a new generator or `@ExposeEntity` attribute, testing patterns |
| [`generated-artifacts.md`](docs/tech/generated-artifacts.md) | Per-file reference for all six generated artifacts with field inclusion rules and error response table |
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
