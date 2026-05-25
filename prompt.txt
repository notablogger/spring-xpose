# Implementation Prompt: `spring-xpose-boot-starter`

## What You Are Building

A Spring Boot starter library that automatically exposes JPA entities as full CRUD APIs —
either standard REST (Spring MVC) or GraphQL — controlled by a single `@ExposeEntity`
annotation on the entity class. The API mode (REST or GraphQL) is set once globally in
`application.properties`. Security (OAuth2 or Basic Auth), roles, and exposed operations
are configured per-entity on the annotation.

**Target stack:** Java 21 · Spring Boot 3.3.x · Spring Framework 6.x · Spring MVC ·
Spring for GraphQL · Spring Security (Servlet) · Spring Data JPA

---

## Core Design Decisions (Non-Negotiable)

| Decision | Choice |
|---|---|
| API mode toggle | Global property `spring-xpose.mode=REST\|GRAPHQL` — mutually exclusive |
| Web layer | Spring MVC — standard synchronous `@RestController`, no WebFlux |
| Repository layer | Spring Data JPA — blocking, straightforward |
| Default auth | `AuthType.NONE` = permit all, no config required |
| DTO layer | None in v1 — entities exposed directly |
| NoSQL | Not in scope (v2 roadmap) |
| Schema files | None — GraphQL schema generated 100% at runtime via reflection |
| Annotation on | The `@Entity` class itself |

---

## The `@ExposeEntity` Annotation

```java
package io.github.springxpose.annotation;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ExposeEntity {

    /**
     * REST base path or GraphQL type name prefix.
     * Defaults to lowercase pluralized entity name (e.g. "products" for Product).
     */
    String path() default "";

    /**
     * Which CRUD operations to expose. Defaults to all five.
     */
    Operation[] expose() default {
        Operation.FIND_ALL,
        Operation.FIND_BY_ID,
        Operation.CREATE,
        Operation.UPDATE,
        Operation.DELETE
    };

    /**
     * How to serialize relation fields.
     * IDS_FOR_LIST_OBJECT_FOR_SINGLE: list endpoints return IDs, single-item returns full object (default).
     * ALWAYS_IDS:    always serialize relations as ID values only.
     * ALWAYS_OBJECT: always serialize full nested object.
     */
    RelationMode relationMode() default RelationMode.IDS_FOR_LIST_OBJECT_FOR_SINGLE;

    /**
     * Authentication mechanism required to access this entity's endpoints.
     * NONE = permit all (default).
     */
    AuthType authType() default AuthType.NONE;

    /**
     * Roles required for ALL operations (read + write).
     * Empty = no role restriction beyond authentication.
     */
    String[] roles() default {};

    /**
     * Roles required for read operations only (GET / GraphQL query).
     * Overrides `roles` for reads if non-empty.
     */
    String[] readRoles() default {};

    /**
     * Roles required for write operations only
     * (POST / PUT / DELETE / GraphQL mutation).
     * Overrides `roles` for writes if non-empty.
     */
    String[] writeRoles() default {};
}
```

### Supporting Enums

```java
public enum Operation    { FIND_ALL, FIND_BY_ID, CREATE, UPDATE, DELETE }
public enum RelationMode { IDS_FOR_LIST_OBJECT_FOR_SINGLE, ALWAYS_IDS, ALWAYS_OBJECT }
public enum AuthType     { NONE, BASIC, OAUTH2 }
```

---

## Project Structure

