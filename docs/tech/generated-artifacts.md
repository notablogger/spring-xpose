# Generated Artifacts — spring-xpose

For every class annotated with `@ExposeEntity` or `@ExposeDocument`, spring-xpose writes **six `.java` files** — eight when the class exposes `Operation.SEARCH` — into:

```
build/generated/sources/annotationProcessor/java/main/
└── <entity-package>.generated/
    ├── <Entity>Repository.java           (JpaRepository for @ExposeEntity, MongoRepository for @ExposeDocument)
    ├── <Entity>Dto.java
    ├── <Entity>RequestDto.java
    ├── <Entity>Mapper.java
    ├── <Entity>Controller.java
    └── <Entity>SecurityConfigurer.java
```

Two more files are written only when `expose` lists `Operation.SEARCH`:

```
    ├── <Entity>SearchRequest.java        (JSON body of POST /api/<path>/search)
    └── <Entity>Spec.java                 (JPA Specification / MongoDB Query builder)
```

These are real, readable source files — open them in your IDE, set breakpoints, and read them like any other class. They are **not** to be edited by hand; they are regenerated on every build.

**Note:** For MongoDB documents (`@ExposeDocument`), annotate the ID field with `@org.springframework.data.annotation.Id`. For JPA entities (`@ExposeEntity`), use `@jakarta.persistence.Id`.

---

## 1. `<Entity>Repository`

**Extends:** `JpaRepository<Entity, IdType>`  
**Annotated:** `@Repository`  
**Registered as:** Spring bean (via `@Repository`)

```java
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    // no custom methods — Spring Data's defaults cover all five operations
}
```

**Provides:**
- `findAll()` / `findAll(Pageable)` — for `FIND_ALL`
- `findAll(Specification)` / `findAll(Specification, Pageable)` — for `SEARCH`: JPA repositories of classes exposing `SEARCH` also extend `JpaSpecificationExecutor<Entity>`
- `findById(id)` — for `FIND_BY_ID`
- `save(entity)` — for `CREATE` and `UPDATE`
- `existsById(id)` / `deleteById(id)` — for `DELETE`

---

## 2. `<Entity>Dto`

**Type:** Plain Java class (POJO)  
**Purpose:** API response shape — what the controller returns to clients

### Field inclusion rules

| Source field | Included? | Shape in Dto |
|---|---|---|
| `@Id` field | ✅ always | same type |
| Scalar field | ✅ unless hidden | boxed type (e.g. `long` → `Long`) |
| `@ExposeField(hidden = true)` | ❌ never | — |
| `@ManyToOne` / `@OneToOne` — `ALWAYS_OBJECT` mode | ✅ | full related entity type |
| `@ManyToOne` / `@OneToOne` — `ALWAYS_IDS` or `IDS_FOR_LIST_OBJECT_FOR_SINGLE` mode | ✅ | `Long <fieldName>Id` |
| `@OneToMany` / `@ManyToMany` | ❌ always excluded | — prevents circular serialisation |

### Example

Entity:
```java
@Entity
@ExposeEntity
public class User {
    @Id private Long id;
    private String email;
    @ExposeField(hidden = true) private String passwordHash;
    @ManyToOne private Role role;
}
```

Generated `UserDto` (default `IDS_FOR_LIST_OBJECT_FOR_SINGLE`):
```java
public class UserDto {
    private Long id;
    private String email;
    // passwordHash omitted
    private Long roleId;  // relation as ID

    // getters + setters
}
```

---

## 3. `<Entity>RequestDto`

**Type:** Plain Java class (POJO)  
**Purpose:** Request body for `CREATE` (`POST`) and `UPDATE` (`PUT`) operations

### Field inclusion rules

| Source field | Included? | Notes |
|---|---|---|
| `@Id` field | ❌ never | ID comes from the URL path variable |
| `@Version` field | ❌ never | Managed by JPA |
| Scalar field | ✅ unless hidden | Jakarta Validation annotations copied |
| `@ExposeField(hidden = true)` | ❌ never | Can't be set via the API |
| `@ManyToOne` / `@OneToOne` | ��� | always as `Long <fieldName>Id` |
| `@OneToMany` / `@ManyToMany` | ❌ always excluded | — |

### Example

Continuing from the `User` entity above:
```java
public class UserRequestDto {
    @NotBlank
    private String email;
    // passwordHash omitted (hidden)
    // id omitted
    private Long roleId;  // always Long, regardless of relationMode

    // getters + setters
}
```

---

## 4. `<Entity>Mapper`

**Type:** Java `interface`  
**Framework:** MapStruct (or custom Spring bean when `customMapper` is set)  
**Annotated:** `@Mapper(componentModel = "spring")` — omitted when `customMapper` is set

### Generated methods

