# `@ExposeEntity` Reference — spring-xpose

## Full example

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

---

## Attributes

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

## `store` — Persistence Store Type

Controls which Spring Data repository base interface is generated and how relations/transactions are handled.

| Value | Repository | EntityManager | @Transactional | Use Case |
|---|---|---|---|---|
| `StoreType.JPA` (default) | `JpaRepository<Entity, Id>` | ✅ injected for relation resolution | ✅ on write operations | Traditional SQL databases (Postgres, MySQL, etc.) |
| `StoreType.MONGO` | `MongoRepository<Entity, Id>` | ❌ not injected | ❌ not emitted | MongoDB / NoSQL document stores |

**Example — MongoDB entity:**

```java
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "notes")
@ExposeEntity(path = "notes", store = StoreType.MONGO)
public class Note {
    @Id                   // Use @org.springframework.data.annotation.Id for MongoDB
    private String id;
    
    @NotBlank
    private String title;
    
    private String content;
}
```

When `store = StoreType.MONGO`:
- Generated repository extends `MongoRepository<Note, String>` instead of `JpaRepository`
- Generated controller **does not** inject `EntityManager`
- Generated controller **does not** emit `@Transactional` on write methods
- Relation resolution through `EntityManager` is skipped (N/A for document stores)

---

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