```
spring-xpose-boot-starter/
│
├── pom.xml                                     # parent POM
│
├── starter/                                    # the library module
│   ├── pom.xml
│   └── src/main/java/io/github/springxpose/
│       │
│       ├── annotation/
│       │   ├── ExposeEntity.java
│       │   ├── Operation.java
│       │   ├── RelationMode.java
│       │   └── AuthType.java
│       │
│       ├── model/
│       │   └── EntityExposureMetadata.java     # record holding all resolved metadata
│       │
│       ├── registry/
│       │   └── EntityExposureRegistry.java     # runtime store: entity class → metadata
│       │
│       ├── processor/
│       │   └── EntityExposureProcessor.java    # BeanDefinitionRegistryPostProcessor
│       │
│       ├── repository/
│       │   └── DynamicRepositoryFactory.java   # registers JpaRepository beans at runtime
│       │
│       ├── rest/
│       │   ├── EntityController.java           # generic @RestController per entity
│       │   └── RelationAwareSerializer.java    # Jackson: IDs vs object by operation context
│       │
│       ├── graphql/
│       │   ├── SchemaGenerator.java            # builds RuntimeWiring at startup
│       │   └── DataFetcherFactory.java         # DataFetcher per operation
│       │
│       ├── security/
│       │   ├── SecurityConfigurerFactory.java  # builds SecurityFilterChain
│       │   └── OperationRoleResolver.java      # resolves effective roles per HTTP method
│       │
│       ├── config/
│       │   ├── SpringXposeProperties.java      # @ConfigurationProperties
│       │   └── SpringXposeAutoConfiguration.java
│       │
│       └── util/
│           └── EntityNameUtils.java            # pluralize, lowercase, derive path/type names
│
│   └── src/main/resources/META-INF/spring/
│       └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
│
├── sample/                                     # runnable demo
│   ├── pom.xml
│   └── src/main/java/io/github/springxpose/sample/
│       ├── SampleApplication.java
│       ├── entity/
│       │   ├── Product.java
│       │   └── Category.java
│       └── DataLoader.java                     # CommandLineRunner with test data
│
└── .github/workflows/
    ├── ci.yml
    ├── release.yml
    └── sample-build.yml
```

---

## `EntityExposureMetadata` Record

```java
public record EntityExposureMetadata(
    Class<?>        entityClass,
    Class<?>        idClass,        // resolved from @Id field via reflection
    String          basePath,       // resolved path (annotation value or auto-derived)
    Set<Operation>  operations,     // which operations to expose
    RelationMode    relationMode,
    AuthType        authType,
    Set<String>     roles,
    Set<String>     readRoles,
    Set<String>     writeRoles
) {
    /** Returns effective read roles: readRoles if non-empty, else roles. */
    public Set<String> effectiveReadRoles() {
        return readRoles.isEmpty() ? roles : readRoles;
    }

    /** Returns effective write roles: writeRoles if non-empty, else roles. */
    public Set<String> effectiveWriteRoles() {
        return writeRoles.isEmpty() ? roles : writeRoles;
    }
}
```

---

## `SpringXposeProperties` (`@ConfigurationProperties`)

```java
@ConfigurationProperties(prefix = "spring-xpose")
public class SpringXposeProperties {

    /** REST or GRAPHQL — mutually exclusive, application-wide. Default: REST */
    private ApiMode mode = ApiMode.REST;

    /** Base path prefix for all REST routes. Default: /api */
    private String restBasePath = "/api";

    /** GraphQL endpoint path. Default: /graphql */
    private String graphqlPath = "/graphql";

    public enum ApiMode { REST, GRAPHQL }

    // getters + setters
}
```

`application.properties` examples:
```properties
# Switch entire app to GraphQL mode
spring-xpose.mode=GRAPHQL

# Customize REST base path
spring-xpose.rest-base-path=/api/v1
```

---

## Startup Scanner (`EntityExposureProcessor`)

Implement `BeanDefinitionRegistryPostProcessor` + `ApplicationContextAware`:

1. After context refresh, scan the classpath for classes annotated with **both**
   `@jakarta.persistence.Entity` and `@ExposeEntity`.
2. For each found class:
   - Resolve `idClass` by finding the field annotated `@jakarta.persistence.Id` via
     reflection. If not found, throw `EntityExposeConfigurationException` with a clear
     message (entity name + missing annotation).
   - Resolve `basePath`: use `annotation.path()` if non-blank, otherwise
     `EntityNameUtils.toPlural(entityClass.getSimpleName().toLowerCase())`.
   - Build `EntityExposureMetadata` record and register in `EntityExposureRegistry`.