| Method | Signature | Purpose |
|---|---|---|
| `toDto` | `EntityDto toDto(Entity entity)` | Single entity → response DTO |
| `toDtoList` | `List<EntityDto> toDtoList(List<Entity> entities)` | List → list of DTOs |
| `toEntity` | `Entity toEntity(EntityRequestDto requestDto)` | Request DTO → new entity (CREATE) |
| `updateEntity` | `void updateEntity(EntityRequestDto requestDto, @MappingTarget Entity entity)` | Merge request fields onto loaded entity (UPDATE) |

### Mapping annotations generated on `toDto`

For `IDS_FOR_LIST_OBJECT_FOR_SINGLE` or `ALWAYS_IDS` relation mode:
```java
@Mapping(source = "role.id", target = "roleId")
UserDto toDto(User entity);
```

### Mapping annotations generated on `toEntity` and `updateEntity`

The following targets are always ignored in both methods — they must not be overwritten from the request body:

- `@Id` field — managed by JPA (`AUTO`/`IDENTITY`/`SEQUENCE`)
- `@Version` field (if present) — managed by JPA optimistic locking
- All hidden fields — not present in `RequestDto`; ignore explicitly to suppress MapStruct warnings
- Relation object fields — resolved by the controller via `EntityManager.getReference()`

### Custom mapper

When `@ExposeEntity(customMapper = MyMapper.class)` is set:
- `@Mapper` is **omitted** — MapStruct does not generate an implementation
- The interface is still generated as a contract
- Your `@Component` class must implement the generated `<Entity>Mapper` interface
- Spring wires it automatically wherever the controller injects `<Entity>Mapper`

---

## 5. `<Entity>Controller`

**Type:** `@RestController`  
**Base path:** `/api/<basePath>` (default: entity name pluralised, lowercased)  
**OpenAPI:** Full `@Tag`, `@Operation`, `@ApiResponse`, and `@Parameter` annotations on every method

### Conditionally generated methods

| `Operation` | HTTP method | Path | Request body | Response |
|---|---|---|---|---|
| `FIND_ALL` | `GET` | `/api/{path}` | — | `200 List<Dto>` or `200 Page<Dto>` if `pageable=true` — every record |
| `FIND_BY_ID` | `GET` | `/api/{path}/{id}` | — | `200 Dto` / `404` |
| `CREATE` | `POST` | `/api/{path}` | `@Valid RequestDto` | `201 Dto` / `400` |
| `UPDATE` | `PUT` | `/api/{path}/{id}` | `@Valid RequestDto` | `200 Dto` / `400` / `404` |
| `DELETE` | `DELETE` | `/api/{path}/{id}` | — | `204` / `404` |
| `SEARCH` | `POST` | `/api/{path}/search` | optional `SearchRequest` (missing = `{}`) | `200 List<Dto>` or `200 Page<Dto>` if `pageable=true` / `400` |

With `pageable = true`, `findAll` and `search` also take a `Pageable` (`?page=&size=&sort=`), annotated `@ParameterObject` when springdoc is on the classpath so Swagger UI shows three optional query parameters.

```java
@PostMapping("/search")
public ResponseEntity<Page<ProductDto>> search(
    @RequestBody(required = false) ProductSearchRequest request, @ParameterObject Pageable pageable) {
  ProductSearchRequest filters = request != null ? request : new ProductSearchRequest();
  SerializationContext.set(SerializationContext.Mode.LIST);
  try {
    return ResponseEntity.ok(repository.findAll(ProductSpec.withFilters(filters), pageable).map(mapper::toDto));
  } finally {
    SerializationContext.clear();
  }
}
```

### Dependencies injected into the controller

| Field | Type | When present |
|---|---|---|
| `repository` | `<Entity>Repository` | always |
| `mapper` | `<Entity>Mapper` | always |
| `entityManager` | `jakarta.persistence.EntityManager` | only when `CREATE` or `UPDATE` is enabled and the entity has non-ignored single relations |
| `mongoTemplate` | `MongoTemplate` | only for `@ExposeDocument` exposing `SEARCH` — runs the filter `Query` |

### `SerializationContext` usage

Before returning a response, the controller sets a `ThreadLocal` mode flag:
- `SerializationContext.set(LIST)` — for `findAll` and `search` (IDs for relations)
- `SerializationContext.set(SINGLE)` — for `findById` (full objects for `IDS_FOR_LIST_OBJECT_FOR_SINGLE`)

The `finally` block always calls `SerializationContext.clear()` to avoid cross-request leakage.

### Relation resolution in `CREATE` / `UPDATE`

