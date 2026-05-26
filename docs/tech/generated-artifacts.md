# Generated Artifacts — spring-xpose

For every class annotated with `@ExposeEntity`, spring-xpose writes **six `.java` files** into:

```
build/generated/sources/annotationProcessor/java/main/
└── <entity-package>.generated/
    ├── <Entity>Repository.java
    ├── <Entity>Dto.java
    ├── <Entity>RequestDto.java
    ├── <Entity>Mapper.java
    ├── <Entity>Controller.java
    └── <Entity>SecurityConfigurer.java
```

These are real, readable source files — open them in your IDE, set breakpoints, and read them like any other class. They are **not** to be edited by hand; they are regenerated on every build.

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
| Scalar field | ✅ unless in `ignoredFields` | boxed type (e.g. `long` → `Long`) |
| `ignoredFields` | ❌ never | — |
| `@ManyToOne` / `@OneToOne` — `ALWAYS_OBJECT` mode | ✅ | full related entity type |
| `@ManyToOne` / `@OneToOne` — `ALWAYS_IDS` or `IDS_FOR_LIST_OBJECT_FOR_SINGLE` mode | ✅ | `Long <fieldName>Id` |
| `@OneToMany` / `@ManyToMany` | ❌ always excluded | — prevents circular serialisation |

### Example

Entity:
```java
@Entity
@ExposeEntity(ignoredFields = {"passwordHash"})
public class User {
    @Id private Long id;
    private String email;
    private String passwordHash;
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
| Scalar field | ✅ unless in `ignoredFields` | Jakarta Validation annotations copied |
| `ignoredFields` | ❌ never | Can't be set via the API |
| `@ManyToOne` / `@OneToOne` | ��� | always as `Long <fieldName>Id` |
| `@OneToMany` / `@ManyToMany` | ❌ always excluded | — |

### Example

Continuing from the `User` entity above:
```java
public class UserRequestDto {
    @NotBlank
    private String email;
    // passwordHash omitted (ignoredFields)
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
- All `ignoredFields` — not present in `RequestDto`; ignore explicitly to suppress MapStruct warnings
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
| `FIND_ALL` | `GET` | `/api/{path}` | — | `200 List<Dto>` or `200 Page<Dto>` if `pageable=true` |
| `FIND_BY_ID` | `GET` | `/api/{path}/{id}` | — | `200 Dto` / `404` |
| `CREATE` | `POST` | `/api/{path}` | `@Valid RequestDto` | `201 Dto` / `400` |
| `UPDATE` | `PUT` | `/api/{path}/{id}` | `@Valid RequestDto` | `200 Dto` / `400` / `404` |
| `DELETE` | `DELETE` | `/api/{path}/{id}` | — | `204` / `404` |

### Dependencies injected into the controller

| Field | Type | When present |
|---|---|---|
| `repository` | `<Entity>Repository` | always |
| `mapper` | `<Entity>Mapper` | always |
| `entityManager` | `jakarta.persistence.EntityManager` | only when `CREATE` or `UPDATE` is enabled and the entity has non-ignored single relations |

### `SerializationContext` usage

Before returning a response, the controller sets a `ThreadLocal` mode flag:
- `SerializationContext.set(LIST)` — for `findAll` (IDs for relations)
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

**`AuthType.NONE`:**
```java
http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
// no auth mechanism configured
```

**`AuthType.BASIC` or `AuthType.OAUTH2`** (with separate read/write roles):
```java
http.authorizeHttpRequests(auth -> auth
    .requestMatchers(GET,    "/api/products")      .hasAnyRole("CUSTOMER", "ADMIN")
    .requestMatchers(GET,    "/api/products/**")   .hasAnyRole("CUSTOMER", "ADMIN")
    .requestMatchers(POST,   "/api/products")      .hasAnyRole("ADMIN")
    .requestMatchers(PUT,    "/api/products/**")   .hasAnyRole("ADMIN")
    .requestMatchers(DELETE, "/api/products/**")   .hasAnyRole("ADMIN")
    .anyRequest().denyAll()
);
http.httpBasic(withDefaults());           // BASIC
// or
http.oauth2ResourceServer(o -> o.jwt(withDefaults()));  // OAUTH2
```

CSRF is disabled — REST APIs are stateless and do not use cookie-based sessions.

---

## Error responses from generated controllers

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