3. For each entity, call `DynamicRepositoryFactory` to register a `JpaRepository<T, ID>`
   Spring bean if one does not already exist (check by generic type match against existing
   bean definitions).
4. Log at `INFO` for each entity:
   ```
   [SpringXpose] Exposing Product   → REST /api/products   ops=[FIND_ALL, FIND_BY_ID, CREATE, UPDATE] auth=OAUTH2
   [SpringXpose] Exposing Category  → GRAPHQL               ops=[FIND_ALL, FIND_BY_ID, CREATE, UPDATE, DELETE] auth=BASIC
   ```

---

## REST Layer (active when `spring-xpose.mode=REST`)

### `EntityController`

For each `EntityExposureMetadata`, programmatically register a Spring MVC controller bean
at runtime using `RequestMappingHandlerMapping`. Each controller handles only the
operations listed in `metadata.operations()`.

| Operation     | HTTP   | Path                         | Return type              | Status |
|---------------|--------|------------------------------|--------------------------|--------|
| `FIND_ALL`    | GET    | `{restBasePath}/{path}`      | `List<T>`                | 200    |
| `FIND_BY_ID`  | GET    | `{restBasePath}/{path}/{id}` | `T`                      | 200 / 404 |
| `CREATE`      | POST   | `{restBasePath}/{path}`      | `T`                      | 201    |
| `UPDATE`      | PUT    | `{restBasePath}/{path}/{id}` | `T`                      | 200 / 404 |
| `DELETE`      | DELETE | `{restBasePath}/{path}/{id}` | `void`                   | 204    |

Implementation approach: create a single generic `EntityControllerHandler` class with
all five methods. For each entity, register a `RequestMappingInfo` pointing at the
correct handler method, bound to that entity's path and its `JpaRepository` instance.
Use `RequestMappingHandlerMapping#registerMapping(...)` for dynamic registration.

Response conventions:
- `FIND_BY_ID` with no result → `ResponseEntity.notFound().build()` (404)
- `UPDATE` with no result → `ResponseEntity.notFound().build()` (404)
- `CREATE` → `ResponseEntity.status(HttpStatus.CREATED).body(saved)`
- `DELETE` → `ResponseEntity.noContent().build()` (204)

### `RelationAwareSerializer`

A Jackson `JsonSerializer` registered via `Jackson2ObjectMapperBuilderCustomizer`.

Uses a `ThreadLocal<SerializationContext>` (values: `LIST` or `SINGLE`) set by the
handler before calling `repository.findAll()` or `repository.findById()`.

Serialization behaviour per `RelationMode`:

| Mode | LIST context | SINGLE context |
|---|---|---|
| `IDS_FOR_LIST_OBJECT_FOR_SINGLE` | Relation field → ID value(s) | Relation field → full object |
| `ALWAYS_IDS` | Relation field → ID value(s) | Relation field → ID value(s) |
| `ALWAYS_OBJECT` | Relation field → full object | Relation field → full object |

For `@ManyToOne`: serialize as the related entity's `@Id` field value (a single scalar).
For `@OneToMany` / `@ManyToMany`: serialize as a `List` of ID values.
Extract the `@Id` field from the related entity type via reflection.

Handler usage:
```java
// FIND_ALL handler
SerializationContext.set(LIST);
List<T> results = repository.findAll();
SerializationContext.clear();
return ResponseEntity.ok(results);

// FIND_BY_ID handler
SerializationContext.set(SINGLE);
T result = repository.findById(id).orElseThrow(...);
SerializationContext.clear();
return ResponseEntity.ok(result);
```

---

## GraphQL Layer (active when `spring-xpose.mode=GRAPHQL`)

### `SchemaGenerator`

Called at startup. Produces a `RuntimeWiring` and `TypeDefinitionRegistry` contributed
to the application's `GraphQlSource` via `RuntimeWiringConfigurer`.

**Types generated per entity** (via reflection over entity fields):

