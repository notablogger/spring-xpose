# Annotation Reference — spring-xpose

spring-xpose provides **two complementary annotations** for compile-time REST API generation:

| Annotation | Target | Persistence | Use when |
|---|---|---|---|
| `@ExposeEntity` | JPA entity class (`@Entity`) | always JPA | SQL databases — Postgres, MySQL, H2, … |
| `@ExposeDocument` | MongoDB document class (`@Document`) | always MongoDB | MongoDB document collections |

The annotation you choose determines the persistence backend — there is no `store` parameter to set.
`@ExposeDocument` additionally omits the JPA-specific `relationMode` attribute (MongoDB documents do not have JPA relations).

---

## `@ExposeEntity` — Full example

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

## `@ExposeDocument` — Full example

```java
@Document(collection = "notes")
@ExposeDocument(
    path          = "notes",
    expose        = {Operation.FIND_ALL, Operation.FIND_BY_ID,
                     Operation.CREATE, Operation.UPDATE, Operation.DELETE},
    authType      = AuthType.BASIC,
    readRoles     = {"CUSTOMER", "ADMIN"},
    writeRoles    = {"ADMIN"},
    ignoredFields = {"internalTag"},
    customMapper  = MyNoteMapper.class    // optional
)
public class Note {
    @Id   // @org.springframework.data.annotation.Id
    private String id;
    @NotBlank private String title;
    private String content;
}
```

---

## Attributes

### Shared attributes (`@ExposeEntity` and `@ExposeDocument`)

| Attribute | Type | Default | Description |
|---|---|---|---|
| `path` | `String` | entity name pluralised | URL segment — `"products"` → `/api/products` |
| `expose` | `Operation[]` | all five | Which HTTP operations to generate |
| `authType` | `AuthType` | `NONE` | Authentication mechanism |
| `roles` | `String[]` | `{}` | Roles required for all operations |
| `readRoles` | `String[]` | `{}` | Roles for GET requests (overrides `roles`) |
| `writeRoles` | `String[]` | `{}` | Roles for POST/PUT/DELETE (overrides `roles`) |
| `ignoredFields` | `String[]` | `{}` | Fields excluded from **both** the response DTO and the request DTO |
| `pageable` | `boolean` | `false` | When `true`, `findAll` accepts `Pageable` and returns `Page<Dto>` |
| `customMapper` | `Class<?>` | `void.class` | Optional custom Spring bean to use instead of the MapStruct-generated mapper |

### `@ExposeEntity`-only attributes

| Attribute | Type | Default | Description |
|---|---|---|---|
| `relationMode` | `RelationMode` | `IDS_FOR_LIST_OBJECT_FOR_SINGLE` | How related entities appear in the **response** DTO |

---

## Persistence behaviour by annotation

| | `@ExposeEntity` | `@ExposeDocument` |
|---|---|---|
| Generated repository | `JpaRepository<Entity, Id>` | `MongoRepository<Entity, Id>` |
| `EntityManager` | ✅ injected for relation resolution | ❌ not injected |
| `@Transactional` on write methods | ✅ emitted | ❌ not emitted |
| JPA relation resolution | ✅ via `EntityManager.getReference()` | ❌ N/A |
| `@jakarta.persistence.Id` | ✅ supported | ✅ supported |
| `@org.springframework.data.annotation.Id` | ✅ supported | ✅ supported |

> **Future expansion:** Other NoSQL databases (DynamoDB, Cassandra, Elasticsearch, etc.) may be supported in future versions via a dedicated annotation.

---

## `expose` — Operations

| Value | HTTP Method | Path | Response |
|---|---|---|---|
| `FIND_ALL` | `GET` | `/api/{path}` | `200 OK` — returns `List<EntityDto>` |
| `FIND_BY_ID` | `GET` | `/api/{path}/{id}` | `200 OK` / `404 Not Found` |
| `CREATE` | `POST` | `/api/{path}` | `201 Created` — `@RequestBody` is `EntityRequestDto` |
| `UPDATE` | `PUT` | `/api/{path}/{id}` | `200 OK` / `404 Not Found` — `@RequestBody` is `EntityRequestDto` |
| `DELETE` | `DELETE` | `/api/{path}/{id}` | `204 No Content` / `404 Not Found` |

---

## `authType` — Authentication

| Value | Generated security | Swagger UI |
|---|---|---|
| `NONE` | `permitAll()` — fully public | No lock |
| `BASIC` | HTTP Basic authentication | 🔒 username / password |
| `OAUTH2` | JWT Bearer token | 🔒 Bearer token |

---

## `relationMode` — Relation serialisation in the DTO

Controls how `@ManyToOne` / `@OneToOne` fields are represented in the generated **response** DTO.  
`@OneToMany` / `@ManyToMany` collection fields are **always excluded** from both DTOs to prevent circular references.  
In the **request** DTO, relations are **always** represented as `Long <field>Id` regardless of `relationMode`.

| Mode | List endpoint (`GET /api/products`) | Single endpoint (`GET /api/products/1`) |
|---|---|---|
| `IDS_FOR_LIST_OBJECT_FOR_SINGLE` | `"categoryId": 3` | `"category": {"id":3,"name":"Books"}` |
| `ALWAYS_IDS` | `"categoryId": 3` | `"categoryId": 3` |
| `ALWAYS_OBJECT` | `"category": {"id":3,...}` | `"category": {"id":3,...}` |

---

## `ignoredFields` — Hiding fields from the API

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

