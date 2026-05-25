# Implementation Prompt: `spring-xpose-boot-starter`

## What You Are Building

A Spring Boot starter library that automatically exposes JPA entities as full CRUD APIs —
either standard REST (Spring MVC) or GraphQL — controlled by a single `@ExposeEntity`
annotation on the entity class.

At **compile time**, an annotation processor (APT) reads every class annotated with
`@ExposeEntity` and generates real `.java` source files — one controller per entity for
REST mode, one DataFetcher wiring class per entity for GraphQL mode. These files land in
`build/generated/sources/annotationProcessor/` and are fully visible, debuggable, and
navigable in any IDE.

The API mode (REST or GraphQL) is set once globally in `application.properties`. Security
(OAuth2 or Basic Auth), roles, and exposed operations are configured per-entity on the
annotation.

**Target stack:**
Java 21 · Spring Boot 3.3.x · Spring Framework 6.x · Spring MVC · Spring for GraphQL ·
Spring Security (Servlet) · Spring Data JPA · Gradle (Groovy DSL) · JavaPoet (code generation)

---

## Core Design Decisions (Non-Negotiable)

| Decision | Choice |
|---|---|
| Code generation | APT (`AbstractProcessor`) + JavaPoet — real `.java` files at compile time |
| API mode toggle | Global property `spring-xpose.mode=REST\|GRAPHQL` — mutually exclusive |
| Web layer | Spring MVC — `@RestController`, `ResponseEntity<T>`, `List<T>` |
| Repository layer | Spring Data JPA — blocking, straightforward |
| Default auth | `AuthType.NONE` = permit all, zero config required |
| DTO layer | None in v1 — entities exposed directly |
| NoSQL | Not in scope (v2 roadmap) |
| GraphQL schema files | None — schema generated at runtime from the generated wiring classes |
| Build system | Gradle, Groovy DSL |
| Publishing | GitHub Packages |

---

## The `@ExposeEntity` Annotation

```java
package io.github.springxpose.annotation;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)   // consumed at compile time by the APT processor
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
     * IDS_FOR_LIST_OBJECT_FOR_SINGLE: list endpoint returns IDs, single-item returns full object.
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

Note: annotation retention is `SOURCE` — the APT processor consumes it at compile time
and generates `.java` files. The generated files are what Spring Boot loads at runtime.

---

## Project Structure

```
spring-xpose/
│
├── settings.gradle                         # declares all subprojects
├── build.gradle                            # root build — shared config
│
├── annotations/                            # module 1: annotation definitions only
│   ├── build.gradle
│   └── src/main/java/io/github/springxpose/annotation/
│       ├── ExposeEntity.java
│       ├── Operation.java
│       ├── RelationMode.java
│       └── AuthType.java
│
├── processor/                              # module 2: APT annotation processor
│   ├── build.gradle
│   └── src/main/java/io/github/springxpose/processor/
│       ├── ExposeEntityProcessor.java      # AbstractProcessor entry point
│       ├── model/
│       │   └── EntityModel.java           # parsed metadata from annotation + entity class
│       ├── generator/
│       │   ├── RestControllerGenerator.java   # writes ProductController.java via JavaPoet
│       │   ├── GraphQlWiringGenerator.java    # writes ProductGraphQlWiring.java via JavaPoet
│       │   └── SecurityConfigurerGenerator.java # writes XposeSecurityConfigurer.java
│       └── util/
│           └── EntityNameUtils.java        # pluralize, lowercase, derive names
│   └── src/main/resources/META-INF/services/
│       └── javax.annotation.processing.Processor   # registers ExposeEntityProcessor
│
├── starter/                                # module 3: Spring Boot autoconfiguration
│   ├── build.gradle
│   └── src/main/java/io/github/springxpose/
│       ├── config/
│       │   ├── SpringXposeProperties.java
│       │   └── SpringXposeAutoConfiguration.java
│       └── serializer/
│           └── RelationAwareSerializer.java  # Jackson serializer used by generated controllers
│   └── src/main/resources/META-INF/spring/
│       └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
│
├── sample/                                 # module 4: runnable demo app
│   ├── build.gradle
│   └── src/main/java/io/github/springxpose/sample/
│       ├── SampleApplication.java
│       ├── entity/
│       │   ├── Product.java
│       │   └── Category.java
│       └── DataLoader.java
│   └── src/main/resources/
│       └── application.properties
│
└── .github/workflows/
    ├── ci.yml
    ├── release.yml
    └── sample-build.yml