```graphql
# Object type
type Product {
  id: ID
  name: String
  price: Float
  categoryId: ID          # @ManyToOne → ID by default (ALWAYS_IDS / IDS_FOR_LIST_OBJECT_FOR_SINGLE list)
  # OR:
  category: Category      # if ALWAYS_OBJECT
}

# Input type for CREATE (no id field; relations as IDs)
input ProductInput {
  name: String!
  price: Float!
  categoryId: ID
}

# Input type for UPDATE (id required; relations as IDs)
input ProductUpdateInput {
  id: ID!
  name: String
  price: Float
  categoryId: ID
}
```

**Queries registered** (only if operation is in `expose()`):
```graphql
findAllProducts:      [Product]
findProductById(id: ID!): Product
```

**Mutations registered** (only if operation is in `expose()`):
```graphql
createProduct(input: ProductInput!):         Product
updateProduct(input: ProductUpdateInput!):   Product
deleteProduct(id: ID!):                      Boolean
```

**Relation handling:**
- Input types always use IDs for relations (never nested objects) — avoids circular
  reference issues and keeps mutations clean.
- Object types: respect `RelationMode`. For `IDS_FOR_LIST_OBJECT_FOR_SINGLE`, register
  two `GraphQLObjectType` variants — one with ID fields (used in list queries), one with
  full nested types (used in single queries). Name them `Product` and `ProductDetail`
  internally, but alias `findAllProducts` to return `[Product]` and `findProductById`
  to return `ProductDetail`.
- `ALWAYS_IDS`: all relation fields rendered as ID scalars in both queries.
- `ALWAYS_OBJECT`: full nested type in both queries (ensure recursive types are handled
  via `GraphQLTypeReference` to avoid infinite recursion).

### `DataFetcherFactory`

Wire `DataFetcher`s via `RuntimeWiringConfigurer`. Use `CompletableFuture` for
async-compatible execution:

```java
// FIND_ALL
env -> CompletableFuture.supplyAsync(() -> repository.findAll())

// FIND_BY_ID
env -> CompletableFuture.supplyAsync(() ->
    repository.findById(parseId(env.getArgument("id"), metadata.idClass()))
              .orElse(null))

// CREATE
env -> CompletableFuture.supplyAsync(() -> {
    T entity = mapInputToEntity(env.getArgument("input"), metadata.entityClass());
    return repository.save(entity);
})

// UPDATE
env -> CompletableFuture.supplyAsync(() -> {
    T entity = mapUpdateInputToEntity(env.getArgument("input"), metadata);
    return repository.save(entity);
})

// DELETE
env -> CompletableFuture.supplyAsync(() -> {
    repository.deleteById(parseId(env.getArgument("id"), metadata.idClass()));
    return true;
})
```

`mapInputToEntity`: instantiate entity via no-arg constructor, set fields via reflection
from the GraphQL arguments map. For relation fields (e.g. `categoryId`): look up the
related entity by ID via its registered repository, then set the relation field.

---

## Security Layer

### `SecurityConfigurerFactory`

Builds a single `SecurityFilterChain` (Spring MVC / Servlet) covering all exposed entity
paths. Uses `HttpSecurity`.

**Strategy per entity:**

- `authType = NONE` → `permitAll()` on all methods for this entity's path pattern
- `authType = BASIC` → enable `httpBasic()`, apply role rules below
- `authType = OAUTH2` → enable `oauth2ResourceServer(oauth2 -> oauth2.jwt(...))`,
  apply role rules below

**Role rules per path (REST):**

```java
http.authorizeHttpRequests(auth -> auth
    // Read operations
    .requestMatchers(HttpMethod.GET, entityPath + "/**")
        .hasAnyRole(effectiveReadRoles)    // or permitAll() if empty

    // Write operations
    .requestMatchers(HttpMethod.POST, entityPath)
        .hasAnyRole(effectiveWriteRoles)
    .requestMatchers(HttpMethod.PUT, entityPath + "/**")
        .hasAnyRole(effectiveWriteRoles)
    .requestMatchers(HttpMethod.DELETE, entityPath + "/**")
        .hasAnyRole(effectiveWriteRoles)
);
```

