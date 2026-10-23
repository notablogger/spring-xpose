# Annotation Reference — spring-xpose

spring-xpose provides **two complementary class-level annotations** for compile-time REST API generation, plus one field-level annotation:

| Annotation | Target | Persistence | Use when |
|---|---|---|---|
| `@ExposeEntity` | JPA entity class (`@Entity`) | always JPA | SQL databases — Postgres, MySQL, H2, … |
| `@ExposeDocument` | MongoDB document class (`@Document`) | always MongoDB | MongoDB document collections |
| `@ExposeField` | field of either | both | query filters on `findAll` — see [`@ExposeField`](#exposefield--filtering-findall) |

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

## `@ExposeField` — Filtering `findAll`

`@ExposeField` is a **field-level** annotation. Its `filterOperations` attribute lists the filter operations a field accepts as optional query parameters on the generated `GET /api/<path>` (findAll) endpoint. It works on both `@ExposeEntity` (JPA) and `@ExposeDocument` (MongoDB) classes.

| Attribute | Type | Default | Description |
|---|---|---|---|
| `filterOperations` | `FilterOperation[]` | `{}` | Operations exposed as query parameters. Empty means the field is not filterable. |

### Declaring filters

```java
import static io.github.notablogger.springxpose.annotation.FilterOperation.*;

@Entity
@ExposeEntity(path = "products", pageable = true)
public class Product {
    @Id @GeneratedValue private Long id;

    @ExposeField(filterOperations = {EQ, NE, LIKE})
    private String name;            // ?name=  ?name_ne=  ?name_like=

    @ExposeField(filterOperations = {GT, GTE, LT, LTE})
    private BigDecimal price;       // ?price_gt=  ?price_gte=  ?price_lt=  ?price_lte=

    @ExposeField(filterOperations = {IN, NOT_IN})
    private Status status;          // ?status_in=ACTIVE,PENDING  ?status_not_in=DELETED

    @ExposeField(filterOperations = IS_NULL)
    private Instant deletedAt;      // ?deletedAt_is_null=true

    private String description;     // no @ExposeField → not filterable
}
```

```
GET /api/products?price_gt=10&price_lt=100&name_like=widget&status_in=ACTIVE&page=0&size=20&sort=price,desc
```

- **Parameter names:** `EQ` uses the field name; every other operation uses `<field>_<operation>` in lower case (`price_gt`, `status_not_in`, `deletedAt_is_null`).
- **All filters are optional.** A parameter that is not sent adds no condition; no parameters returns everything, exactly as before.
- **Filters are AND-ed.** There is no OR.
- **Works with paging and sorting** (`pageable = true`): filters, `page`, `size` and `sort` combine.

### Operations

| `FilterOperation` | Query param | Matches | JPA | MongoDB | Allowed field types |
|---|---|---|---|---|---|
| `EQ` | `price` | equal | `=` | `is` | any scalar |
| `NE` | `price_ne` | not equal, **null included** | `<> OR IS NULL` | `ne` | any scalar |
| `GT` | `price_gt` | greater than | `>` | `gt` | primitive or `Comparable` |
| `GTE` | `price_gte` | greater than or equal | `>=` | `gte` | primitive or `Comparable` |
| `LT` | `price_lt` | less than | `<` | `lt` | primitive or `Comparable` |
| `LTE` | `price_lte` | less than or equal | `<=` | `lte` | primitive or `Comparable` |
| `LIKE` | `name_like` | contains, case-insensitive | `lower(name) LIKE '%value%'` | regex, `i` option | `String` |
| `IN` | `status_in` | equal to any of the values | `IN (…)` | `in` | any scalar |
| `NOT_IN` | `status_not_in` | equal to none of the values, **null included** | `NOT IN (…) OR IS NULL` | `nin` | any scalar |
| `IS_NULL` | `deletedAt_is_null` | `true`: null, `false`: set | `IS NULL` / `IS NOT NULL` | `is(null)` / `ne(null)` | any non-primitive |

"Comparable" covers numbers, `String`, enums and `java.time` types. On collection fields only `IN`, `NOT_IN` and `IS_NULL` are allowed and mean something different — see [Collection fields](#collection-fields).

### Parameter values

| Field / operation | Format | Example |
|---|---|---|
| Numbers | plain number | `?price_gt=9.99` |
| `String` | as is; `EQ`, `NE`, `IN`, `NOT_IN` are **case-sensitive** | `?name=Widget` |
| Enums | constant name | `?status=ACTIVE` |
| `LocalDate` | ISO-8601 date | `?launched_gte=2026-01-15` |
| `Instant` | ISO-8601 instant | `?createdAt_gt=2026-01-15T00:00:00Z` |
| `IN` / `NOT_IN` | comma-separated, or the parameter repeated | `?status_in=A,B` = `?status_in=A&status_in=B` |
| `IS_NULL` | `true` / `false` | `?deletedAt_is_null=false` |
| `LIKE` | any text; `%`, `_` and regex characters are matched literally | `?name_like=50%` does not match `500` |

- **A value that cannot be converted** (`?price_gt=abc`, `?deletedAt_is_null=maybe`, `?launched=15.01.2026`) is rejected with `400 Bad Request` before any query runs.
- **An empty `IN` / `NOT_IN`** (`?status_in=`) adds no condition.
- **Unknown parameters are ignored**, including typos and the Java property name (`?priceGt=10` is not `?price_gt=10`).

### Collection fields

On a collection field — a JPA `@ElementCollection` or a MongoDB array — `IN`, `NOT_IN` and `IS_NULL` work on the elements:

```java
@ElementCollection
@ExposeField(filterOperations = {IN, NOT_IN, IS_NULL})
private List<String> tags;    // ?tags_in=sale,new  ?tags_not_in=clearance  ?tags_is_null=true
```

| Operation | Meaning | JPA | MongoDB |
|---|---|---|---|
| `IN` | has **any** of the values | `EXISTS` subquery on the collection | `in` |
| `NOT_IN` | has **none** of the values (empty collections match) | `NOT EXISTS` | `nin` |
| `IS_NULL` | `true`: null or empty, `false`: has elements | `isEmpty` / `isNotEmpty` | `tags.0` does not exist / exists |

- A single value is `?tags_in=sale`. The parameter binds to a `List` of the element type (`List<Integer>` for `Set<Integer>`).
- JPA uses a subquery rather than a join, so a row matching several values is returned once and paging totals stay correct.
- **JPA collections must be `@ElementCollection`.** Without it the list is stored in a single column — Hibernate 6 maps an unannotated `List<String>` to a native array column, and converters or JSON mappings store text — and standard JPA queries cannot look inside that column.
- **Arrays** (`String[]`) support only `IS_NULL`; declare the field as a `List` to filter its elements.

### Compile-time validation

Mistakes fail the build with an error on the offending field instead of producing an endpoint that breaks at runtime:

| Declaration | Error |
|---|---|
| `GT` / `GTE` / `LT` / `LTE` on a non-comparable type (`Map`, a custom class) | `GT is not supported on 'x' … requires a primitive or Comparable type` |
| `LIKE` on a non-`String` field | `… LIKE requires a String field` |
| `IS_NULL` on a primitive (`int`, `boolean`, …) | `… IS_NULL cannot apply to a primitive field, which is never null` |
| `EQ`, `NE`, ranges or `LIKE` on a collection | `… EQ does not apply to a collection — use IN (has any of) or NOT_IN (has none of)` |
| Filter on a JPA collection without `@ElementCollection` | `collection field 'tags' must be an @ElementCollection to be filtered — …` |
| Filter on a raw collection (`List` without a type argument) or `List<?>` | `collection field 'tags' needs a concrete element type, e.g. List<String>` |
| Anything but `IS_NULL` on an array | `… does not apply to an array — declare the field as a List to use IN / NOT_IN` |
| `@ExposeField` on a JPA relation (`@ManyToOne`, `@OneToOne`, `@OneToMany`, `@ManyToMany`) | `relation field 'category' cannot be filtered — only scalar fields support filter operations` |
| Two filters mapping to the same parameter or property, e.g. `price` + `GT` next to a field `priceGt` + `EQ` | `filter 'priceGt EQ' on Product clashes with 'price GT' — …` |

`@ExposeField` without `filterOperations` (or with an empty array) is allowed and has no effect.

### Limitations

- **No relation filters.** Filtering by a related entity (`?category=5`) or its attributes (`?category_name=Books`) is not supported yet — see the workaround below.
- **AND only.** Filters cannot be combined with OR or grouped.
- **`LIKE` is "contains" only** — no prefix/suffix match and no user-supplied wildcards. Because it lower-cases the column (JPA) or uses an unanchored case-insensitive regex (MongoDB), it cannot use a normal index; on large tables add a functional/text index or prefer `EQ`.
- **`EQ` / `IN` on text is case-sensitive.**
- **Collections:** only `IN`, `NOT_IN` and `IS_NULL`; no "has all of". JPA needs `@ElementCollection`.
- **Very long `IN` lists** are passed to the database as-is; some databases limit the number of bind parameters per statement.
- **Filters apply to `findAll` only** — not to `findById`, update or delete. If `FIND_ALL` is not in `expose`, the filter classes are still generated but unused.
- **Unknown or misspelled parameters are silently ignored** rather than rejected.
- **MongoDB field names** in queries are the Java property names; Spring Data maps them to `@Field` names as usual.

> ⚠️ **Hidden fields stay filterable.** A field listed in `ignoredFields` never appears in responses, but if it also has `@ExposeField`, clients can still filter on it — and so guess its value (`?secret=wrong` returns nothing, `?secret=s3cret` returns rows). Only expose filters on fields whose values clients may learn.

### Workaround: filtering on a relation today

Leave `FIND_ALL` out of `expose` and write that one endpoint yourself at the same path; the generated security configuration for `/api/<path>/**` still applies:

```java
@ExposeEntity(path = "products",
              expose = {Operation.FIND_BY_ID, Operation.CREATE, Operation.UPDATE, Operation.DELETE})
public class Product { ... @ManyToOne private Category category; ... }

public interface ProductQueries extends JpaRepository<Product, Long> {
    List<Product> findByCategoryId(Long categoryId);
}

@RestController
@RequestMapping("/api/products")
class ProductListController {
    private final ProductQueries products;
    private final ProductMapper mapper;     // generated mapper

    ProductListController(ProductQueries products, ProductMapper mapper) {
        this.products = products; this.mapper = mapper;
    }

    @GetMapping
    List<ProductDto> findAll(@RequestParam(required = false) Long categoryId) {
        SerializationContext.set(SerializationContext.Mode.LIST);   // same JSON shape as generated endpoints
        try {
            return mapper.toDtoList(categoryId == null ? products.findAll()
                                                       : products.findByCategoryId(categoryId));
        } finally {
            SerializationContext.clear();
        }
    }
}
```

If the entity also has `@ExposeField` filters, reuse the generated classes and add your condition:
`ProductSpec.withFilters(filters).and((root, q, cb) -> cb.equal(root.get("category").get("id"), categoryId))`.

### Generated code and OpenAPI

- `<Entity>FilterParams` — one property per (field, operation), bound with `@ModelAttribute`. Suffixed parameters carry `@BindParam("price_gt")` (binding) and `@Parameter(name = "price_gt")` (OpenAPI). Primitives are boxed, `IN` / `NOT_IN` are `List`s, `IS_NULL` is `Boolean`.
- `<Entity>Spec` — `withFilters(FilterParams)` returning a JPA `Specification` or a MongoDB `Query`.
- JPA repositories also extend `JpaSpecificationExecutor<Entity>`; MongoDB controllers get a `MongoTemplate`.
- **OpenAPI:** when springdoc is on the classpath the controller parameter gets `@ParameterObject`, so Swagger UI lists every filter (`price_gt`, `status_in` as an array, `deletedAt_is_null` as a boolean, …) with its type and a description. Without springdoc the filters still work; they are just not documented.

Details and generated source examples: [`generated-artifacts.md`](generated-artifacts.md#7-entityfilterparams).

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