```

**Why three separate modules?**
- `annotations` has zero dependencies — users depend on it with `compileOnly` scope.
- `processor` depends on `annotations` + JavaPoet — users depend on it with
  `annotationProcessor` scope only (not in the runtime classpath).
- `starter` is the only runtime dependency users add — it provides autoconfiguration,
  the `RelationAwareSerializer`, and `SpringXposeProperties`.

---

## Module 1: `annotations/build.gradle`

```groovy
plugins {
    id 'java-library'
    id 'maven-publish'
}

dependencies {
    // no dependencies — annotation definitions only
}

publishing {
    publications {
        mavenJava(MavenPublication) {
            from components.java
            groupId = 'io.github.spring-xpose'
            artifactId = 'spring-xpose-annotations'
        }
    }
    repositories {
        maven {
            name = 'GitHubPackages'
            url = 'https://maven.pkg.github.com/YOUR_GITHUB_USERNAME/spring-xpose'
            credentials {
                username = System.getenv('GITHUB_ACTOR')
                password = System.getenv('GITHUB_TOKEN')
            }
        }
    }
}
```

---

## Module 2: `processor/build.gradle`

```groovy
plugins {
    id 'java-library'
    id 'maven-publish'
}

dependencies {
    implementation project(':annotations')
    implementation 'com.squareup:javapoet:1.13.0'

    // needed to read JPA annotations from entity classes during processing
    compileOnly 'jakarta.persistence:jakarta.persistence-api:3.1.0'

    // Spring annotations needed to generate correct controller/security code
    compileOnly 'org.springframework:spring-web:6.1.0'
    compileOnly 'org.springframework:spring-context:6.1.0'
    compileOnly 'org.springframework.boot:spring-boot-autoconfigure:3.3.4'
}

// disable incremental annotation processing for correctness
tasks.withType(JavaCompile) {
    options.compilerArgs += ['-proc:full']
}
```

---

## Module 3: `starter/build.gradle`

```groovy
plugins {
    id 'java-library'
    id 'maven-publish'
}

dependencies {
    api project(':annotations')

    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-oauth2-resource-server'

    // GraphQL — optional; only needed when spring-xpose.mode=GRAPHQL
    compileOnly 'org.springframework.boot:spring-boot-starter-graphql'

    annotationProcessor 'org.springframework.boot:spring-boot-configuration-processor'
}
```

---

## Module 4: `sample/build.gradle`

```groovy
plugins {
    id 'org.springframework.boot' version '3.3.4'
    id 'java'
}

dependencies {
    implementation project(':starter')

    // the processor generates controllers at compile time
    annotationProcessor project(':processor')
    compileOnly project(':annotations')

    runtimeOnly 'com.h2database:h2'
}
```

---

## Root `settings.gradle`

```groovy
rootProject.name = 'spring-xpose'

include 'annotations'
include 'processor'
include 'starter'
include 'sample'
```

---

## Root `build.gradle`

```groovy
allprojects {
    group = 'io.github.spring-xpose'
    version = '0.1.0-SNAPSHOT'
}