For each non-ignored single relation field, the controller emits:
```java
if (requestDto.getCategoryId() != null) {
    entity.setCategory(entityManager.getReference(Category.class, requestDto.getCategoryId()));
}
```
`getReference()` returns a JPA proxy immediately — no `SELECT` is issued. The FK constraint is validated at flush time. A bad ID causes `jakarta.persistence.EntityNotFoundException`, which `SpringXposeExceptionHandler` converts to `422 Unprocessable Entity`.

---

## 6. `<Entity>SecurityConfigurer`

**Type:** `@Configuration` class with a `@Bean SecurityFilterChain` method  
**Scope:** Scoped to `/api/<basePath>` and `/api/<basePath>/**` via `securityMatcher`  
**Order:** Deterministic value in range 100–999 derived from `entity.hashCode()`

### Generated security rules by `authType`

**`AuthType.NONE`** — each exposed operation is public, anything else is denied:
```java
http.authorizeHttpRequests(auth -> auth
    .requestMatchers(GET,  "/api/products")        .permitAll()
    .requestMatchers(GET,  "/api/products/**")     .permitAll()
    .requestMatchers(POST, "/api/products/search") .permitAll()   // SEARCH
    .requestMatchers(POST, "/api/products")        .permitAll()
    // PUT / DELETE likewise
    .anyRequest().denyAll()
);
// no auth mechanism configured
```

**`AuthType.BASIC` or `AuthType.OAUTH2`** (with separate read/write roles):
```java
http.authorizeHttpRequests(auth -> auth
    .requestMatchers(GET,    "/api/products")      .hasAnyRole("CUSTOMER", "ADMIN")
    .requestMatchers(GET,    "/api/products/**")   .hasAnyRole("CUSTOMER", "ADMIN")
    .requestMatchers(POST,   "/api/products/search").hasAnyRole("CUSTOMER", "ADMIN")   // SEARCH is a read
    .requestMatchers(POST,   "/api/products")      .hasAnyRole("ADMIN")
    .requestMatchers(PUT,    "/api/products/**")   .hasAnyRole("ADMIN")
    .requestMatchers(DELETE, "/api/products/**")   .hasAnyRole("ADMIN")
    .anyRequest().denyAll()
);
http.httpBasic(withDefaults());           // BASIC
// or
http.oauth2ResourceServer(o -> o.jwt(withDefaults()));  // OAUTH2
```

Only exposed operations get a rule. `POST /api/<path>/search` always gets the **read** rule (`readRoles`, else `roles`, else `authenticated()`), never the write rule of `POST /api/<path>`.

CSRF is disabled — REST APIs are stateless and do not use cookie-based sessions.

---

## 7. `<Entity>SearchRequest`

**Type:** `final` class — the JSON request body of `POST /api/<path>/search`, read by Jackson  
**Generated when:** `expose` lists `Operation.SEARCH` (with no `filterOperations` it has no properties and accepts only `{}`)

One property per (field, operation): `EQ` uses the field name (`price`), every other operation `<field><Operation>` (`priceGt`, `statusNotIn`, `deletedAtIsNull`), read from the JSON key `<field>_<operation>` (`price_gt`, `status_not_in`, `deletedAt_is_null`). Primitive field types are boxed, so an absent key is `null` and adds no condition. `IN` / `NOT_IN` properties are a `List` of the field type — of the element type for collection fields — and an empty list also adds no condition; `IS_NULL` properties are `Boolean`. Each property carries `@JsonProperty("<key>")` — Jackson reads that key and springdoc documents it — and `@Schema(description = ...)`. A `@JsonAnySetter` receives every key without a property and throws, so unknown keys are a `400` regardless of the application's `FAIL_ON_UNKNOWN_PROPERTIES` setting:

```java
@Schema(description = "Search filters for Product. Every key is optional; keys are combined with AND. Unknown keys are rejected.")
public final class ProductSearchRequest {
  @JsonProperty("price")
  @Schema(description = "price equals")
  private BigDecimal price;

  @JsonProperty("price_gt")
  @Schema(description = "price greater than")
  private BigDecimal priceGt;

  public BigDecimal getPrice() { return price; }
  public BigDecimal getPriceGt() { return priceGt; }

  @JsonAnySetter
  void rejectUnknownKey(String key, Object value) {
    throw new IllegalArgumentException("Unknown search key '" + key + "' for Product — allowed keys: price, price_gt");
  }
}
```

---

## 8. `<Entity>Spec`

**Type:** class with a static `withFilters(<Entity>SearchRequest)` method  
**Generated when:** same as `<Entity>SearchRequest`

Each non-null search property adds one condition; conditions are AND-ed. No conditions match every record.