**GraphQL security:** Since all GraphQL traffic hits one path (`/graphql`), add a
`OncePerRequestFilter` that:
1. Reads and caches the request body (use `ContentCachingRequestWrapper`).
2. Parses the operation string to detect `query` vs `mutation` keyword.
3. Extracts the operation name (e.g. `createProduct` → maps to `Product` entity).
4. Looks up the entity's metadata from `EntityExposureRegistry`.
5. Validates the `Authorization` header against the entity's `authType` and applicable
   read/write roles.
6. Returns `401 Unauthorized` (no/invalid credentials) or `403 Forbidden` (wrong role)
   if validation fails; otherwise calls `filterChain.doFilter(...)`.

For OAuth2 JWT: use `JwtDecoder` configured from standard Spring Boot properties
(`spring.security.oauth2.resourceserver.jwt.issuer-uri`).

For Basic Auth: authenticate against `UserDetailsService`. The library provides a
`@ConditionalOnMissingBean` default that reads from standard
`spring.security.user.*` properties. Users can provide their own `UserDetailsService`
bean to override.

**Critical constraint:** Use `@Order(Ordered.LOWEST_PRECEDENCE - 10)` on the generated
`SecurityFilterChain` so user-defined chains always take priority. The library adds to
security, never replaces it.

---

## `SpringXposeAutoConfiguration`

```java
@AutoConfiguration
@EnableConfigurationProperties(SpringXposeProperties.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SpringXposeAutoConfiguration {

    @Bean
    public EntityExposureRegistry entityExposureRegistry() { ... }

    @Bean
    public EntityExposureProcessor entityExposureProcessor(
            EntityExposureRegistry registry,
            DynamicRepositoryFactory repoFactory) { ... }

    @Bean
    @ConditionalOnProperty(name = "spring-xpose.mode", havingValue = "REST", matchIfMissing = true)
    @ConditionalOnClass(name = "org.springframework.web.servlet.DispatcherServlet")
    public EntityControllerRegistrar entityRestControllers(
            EntityExposureRegistry registry,
            SpringXposeProperties props) { ... }

    @Bean
    @ConditionalOnProperty(name = "spring-xpose.mode", havingValue = "GRAPHQL")
    @ConditionalOnClass(name = "org.springframework.graphql.execution.GraphQlSource")
    public RuntimeWiringConfigurer entityGraphQlWiring(
            EntityExposureRegistry registry) { ... }

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE - 10)
    public SecurityFilterChain entityExposeSecurityChain(
            HttpSecurity http,
            EntityExposureRegistry registry,
            SpringXposeProperties props) throws Exception { ... }
}
```

Register in:
```
src/main/resources/META-INF/spring/
  org.springframework.boot.autoconfigure.AutoConfiguration.imports
```
Contents:
```
io.github.springxpose.config.SpringXposeAutoConfiguration
```

---

## Testing Requirements

### Unit Tests

| Test class | What it verifies |
|---|---|
| `EntityExposureProcessorTest` | Metadata built correctly for all annotation config combos; path derivation; `idClass` resolution; `EntityExposeConfigurationException` thrown when `@Id` missing |
| `EntityControllerRegistrarTest` | Correct request mappings registered; omitted operations produce no mapping |
| `SchemaGeneratorTest` | Correct SDL produced for entity with/without relations; input types correct; mutations absent when operation excluded |
| `RelationAwareSerializerTest` | `IDS_FOR_LIST_OBJECT_FOR_SINGLE`: IDs on LIST context, object on SINGLE; `ALWAYS_IDS`; `ALWAYS_OBJECT` |
| `OperationRoleResolverTest` | `effectiveReadRoles` / `effectiveWriteRoles` logic with all combos of `roles` / `readRoles` / `writeRoles` |
| `EntityNameUtilsTest` | Pluralization edge cases: `Category→categories`, `Person→people`, `Status→statuses`, `Company→companies` |

### Integration Tests (all use `@SpringBootTest` + `MockMvc` + H2)