subprojects {
    apply plugin: 'java'

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
        withSourcesJar()
        withJavadocJar()
    }

    repositories {
        mavenCentral()
    }

    test {
        useJUnitPlatform()
    }
}
```

---

## The Annotation Processor (`ExposeEntityProcessor`)

This is the heart of the library. Implement as a standard Java APT processor.

### `ExposeEntityProcessor.java`

```java
@SupportedAnnotationTypes("io.github.springxpose.annotation.ExposeEntity")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
@AutoService(Processor.class)   // use google/auto-service to generate META-INF/services entry
public class ExposeEntityProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations,
                           RoundEnvironment roundEnv) {

        for (Element element : roundEnv.getElementsAnnotatedWith(ExposeEntity.class)) {
            if (element.getKind() != ElementKind.CLASS) continue;

            TypeElement entityClass = (TypeElement) element;
            EntityModel model = EntityModel.parse(entityClass, processingEnv);

            ApiMode mode = resolveMode();  // read from processor options or default REST

            if (mode == ApiMode.REST) {
                new RestControllerGenerator(processingEnv).generate(model);
            } else {
                new GraphQlWiringGenerator(processingEnv).generate(model);
            }

            new SecurityConfigurerGenerator(processingEnv).generate(model);
        }
        return true;
    }
}
```

Use `google/auto-service` (`com.google.auto.service:auto-service:1.1.1`) to avoid
manually maintaining the `META-INF/services` file.

### `EntityModel.java`

A plain record parsed from the `TypeElement` at compile time:

```java
public record EntityModel(
    String entityClassName,          // fully qualified, e.g. com.example.Product
    String entitySimpleName,         // Product
    String packageName,              // com.example
    String idFieldName,              // id
    String idClassName,              // java.lang.Long
    String basePath,                 // products
    List<FieldModel> fields,         // all non-static, non-transient fields
    List<RelationFieldModel> relations, // fields annotated with @ManyToOne / @OneToMany etc.
    Set<Operation> operations,
    RelationMode relationMode,
    AuthType authType,
    Set<String> roles,
    Set<String> readRoles,
    Set<String> writeRoles
) {
    public static EntityModel parse(TypeElement element, ProcessingEnvironment env) {
        // Read @ExposeEntity annotation values
        // Find @Id annotated field — emit compile error via env.getMessager() if missing
        // Collect all fields, identify relation fields via @ManyToOne / @OneToMany etc.
        // Derive basePath from annotation.path() or pluralized lowercase class name
    }
}
```

If `@Id` is not found, emit a **compile error** (not a runtime exception):
```java
processingEnv.getMessager().printMessage(
    Diagnostic.Kind.ERROR,
    "@ExposeEntity: no @Id field found on " + element.getSimpleName(),
    element
);
```
This stops the build with a clear message pointing to the exact entity class — far better
than a runtime failure.

---

## REST Code Generation (`RestControllerGenerator`)

Uses **JavaPoet** to generate one `@RestController` class per entity.

### Generated output example for `Product`

```java
// Generated by spring-xpose — do not edit
package com.example.generated;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductRepository repository;

    public ProductController(ProductRepository repository) {
        this.repository = repository;
    }

    // Generated only if Operation.FIND_ALL is in expose()
    @GetMapping
    public ResponseEntity<List<Product>> findAll() {
        SerializationContext.set(SerializationContext.Mode.LIST);
        try {
            return ResponseEntity.ok(repository.findAll());
        } finally {
            SerializationContext.clear();
        }
    }

    // Generated only if Operation.FIND_BY_ID is in expose()
    @GetMapping("/{id}")
    public ResponseEntity<Product> findById(@PathVariable Long id) {
        SerializationContext.set(SerializationContext.Mode.SINGLE);
        try {
            return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
        } finally {
            SerializationContext.clear();
        }
    }

    // Generated only if Operation.CREATE is in expose()
    @PostMapping
    public ResponseEntity<Product> create(@RequestBody @Valid Product entity) {
        return ResponseEntity.status(HttpStatus.CREATED)
                             .body(repository.save(entity));
    }

    // Generated only if Operation.UPDATE is in expose()
    @PutMapping("/{id}")
    public ResponseEntity<Product> update(@PathVariable Long id,
                                          @RequestBody @Valid Product entity) {
        if (!repository.existsById(id)) return ResponseEntity.notFound().build();
        entity.setId(id);
        return ResponseEntity.ok(repository.save(entity));
    }

    // Generated only if Operation.DELETE is in expose()
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!repository.existsById(id)) return ResponseEntity.notFound().build();
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
```

### JavaPoet generation approach

```java
public class RestControllerGenerator {

