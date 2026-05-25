# spring-xpose

A Spring Boot library that generates **REST controllers**, **security configurations**, and **relation-aware serializers** at **compile time** from a single `@ExposeEntity` annotation on your JPA entity.

---

## Installation

spring-xpose ships as **3 coordinates** — one version, added once each for three different Gradle/Maven classpaths. This is the same pattern used by Lombok and MapStruct.

### Gradle (Groovy DSL)

```groovy
dependencies {
    // Runtime: Spring Boot autoconfiguration, serializer, security wiring
    implementation 'io.github.notablogger:spring-xpose-starter:0.1.0'

    // Compile-time only: generates controllers/repositories/security at build time
    annotationProcessor 'io.github.notablogger:spring-xpose-processor:0.1.0'

    // Compile-time only: @ExposeEntity annotation and supporting enums
    compileOnly 'io.github.notablogger:spring-xpose-annotations:0.1.0'

    // Required so the processor can read JPA annotations from your entities
    annotationProcessor 'jakarta.persistence:jakarta.persistence-api:3.1.0'
}
```

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("io.github.notablogger:spring-xpose-starter:0.1.0")
    annotationProcessor("io.github.notablogger:spring-xpose-processor:0.1.0")
    compileOnly("io.github.notablogger:spring-xpose-annotations:0.1.0")
    annotationProcessor("jakarta.persistence:jakarta.persistence-api:3.1.0")
}
```

### Maven

```xml
<dependencies>
    <dependency>
        <groupId>io.github.notablogger</groupId>
        <artifactId>spring-xpose-starter</artifactId>
        <version>0.1.0</version>
    </dependency>
    <dependency>
        <groupId>io.github.notablogger</groupId>
        <artifactId>spring-xpose-annotations</artifactId>
        <version>0.1.0</version>
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
                        <version>0.1.0</version>
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

> **Why 3 coordinates?**
> The annotation processor runs only at compile time and must never end up on the runtime classpath (it pulls in JavaPoet, auto-service, etc. — none of which your app needs). `compileOnly` keeps the annotation source-only. `implementation` (the starter) is the only jar that ships with your application. This is identical to how Lombok, MapStruct, and Dagger work.

---

## Quick Start

### Annotate your entity

```java
@Entity
@ExposeEntity(
    path = "products",
    expose = {Operation.FIND_ALL, Operation.FIND_BY_ID, Operation.CREATE, Operation.UPDATE, Operation.DELETE},
    relationMode = RelationMode.IDS_FOR_LIST_OBJECT_FOR_SINGLE,
    authType = AuthType.BASIC,
    readRoles  = {"USER", "ADMIN"},
    writeRoles = {"ADMIN"}
)
public class Product {
    @Id @GeneratedValue private Long id;
    private String name;
    private Double price;
    // getters / setters ...
}
```

### Build your project — that's it

At compile time the processor generates:

| Generated class | What it does |
|---|---|
| `ProductRepository` | Spring Data JPA repository in `*.generated` package |
| `ProductController` | `@RestController` at `/api/products` with full OpenAPI annotations |
| `ProductSecurityConfigurer` | `@Configuration` with a `SecurityFilterChain` scoped to `/api/products/**` |

---

## Modules

| Module | Artifact | Purpose |
|---|---|---|
| `annotations` | `spring-xpose-annotations` | `@ExposeEntity` and supporting enums |
| `processor` | `spring-xpose-processor` | Compile-time APT (JavaPoet-based code generation) |
| `starter` | `spring-xpose-starter` | Spring Boot auto-configuration, `RelationAwareSerializer` |

---

## `@ExposeEntity` Reference

| Attribute | Default | Description |
|---|---|---|
| `path` | entity name pluralised | REST base path segment (e.g. `"products"`) |
| `expose` | all five operations | Which CRUD operations to generate |
| `relationMode` | `IDS_FOR_LIST_OBJECT_FOR_SINGLE` | How relations are serialised |
| `authType` | `NONE` | `NONE`, `BASIC`, or `OAUTH2` |
| `roles` | `{}` | Roles for all operations |
| `readRoles` | `{}` | Roles for GET operations (overrides `roles`) |
| `writeRoles` | `{}` | Roles for POST/PUT/DELETE (overrides `roles`) |

### Operations

| Operation | HTTP | Path |
|---|---|---|
| `FIND_ALL` | `GET` | `/api/{path}` |
| `FIND_BY_ID` | `GET` | `/api/{path}/{id}` |
| `CREATE` | `POST` | `/api/{path}` — returns `201 Created` |
| `UPDATE` | `PUT` | `/api/{path}/{id}` |
| `DELETE` | `DELETE` | `/api/{path}/{id}` — returns `204 No Content` |

### Auth types

| `authType` | Generated security |
|---|---|
| `NONE` | `permitAll()` — fully public |
| `BASIC` | HTTP Basic with optional role-based matchers |
| `OAUTH2` | JWT bearer token with optional role-based matchers |

### Relation modes

| Mode | List (`GET /api/products`) | Single (`GET /api/products/1`) |
|---|---|---|
| `IDS_FOR_LIST_OBJECT_FOR_SINGLE` | `"category": 3` | `"category": {"id":3,"name":"Books"}` |
| `ALWAYS_IDS` | `"category": 3` | `"category": 3` |
| `ALWAYS_OBJECT` | full nested object | full nested object |

---

## Optional: OpenAPI / Swagger UI

All generated controllers are annotated with `@Tag`, `@Operation`, and `@ApiResponse`. Add springdoc to get a live Swagger UI at `/swagger-ui.html`:

```groovy
implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0'
```

---

## Building locally

```bash
./gradlew build
```

## Running tests

```bash
./gradlew test
```

---

## Sample projects

- **REST sample** — [spring-xpose-sample-rest](https://github.com/notablogger/spring-xpose-sample-rest)
- **GraphQL sample** — coming soon

---

## License

Apache 2.0