| Test class | What it verifies |
|---|---|
| `RestCrudIntegrationTest` | Full CRUD lifecycle: POST → GET all → GET by id → PUT → DELETE, correct status codes |
| `RestRelationSerializationTest` | GET all returns IDs for relations; GET by id returns full nested object |
| `RestPartialExposeTest` | Entity with `expose={FIND_ALL, FIND_BY_ID}` — POST/PUT/DELETE return 404/405 |
| `RestNotFoundTest` | GET by unknown id returns 404; PUT unknown id returns 404 |
| `GraphQLQueryIntegrationTest` | `findAll` + `findById` queries return correct data and correct field shapes |
| `GraphQLMutationIntegrationTest` | `create`, `update`, `delete` mutations work end-to-end |
| `GraphQLRelationTest` | List query returns IDs; single query returns full nested object |
| `SecurityOAuth2IntegrationTest` | No token → 401; invalid token → 401; wrong role → 403; correct role + token → 200 |
| `SecurityBasicAuthIntegrationTest` | No credentials → 401; wrong role → 403; correct credentials + role → 200 |
| `SecurityPermitAllIntegrationTest` | `AuthType.NONE` entity: no credentials needed, all ops accessible |
| `SecurityReadWriteRoleTest` | `readRoles` user: GET succeeds, POST returns 403; `writeRoles` user: POST succeeds, GET returns 403 |
| `ModeExclusivityTest` | With `mode=REST`, `/graphql` returns 404; with `mode=GRAPHQL`, REST entity paths not registered |

### Test Utilities (in `starter/src/test/`)

```java
// Composed annotation — use on all integration tests
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
public @interface SpringXposeTest {}

// Generates test JWTs signed with an in-memory RSA key pair
public class MockJwtFactory {
    public static String forRoles(String... roles) { ... }
    public static String expired() { ... }
    public static String withoutRoles() { ... }
}

// Fluent builder for creating test entities in H2
public class TestEntityBuilder<T> {
    public static <T> TestEntityBuilder<T> of(Class<T> type) { ... }
    public TestEntityBuilder<T> field(String name, Object value) { ... }
    public T build() { ... }        // instantiates + sets fields via reflection
    public T persist(JpaRepository<T, ?> repo) { ... }
}
```

---

## Sample Project (`sample/`)

### `Product.java`

```java
@Entity
@ExposeEntity(
    path = "products",
    expose = {Operation.FIND_ALL, Operation.FIND_BY_ID, Operation.CREATE, Operation.UPDATE},
    // DELETE intentionally excluded
    authType = AuthType.OAUTH2,
    readRoles  = {"ROLE_USER", "ROLE_ADMIN"},
    writeRoles = {"ROLE_ADMIN"},
    relationMode = RelationMode.IDS_FOR_LIST_OBJECT_FOR_SINGLE
)
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private BigDecimal price;
    @ManyToOne
    private Category category;
    // getters + setters
}
```

### `Category.java`

```java
@Entity
@ExposeEntity(
    path = "categories",
    authType = AuthType.BASIC,
    roles = {"ROLE_USER"},          // all ops require ROLE_USER
    relationMode = RelationMode.ALWAYS_OBJECT
)
public class Category {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    @OneToMany(mappedBy = "category", fetch = FetchType.LAZY)
    private List<Product> products;
    // getters + setters
}
```

### `DataLoader.java`

A `CommandLineRunner` that inserts 2 categories and 5 products on startup so the sample
works out of the box with no manual setup.

### `application.properties` (sample)

```properties
spring-xpose.mode=REST
spring-xpose.rest-base-path=/api

spring.datasource.url=jdbc:h2:mem:sampledb
spring.h2.console.enabled=true
spring.jpa.show-sql=true

# OAuth2 — point at a local Spring Authorization Server or Keycloak
spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:9000

# Basic auth user for Category endpoints
spring.security.user.name=admin
spring.security.user.password=secret
spring.security.user.roles=USER
```

### README `curl` examples (required in `README.md`)