    public void generate(EntityModel model) {
        MethodSpec findAll = MethodSpec.methodBuilder("findAll")
            .addAnnotation(GetMapping.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(ParameterizedTypeName.get(
                ClassName.get(ResponseEntity.class),
                ParameterizedTypeName.get(ClassName.get(List.class),
                    ClassName.bestGuess(model.entityClassName()))))
            .addStatement("$T.set($T.Mode.LIST)", SerializationContext.class,
                          SerializationContext.class)
            // ... add try/finally, repository call
            .build();

        TypeSpec controller = TypeSpec.classBuilder(model.entitySimpleName() + "Controller")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(RestController.class)
            .addAnnotation(AnnotationSpec.builder(RequestMapping.class)
                .addMember("value", "$S", "/" + model.basePath())
                .build())
            .addField(repositoryField(model))
            .addMethod(constructor(model))
            .addMethod(findAll)
            // ... other methods based on model.operations()
            .build();

        JavaFile.builder(model.packageName() + ".generated", controller)
            .build()
            .writeTo(processingEnv.getFiler());
    }
}
```

Key rule: **only generate a method if the corresponding `Operation` is present in
`model.operations()`**. No method = no route. Clean and obvious.

---

## GraphQL Code Generation (`GraphQlWiringGenerator`)

Generates one `RuntimeWiringConfigurer` implementation per entity.

### Generated output example for `Product`

```java
// Generated by spring-xpose — do not edit
package com.example.generated;

@Component
public class ProductGraphQlWiring implements RuntimeWiringConfigurer {

    private final ProductRepository repository;

    public ProductGraphQlWiring(ProductRepository repository) {
        this.repository = repository;
    }

    @Override
    public void configure(RuntimeWiring.Builder builder) {
        // Query: findAllProducts
        builder.type("Query", wiring -> wiring
            .dataFetcher("findAllProducts",
                env -> repository.findAll())

            .dataFetcher("findProductById",
                env -> repository.findById(
                    Long.valueOf(env.getArgument("id").toString())
                ).orElse(null))
        );

        // Mutations
        builder.type("Mutation", wiring -> wiring
            .dataFetcher("createProduct", env -> {
                Map<String, Object> input = env.getArgument("input");
                Product entity = mapToEntity(input);
                return repository.save(entity);
            })
            .dataFetcher("updateProduct", env -> {
                Map<String, Object> input = env.getArgument("input");
                Long id = Long.valueOf(input.get("id").toString());
                if (!repository.existsById(id)) return null;
                Product entity = mapToEntity(input);
                return repository.save(entity);
            })
            .dataFetcher("deleteProduct", env -> {
                Long id = Long.valueOf(env.getArgument("id").toString());
                repository.deleteById(id);
                return true;
            })
        );
    }

    // Generated: maps GraphQL input map to entity fields by name
    private Product mapToEntity(Map<String, Object> input) {
        Product entity = new Product();
        if (input.containsKey("name"))
            entity.setName((String) input.get("name"));
        if (input.containsKey("price"))
            entity.setPrice(new BigDecimal(input.get("price").toString()));
        // relation fields: categoryId → load Category by id and set
        return entity;
    }
}
```

Also generate a `ProductGraphQlSchema.java` `@Component` that contributes the SDL
type definitions programmatically via `GraphQlSourceBuilderCustomizer`:

```java
@Component
public class ProductGraphQlSchema implements GraphQlSourceBuilderCustomizer {
    @Override
    public void customize(GraphQlSource.SchemaResourceBuilder builder) {
        builder.schemaResources(
            new ByteArrayResource("""
                type Product {
                  id: ID
                  name: String
                  price: Float
                  categoryId: ID
                }
                input ProductInput {
                  name: String!
                  price: Float!
                  categoryId: ID
                }
                input ProductUpdateInput {
                  id: ID!
                  name: String
                  price: Float
                  categoryId: ID
                }
                extend type Query {
                  findAllProducts: [Product]
                  findProductById(id: ID!): Product
                }
                extend type Mutation {
                  createProduct(input: ProductInput!): Product
                  updateProduct(input: ProductUpdateInput!): Product
                  deleteProduct(id: ID!): Boolean
                }
            """.getBytes())
        );
    }
}
```

Relation field handling in SDL:
- `ALWAYS_IDS` / `IDS_FOR_LIST_OBJECT_FOR_SINGLE`: generate `categoryId: ID`
- `ALWAYS_OBJECT`: generate `category: Category` (also ensure `Category` type is registered)

---

## Security Code Generation (`SecurityConfigurerGenerator`)

Generates one `SecurityFilterChain` `@Configuration` class per entity.

### Generated output example for `Product`

```java
// Generated by spring-xpose — do not edit
package com.example.generated;

@Configuration
public class ProductSecurityConfigurer {