| Operation | JPA (`CriteriaBuilder`) | MongoDB (`Criteria`) |
|---|---|---|
| `EQ` | `cb.equal(path, v)` | `where(f).is(v)` |
| `NE` | `cb.or(cb.notEqual(path, v), cb.isNull(path))` | `where(f).ne(v)` |
| `GT` / `GTE` / `LT` / `LTE` | `cb.greaterThan(…)` / `greaterThanOrEqualTo` / `lessThan` / `lessThanOrEqualTo` | `gt` / `gte` / `lt` / `lte` |
| `LIKE` | `cb.like(cb.lower(path), likePattern(v), '\\')` | `where(f).regex(Pattern.quote(v), "i")` |
| `IN` | `path.in(list)` | `where(f).in(list)` |
| `NOT_IN` | `cb.or(cb.not(path.in(list)), cb.isNull(path))` | `where(f).nin(list)` |
| `IS_NULL` | `cb.isNull(path)` / `cb.isNotNull(path)` | `where(f).is(null)` / `ne(null)` |
| `IN` on a collection | `<field>HasAny(...)` (EXISTS subquery) | `where(f).in(list)` |
| `NOT_IN` on a collection | `cb.not(<field>HasAny(...))` | `where(f).nin(list)` |
| `IS_NULL` on a collection | `cb.isEmpty(path)` / `cb.isNotEmpty(path)` | `where(f + ".0").exists(!v)` |
| To-one relation (`categoryId…`) | as above on `root.get("category").get("id")` (FK column, no join); null checks on `root.get("category")` | — |
| To-many relation (`suppliers_in…`) | as collections; `<field>HasAny` compares `correlated.join("suppliers").get("id")` | — |

- **JPA** — returns `Specification<Entity>`, executed with `repository.findAll(spec)` / `findAll(spec, pageable)`. A `likePattern` helper is added when `LIKE` is used; it lower-cases the value and escapes `%`, `_` and `\`.
- **Collection fields** (JPA `@ElementCollection`) — `IN` / `NOT_IN` call a generated `<field>HasAny(root, query, cb, values)` helper: an `EXISTS` subquery that correlates the outer root and joins the collection, so matching rows are not duplicated.
- **MongoDB** — returns a `Query` built from `Criteria`, executed with `mongoTemplate.find(...)` (and `count(...)` for pageable). `LIKE` uses `Pattern.quote(value)` with the `i` option.

```java
public static Specification<Product> withFilters(ProductSearchRequest f) {
  return (root, query, cb) -> {
    List<Predicate> predicates = new ArrayList<>();
    if (f.getPrice() != null)
      predicates.add(cb.equal(root.get("price"), f.getPrice()));
    if (f.getPriceGt() != null)
      predicates.add(cb.greaterThan(root.<BigDecimal>get("price"), f.getPriceGt()));
    return cb.and(predicates.toArray(new Predicate[0]));
  };
}
```

---

## Error responses from generated controllers

A search body that cannot be read — a value of the wrong type (`{"price_gt": "abc"}`), a non-ISO date, a scalar where an array is expected, or an unknown key — is rejected while Jackson reads `<Entity>SearchRequest`, before any query runs. The response is the `400` malformed-body problem, with the failing **JSON key** (a path such as `categoryId_in[1]` for an array element) in `errors`:

```json
{
  "type": "urn:springxpose:malformed-body",
  "title": "Malformed request body",
  "status": 400,
  "detail": "The request body is invalid at 'price_gt': Cannot deserialize value of type `java.math.BigDecimal` from String \"abc\": not a valid representation",
  "errors": {
    "price_gt": "Cannot deserialize value of type `java.math.BigDecimal` from String \"abc\": not a valid representation"
  }
}
```

The same applies to `CREATE` / `UPDATE` bodies (`{"price": "abc"}` → `errors.price`). JSON that is not well-formed (`{oops`) has no failing key; its problem has only a `detail`.


All generated controllers benefit from `SpringXposeExceptionHandler` (auto-configured by the starter). Responses follow **RFC 9457 Problem Detail** format:

| Exception | HTTP status | `type` URN |
|---|---|---|
| `HttpMessageNotReadableException` | `400 Bad Request` | `urn:springxpose:malformed-body` |
| `MethodArgumentNotValidException` | `400 Bad Request` | `urn:springxpose:validation-error` |
| `EntityNotFoundException` | `422 Unprocessable Entity` | `urn:springxpose:relation-not-found` |
| `DataIntegrityViolationException` | `409 Conflict` | `urn:springxpose:constraint-violation` |
| `OptimisticLockingFailureException` | `409 Conflict` | `urn:springxpose:optimistic-lock` |

Validation errors include a per-field `errors` map in the problem detail body:
```json
{
  "type": "urn:springxpose:validation-error",
  "title": "Validation failed",
  "status": 400,
  "detail": "One or more fields failed validation.",
  "errors": {
    "name": "must not be blank",
    "price": "must be greater than 0"
  }
}
```