```bash
# List all products (OAuth2 Bearer token required, ROLE_USER)
curl -H "Authorization: Bearer $TOKEN" \
     http://localhost:8080/api/products

# Get single product (full category object returned)
curl -H "Authorization: Bearer $TOKEN" \
     http://localhost:8080/api/products/1

# Create a product (ROLE_ADMIN required)
curl -X POST \
     -H "Content-Type: application/json" \
     -H "Authorization: Bearer $ADMIN_TOKEN" \
     -d '{"name":"Widget","price":9.99,"category":{"id":1}}' \
     http://localhost:8080/api/products

# Update a product
curl -X PUT \
     -H "Content-Type: application/json" \
     -H "Authorization: Bearer $ADMIN_TOKEN" \
     -d '{"name":"Updated Widget","price":12.99,"category":{"id":1}}' \
     http://localhost:8080/api/products/1

# List all categories (Basic Auth, ROLE_USER)
curl -u admin:secret \
     http://localhost:8080/api/categories

# GraphQL — list products (switch spring-xpose.mode=GRAPHQL)
curl -X POST \
     -H "Content-Type: application/json" \
     -d '{"query":"{ findAllProducts { id name price categoryId } }"}' \
     http://localhost:8080/graphql

# GraphQL — create product mutation
curl -X POST \
     -H "Content-Type: application/json" \
     -H "Authorization: Bearer $ADMIN_TOKEN" \
     -d '{"query":"mutation { createProduct(input: {name:\"Gadget\", price:19.99, categoryId:1}) { id name } }"}' \
     http://localhost:8080/graphql
```

---

## Build Pipeline

### Parent `pom.xml`

```xml
<groupId>io.github.spring-xpose</groupId>
<artifactId>spring-xpose-parent</artifactId>
<version>0.1.0-SNAPSHOT</version>
<packaging>pom</packaging>

<modules>
  <module>starter</module>
  <module>sample</module>
</modules>

<properties>
  <java.version>21</java.version>
  <maven.compiler.release>21</maven.compiler.release>
  <spring-boot.version>3.3.4</spring-boot.version>
</properties>
```

### Starter `pom.xml` — Key Dependencies

```xml
<!-- Core -->
<dependency>spring-boot-starter-web</dependency>           <!-- Spring MVC -->
<dependency>spring-boot-starter-data-jpa</dependency>
<dependency>spring-boot-starter-security</dependency>
<dependency>spring-boot-starter-oauth2-resource-server</dependency>
<dependency>spring-graphql</dependency>                    <!-- optional, conditional -->
<dependency>graphql-java</dependency>                      <!-- optional, conditional -->

<!-- Utilities -->
<dependency>lombok</dependency>
<dependency>spring-boot-autoconfigure-processor</dependency>
<dependency>spring-boot-configuration-processor</dependency>

<!-- Test -->
<dependency scope="test">spring-boot-starter-test</dependency>
<dependency scope="test">h2</dependency>
<dependency scope="test">spring-security-test</dependency>
```

Note: `spring-graphql` and `graphql-java` should be declared as `optional` so they are
not pulled into user projects that only want REST mode.

### Maven Plugins (for Maven Central publish)

```xml
<plugin>maven-source-plugin</plugin>        <!-- attach sources jar -->
<plugin>maven-javadoc-plugin</plugin>       <!-- attach javadoc jar -->
<plugin>maven-gpg-plugin</plugin>           <!-- sign with ${gpg.passphrase} -->
<plugin>nexus-staging-maven-plugin</plugin> <!-- publish via Sonatype OSSRH -->
```

---

## GitHub Actions

### `.github/workflows/ci.yml`

Triggers: push and pull_request on all branches.

```yaml
name: CI
on: [push, pull_request]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '21', distribution: 'temurin' }
      - uses: actions/cache@v4
        with:
          path: ~/.m2
          key: "${{ runner.os }}-maven-${{ hashFiles('**/pom.xml') }}"
      - run: mvn --batch-mode verify
```

### `.github/workflows/release.yml`

Triggers: push of a `v*` tag (e.g. `v0.1.0`).