    @Bean
    @Order(101)   // above default (100) so it is evaluated first; user beans take lower order
    public SecurityFilterChain productSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/products", "/api/products/**")
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/api/products/**")
                    .hasAnyRole("USER", "ADMIN")         // from readRoles
                .requestMatchers(HttpMethod.POST, "/api/products")
                    .hasRole("ADMIN")                    // from writeRoles
                .requestMatchers(HttpMethod.PUT, "/api/products/**")
                    .hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/products/**")
                    .hasRole("ADMIN")
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }
}
```

Generation rules:
- `authType = NONE` → generate `.anyRequest().permitAll()`, no auth mechanism configured
- `authType = BASIC` → add `.httpBasic(Customizer.withDefaults())`
- `authType = OAUTH2` → add `.oauth2ResourceServer(oauth2 -> oauth2.jwt(...))`
- `roles` non-empty and `readRoles`/`writeRoles` empty → apply to all methods
- `readRoles` non-empty → use for GET; `writeRoles` non-empty → use for POST/PUT/DELETE
- Empty roles array with non-NONE authType → `.authenticated()` (auth required, no role check)
- Each entity gets its own `@Bean` method with a unique name (`productSecurityFilterChain`,
  `categorySecurityFilterChain`) and its own `@Order` value to avoid bean name conflicts

For GraphQL mode, additionally generate a `OncePerRequestFilter` that inspects the
request body to detect `query` vs `mutation`, maps the operation name to the entity,
and enforces the appropriate role before passing to the GraphQL handler.

---

## `RelationAwareSerializer` (in `starter` module — runtime)

This is the only significant runtime component that cannot be code-generated (since it
needs to act dynamically during Jackson serialization). It lives in the `starter` module.

```java
public class RelationAwareSerializer extends JsonSerializer<Object> {

    // ThreadLocal set by generated controllers before each repository call
    public static final ThreadLocal<Mode> CONTEXT = new ThreadLocal<>();
    public enum Mode { LIST, SINGLE }

    @Override
    public void serialize(Object value, JsonGenerator gen,
                          SerializerProvider provider) throws IOException {
        Mode mode = CONTEXT.get();
        // inspect @ManyToOne / @OneToMany on the field being serialized
        // if relation and mode == LIST → write ID scalar
        // if relation and mode == SINGLE → delegate to default serializer (full object)
        // if ALWAYS_IDS → always write ID
        // if ALWAYS_OBJECT → always delegate
    }
}
```

Registered via `Jackson2ObjectMapperBuilderCustomizer` in `SpringXposeAutoConfiguration`.

---

## `SpringXposeAutoConfiguration` (in `starter` module)

Kept minimal — most work is done at compile time by the processor.

```java
@AutoConfiguration
@EnableConfigurationProperties(SpringXposeProperties.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SpringXposeAutoConfiguration {

    // Registers RelationAwareSerializer with Jackson
    @Bean
    @ConditionalOnMissingBean
    public Jackson2ObjectMapperBuilderCustomizer relationSerializerCustomizer() { ... }

    // Provides SpringXposeProperties so generated security configurers can read base path
    // Everything else (controllers, wiring, security) is generated code — Spring picks it up
    // automatically via @RestController / @Component / @Configuration on the generated classes
}
```

That's it. The autoconfiguration is intentionally thin because the generated `@RestController`,
`@Component`, and `@Configuration` classes are picked up by Spring's normal component scan
— no manual registration needed.

---

## Testing Requirements

### Unit Tests (in `processor` module)

Test the code generator directly — compile test entity classes in-process and assert on
the generated source.

Use **Google Compile Testing** (`com.google.testing.compile:compile-testing:0.21.0`) for
this — it lets you run the APT processor against a source string and assert on the
generated output.

| Test class | What it verifies |
|---|---|
| `RestControllerGeneratorTest` | Generated controller has correct class name, path, methods; omitted operations produce no method; correct HTTP status codes |
| `GraphQlWiringGeneratorTest` | Generated wiring registers correct queries/mutations; omitted operations absent; correct field names |
| `SecurityConfigurerGeneratorTest` | `authType=NONE` → `permitAll()`; `OAUTH2` → jwt config present; role rules generated correctly; `readRoles` / `writeRoles` split correct |
| `EntityModelParserTest` | `@Id` missing → compile error emitted; path derived correctly; relation fields detected |
| `EntityNameUtilsTest` | `Category→categories`, `Person→people`, `Status→statuses`, `Company→companies` |

Example using Compile Testing:

```java
@Test
void generatesGetMappingForFindAll() {
    Compilation compilation = Compiler.javac()
        .withProcessors(new ExposeEntityProcessor())
        .compile(JavaFileObjects.forSourceString(
            "com.example.Product",
            """
            package com.example;
            @jakarta.persistence.Entity
            @io.github.springxpose.annotation.ExposeEntity(path = "products")
            public class Product {
                @jakarta.persistence.Id
                private Long id;
                private String name;
                // getters/setters
            }
            """));

    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("com.example.generated.ProductController")
        .contentsAsUtf8String()
        .contains("@GetMapping")
        .contains("/api/products");
}
```

### Integration Tests (in `sample` module — `@SpringBootTest` + `MockMvc` + H2)

| Test class | What it verifies |
|---|---|
| `RestCrudIntegrationTest` | Full CRUD lifecycle: POST → GET all → GET by id → PUT → DELETE |
| `RestRelationSerializationTest` | GET all returns IDs for relations; GET by id returns full object |
| `RestPartialExposeTest` | Entity with `expose={FIND_ALL, FIND_BY_ID}` — POST/PUT/DELETE return 404/405 |
| `RestNotFoundTest` | GET /products/99999 → 404; PUT /products/99999 → 404 |
| `GraphQLQueryIntegrationTest` | `findAll` + `findById` return correct data and field shapes |
| `GraphQLMutationIntegrationTest` | `create`, `update`, `delete` mutations work end-to-end |
| `SecurityOAuth2IntegrationTest` | No token → 401; invalid token → 401; wrong role → 403; correct role → 200 |
| `SecurityBasicAuthIntegrationTest` | No credentials → 401; wrong role → 403; correct role → 200 |
| `SecurityPermitAllIntegrationTest` | `AuthType.NONE` entity: no credentials needed, all ops work |
| `SecurityReadWriteRoleTest` | `readRoles` user: GET → 200, POST → 403; `writeRoles` user: POST → 200, GET → 403 |

### Test Utilities

```java
// Composed annotation for all integration tests
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
    roles = {"ROLE_USER"},
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

### What the processor generates for these two entities

```
sample/build/generated/sources/annotationProcessor/java/main/
  com/example/generated/
    ProductController.java          ← @RestController with 4 methods (no DELETE)
    ProductSecurityConfigurer.java  ← SecurityFilterChain with OAuth2 + role split
    CategoryController.java         ← @RestController with all 5 methods
    CategorySecurityConfigurer.java ← SecurityFilterChain with Basic Auth + ROLE_USER
```

These files are fully visible in the IDE, navigable with Cmd+Click, and show up in
stack traces by their real class names.

### `application.properties` (sample)

```properties
spring-xpose.mode=REST
spring-xpose.rest-base-path=/api

spring.datasource.url=jdbc:h2:mem:sampledb
spring.h2.console.enabled=true
spring.jpa.show-sql=true

spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:9000
spring.security.user.name=admin
spring.security.user.password=secret
spring.security.user.roles=USER
```

### `curl` examples (required in README.md)

```bash
# List all products (OAuth2, ROLE_USER)
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/products

# Get single product (full category object returned)
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/products/1

# Create product (OAuth2, ROLE_ADMIN)
curl -X POST \
     -H "Content-Type: application/json" \
     -H "Authorization: Bearer $ADMIN_TOKEN" \
     -d '{"name":"Widget","price":9.99,"category":{"id":1}}' \
     http://localhost:8080/api/products

# Update product
curl -X PUT \
     -H "Content-Type: application/json" \
     -H "Authorization: Bearer $ADMIN_TOKEN" \
     -d '{"name":"Updated Widget","price":12.99,"category":{"id":1}}' \
     http://localhost:8080/api/products/1

# List categories (Basic Auth)
curl -u admin:secret http://localhost:8080/api/categories

# GraphQL query (set spring-xpose.mode=GRAPHQL)
curl -X POST \
     -H "Content-Type: application/json" \
     -d '{"query":"{ findAllProducts { id name price categoryId } }"}' \
     http://localhost:8080/graphql
```

---

## GitHub Actions

### `.github/workflows/ci.yml`

Triggers: push + pull_request on all branches.

```yaml
name: CI
on: [push, pull_request]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      - uses: actions/cache@v4
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: "${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle') }}"
      - run: ./gradlew check
```

### `.github/workflows/release.yml`

Triggers: push of a `v*` tag.

```yaml
name: Publish to GitHub Packages
on:
  push:
    tags: ['v*']
jobs:
  publish:
    runs-on: ubuntu-latest
    permissions:
      contents: write
      packages: write
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      - name: Publish annotations, processor, starter
        run: ./gradlew :annotations:publish :processor:publish :starter:publish
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
          GITHUB_ACTOR: ${{ github.actor }}
      - name: Create GitHub Release
        run: gh release create ${{ github.ref_name }} --generate-notes
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
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
        with:
          java-version: '21'
          distribution: 'temurin'
      - run: ./gradlew :sample:build
```

### Required Secrets

| Secret | Purpose |
|---|---|
| `GITHUB_TOKEN` | Automatic — publish to GitHub Packages + create release |

No GPG signing or OSSRH credentials needed for GitHub Packages.

---

## Constraints & Rules

1. **All controller, wiring, and security code is generated at compile time.** No runtime
   reflection to register controllers or security chains. The only runtime component
   is `RelationAwareSerializer` in the `starter` module.
2. **`@RetentionPolicy.SOURCE` on `@ExposeEntity`** — it is consumed by the processor and
   does not need to be present at runtime.
3. **Compile errors, not runtime exceptions.** Use `processingEnv.getMessager()` with
   `Diagnostic.Kind.ERROR` for all configuration mistakes (missing `@Id`, invalid path
   characters, etc.).
4. **Generated files are in `build/` — never committed to source control.** Add
   `build/` to `.gitignore`. The sample README must explain that generated files appear
   in `build/generated/sources/annotationProcessor/` and how to view them in IntelliJ
   and VS Code.
5. **Java 21 features in the processor:** use records for `EntityModel` and `FieldModel`,
   pattern matching in switches when inspecting element kinds.
6. **`@ConditionalOnMissingBean`** on all `starter` beans so users can override
   `RelationAwareSerializer` or any autoconfiguration component.
7. **`spring-graphql` and `graphql-java` are `compileOnly` in `starter`** — the starter
   must boot and function correctly in REST mode without them on the classpath.
8. **No `@SuppressWarnings("unchecked")`** without an inline comment explaining safety.
9. **Generated class names must not conflict** — always suffix with `Controller`,
   `GraphQlWiring`, `GraphQlSchema`, `SecurityConfigurer`. Place in a `.generated`
   subpackage of the entity's package.
10. **Thread safety of `SerializationContext`:** always use try/finally in generated
    controllers to ensure `SerializationContext.clear()` is called even on exceptions.

---

## Deliverables Checklist

- [ ] `./gradlew check` passes — all unit + integration tests green
- [ ] `./gradlew :sample:build` produces a runnable jar
- [ ] Sample boots, `DataLoader` inserts data, all endpoints work with correct auth
- [ ] Generated files visible in `build/generated/sources/annotationProcessor/`
- [ ] REST mode works without `spring-graphql` on the classpath
- [ ] GraphQL mode produces correct schema with no `.graphqls` files
- [ ] `./gradlew :annotations:publish :processor:publish :starter:publish` publishes to
  GitHub Packages (requires `GITHUB_TOKEN`)
- [ ] All three GitHub Actions workflows are present and valid YAML
- [ ] `README.md` includes: quick-start, annotation reference table, auth guide,
  `curl` examples, Mermaid architecture diagram, how-to view generated files in IDE,
  GitHub Packages install instructions