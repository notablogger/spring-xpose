# spring-xpose

[![Maven Central](https://img.shields.io/maven-central/v/io.github.notablogger/spring-xpose-starter?label=Maven%20Central)](https://central.sonatype.com/search?q=io.github.notablogger)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Build](https://github.com/notablogger/spring-xpose/actions/workflows/ci.yml/badge.svg)](https://github.com/notablogger/spring-xpose/actions/workflows/ci.yml)

> **Annotate your JPA entity once. Get a fully working REST API at compile time.**

spring-xpose reads `@ExposeEntity` on your JPA entity at compile time and generates — as real, readable `.java` files — a Spring Data repository, a `@RestController` with full OpenAPI documentation, and a `SecurityFilterChain` scoped to that entity's path. No runtime magic, no reflection, no proxies. Just generated code you can open, read, and debug.

---

## Why spring-xpose?

Writing REST APIs for JPA entities is repetitive work:

1. Write a `@Repository`
2. Write a `@RestController` with 5 methods, validation, error handling
3. Wire up `SecurityFilterChain` per resource
4. Add OpenAPI annotations to every method
5. Repeat for every entity

spring-xpose eliminates steps 1–4 entirely. You keep full control — the generated files land in `build/generated/` and are visible in your IDE. You can always override or extend them.

| | spring-xpose | Spring Data REST | Manual |
|---|---|---|---|
| Compile-time generation | ✅ | ❌ (runtime) | N/A |
| Per-entity security | ✅ | ❌ | Manual |
| OpenAPI out of the box | ✅ | Partial | Manual |
| Debuggable generated code | ✅ | ❌ | N/A |
| Override individual endpoints | ✅ | Limited | ✅ |
| Role-based read/write split | ✅ | ❌ | Manual |

---

## Requirements

- Java 21+
- Spring Boot 3.3.x
- Spring Data JPA
- Gradle or Maven

---

## Installation

spring-xpose follows the same pattern as **Lombok** and **MapStruct** — three coordinates, one version. The annotation processor runs only at compile time; it never ends up in your application JAR.

### Gradle (Groovy DSL)

```groovy
dependencies {
    // 1. Runtime: autoconfiguration, serializer, security wiring
    implementation 'io.github.notablogger:spring-xpose-starter:0.1.3'

    // 2. Compile-time: generates controllers, repositories, security configurers
    annotationProcessor 'io.github.notablogger:spring-xpose-processor:0.1.3'

    // 3. Compile-time: @ExposeEntity annotation and enums (not in your JAR)
    compileOnly 'io.github.notablogger:spring-xpose-annotations:0.1.3'

    // 4. Required so the processor can read @Id, @ManyToOne etc. at build time
    annotationProcessor 'jakarta.persistence:jakarta.persistence-api:3.1.0'
}
```

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("io.github.notablogger:spring-xpose-starter:0.1.3")
    annotationProcessor("io.github.notablogger:spring-xpose-processor:0.1.3")
    compileOnly("io.github.notablogger:spring-xpose-annotations:0.1.3")
    annotationProcessor("jakarta.persistence:jakarta.persistence-api:3.1.0")
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

### Step 4 — Start your app and call the API

```bash
curl http://localhost:8080/api/products
# → []

curl -X POST http://localhost:8080/api/products \
  -H "Content-Type: application/json" \
  -d '{"name":"Laptop","price":999.99}'
# → {"id":1,"name":"Laptop","price":999.99}
```

That's it. No controller written. No repository written. No security config written.

### What was generated

Under `build/generated/sources/annotationProcessor/` you will find:

```
ProductRepository.java       ← extends JpaRepository<Product, Long>
ProductController.java       ← @RestController at /api/products, full OpenAPI annotations
ProductSecurityConfigurer.java ← SecurityFilterChain scoped to /api/products/**
```

These are real `.java` files — open them in your IDE, set breakpoints, read them.

---

## Swagger UI (Optional)

Add springdoc to your dependencies:

```groovy
implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0'
```

All generated controllers are annotated with `@Tag`, `@Operation`, and `@ApiResponse`. Secured endpoints show a 🔒 lock icon in Swagger UI automatically.

Configure the API title, description and version in `application.yml`:

```yaml
spring-xpose:
  api:
    title: "My App API"
    description: "Auto-generated CRUD API"
    version: "1.0.0"
```

---

## `@ExposeEntity` Reference

### Full example

```java
@Entity
@ExposeEntity(
    path       = "orders",                          // → /api/orders
    expose     = {Operation.FIND_ALL,               // which HTTP methods to generate
                  Operation.FIND_BY_ID,
                  Operation.CREATE,
                  Operation.UPDATE,
                  Operation.DELETE},
    relationMode = RelationMode.IDS_FOR_LIST_OBJECT_FOR_SINGLE,
    authType   = AuthType.BASIC,                    // HTTP Basic auth
    readRoles  = {"CUSTOMER", "ADMIN"},             // GET requires one of these roles
    writeRoles = {"ADMIN"}                          // POST/PUT/DELETE requires ADMIN
)
public class Order { ... }
```

### Attributes

| Attribute | Type | Default | Description |
|---|---|---|---|
| `path` | `String` | entity name pluralised | URL segment — `"products"` → `/api/products` |
| `expose` | `Operation[]` | all five | Which HTTP operations to generate |
| `relationMode` | `RelationMode` | `IDS_FOR_LIST_OBJECT_FOR_SINGLE` | How related entities are serialised |
| `authType` | `AuthType` | `NONE` | Authentication mechanism |
| `roles` | `String[]` | `{}` | Roles required for all operations |
| `readRoles` | `String[]` | `{}` | Roles for GET requests (overrides `roles`) |
| `writeRoles` | `String[]` | `{}` | Roles for POST/PUT/DELETE (overrides `roles`) |

### `expose` — Operations

| Value | HTTP Method | Path | Response |
|---|---|---|---|
| `FIND_ALL` | `GET` | `/api/{path}` | `200 OK` |
| `FIND_BY_ID` | `GET` | `/api/{path}/{id}` | `200 OK` / `404 Not Found` |
| `CREATE` | `POST` | `/api/{path}` | `201 Created` |
| `UPDATE` | `PUT` | `/api/{path}/{id}` | `200 OK` / `404 Not Found` |
| `DELETE` | `DELETE` | `/api/{path}/{id}` | `204 No Content` / `404 Not Found` |

### `authType` — Authentication

| Value | Generated security | Swagger UI |
|---|---|---|
| `NONE` | `permitAll()` — fully public | No lock |
| `BASIC` | HTTP Basic authentication | 🔒 username / password |
| `OAUTH2` | JWT Bearer token | 🔒 Bearer token |

### `relationMode` — Relation serialisation

Controls how `@ManyToOne` / `@OneToMany` fields are serialised:

| Mode | `GET /api/products` (list) | `GET /api/products/1` (single) |
|---|---|---|
| `IDS_FOR_LIST_OBJECT_FOR_SINGLE` | `"category": 3` | `"category": {"id":3,"name":"Books"}` |
| `ALWAYS_IDS` | `"category": 3` | `"category": 3` |
| `ALWAYS_OBJECT` | `"category": {"id":3,...}` | `"category": {"id":3,...}` |

---

## Configuration Reference

All properties live under the `spring-xpose` prefix in `application.yml` / `application.properties`.

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
| `processor` | `spring-xpose-processor` | `annotationProcessor` | Compile-time APT — generates `.java` files via JavaPoet |
| `starter` | `spring-xpose-starter` | `implementation` | Spring Boot autoconfiguration, `RelationAwareSerializer` |

---

## Sample Project

→ **[spring-xpose-sample-rest](https://github.com/notablogger/spring-xpose-sample-rest)** — a runnable Spring Boot app demonstrating all three auth types, multiple relation modes, validation, and Swagger UI.

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

- [x] REST — compile-time controller generation
- [x] Per-entity security (NONE / BASIC / OAUTH2)
- [x] Relation-aware serialisation
- [x] OpenAPI / Swagger UI with auth scheme
- [ ] GraphQL — compile-time DataFetcher wiring
- [ ] DTO projection support

---

## License

Apache 2.0 — see [LICENSE](LICENSE)