```yaml
name: Release
on:
  push:
    tags: ['v*']
jobs:
  release:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '21', distribution: 'temurin', server-id: ossrh }
      - name: Import GPG key
        run: echo "${{ secrets.GPG_PRIVATE_KEY }}" | gpg --import
      - name: Write Maven settings
        run: |
          mkdir -p ~/.m2
          cat > ~/.m2/settings.xml << EOF
          <settings>
            <servers>
              <server>
                <id>ossrh</id>
                <username>${{ secrets.OSSRH_USERNAME }}</username>
                <password>${{ secrets.OSSRH_PASSWORD }}</password>
              </server>
            </servers>
          </settings>
          EOF
      - run: mvn --batch-mode -Prelease release:prepare release:perform
        env:
          GPG_PASSPHRASE: ${{ secrets.GPG_PASSPHRASE }}
      - name: Create GitHub Release
        run: gh release create ${{ github.ref_name }} --generate-notes
        env: { GITHUB_TOKEN: "${{ secrets.GITHUB_TOKEN }}" }
```

### `.github/workflows/sample-build.yml`

Triggers: push to `main`.

```yaml
name: Sample Build
on:
  push:
    branches: [main]
jobs:
  sample:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '21', distribution: 'temurin' }
      - run: mvn --batch-mode package -pl sample -am -DskipTests
```

### Required Repository Secrets

| Secret | Purpose |
|---|---|
| `OSSRH_USERNAME` | Sonatype OSSRH username |
| `OSSRH_PASSWORD` | Sonatype OSSRH password |
| `GPG_PRIVATE_KEY` | Armored GPG private key for artifact signing |
| `GPG_PASSPHRASE` | Passphrase for the GPG key |
| `GITHUB_TOKEN` | Automatic — used for GitHub Release creation |

---

## Constraints & Rules

1. **Standard Spring MVC only.** No WebFlux, no `Mono`, no `Flux`, no `RouterFunction`.
   All controllers return plain `ResponseEntity<T>` or `List<T>`.
2. **No `.graphqls` schema files required from the user.** All GraphQL schema is built
   at runtime via reflection.
3. **Never replace user's security config.** Use `@Order(Ordered.LOWEST_PRECEDENCE - 10)`
   on the generated `SecurityFilterChain`.
4. **Java 21 features encouraged:** records for `EntityExposureMetadata`, sealed
   interfaces where appropriate, pattern matching in switch expressions.
5. **No APT or compile-time code generation.** All controller and schema wiring happens
   at runtime via reflection and Spring's `BeanDefinitionRegistry`.
6. **`@ConditionalOnMissingBean`** on all library-provided beans so users can override
   any component (repository, serializer, security chain, DataFetcher, etc.).
7. **Graceful startup failure:** if an entity's `@Id` field cannot be resolved, throw
   `EntityExposeConfigurationException` with the entity class name and a clear message
   — never a `NullPointerException` at runtime.
8. **Thread safety:** `EntityExposureRegistry` is write-once at startup and read-only
   thereafter. Use an immutable map internally (e.g. `Map.copyOf(...)`).
9. **No `@SuppressWarnings("unchecked")`** without an inline comment explaining why it
   is safe.
10. **`graphql-java` and `spring-graphql` are optional dependencies.** The starter must
    boot and function correctly in REST mode even when these JARs are absent from the
    classpath.

---

## Deliverables Checklist

- [ ] `starter/` module compiles and all unit tests pass (`mvn test -pl starter`)
- [ ] `sample/` boots cleanly, `DataLoader` inserts test data, all endpoints accessible
      with correct credentials
- [ ] All integration tests pass against H2 (`mvn verify`)
- [ ] `mvn package` produces a jar containing the correct
      `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- [ ] REST mode works with `spring-graphql` absent from classpath
- [ ] GraphQL mode works with correct schema auto-generated — no `.graphqls` files needed
- [ ] `README.md` contains: quick-start guide, full annotation reference table, auth
      configuration guide (OAuth2 + Basic), `curl` examples, Mermaid architecture
      diagram, Maven Central badge, secrets setup table
- [ ] All three GitHub Actions workflows are present and syntactically valid YAML