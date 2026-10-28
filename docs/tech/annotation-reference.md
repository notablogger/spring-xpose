# Annotation Reference — spring-xpose

spring-xpose provides **two complementary class-level annotations** for compile-time REST API generation, plus one field-level annotation:

| Annotation | Target | Persistence | Use when |
|---|---|---|---|
| `@ExposeEntity` | JPA entity class (`@Entity`) | always JPA | SQL databases — Postgres, MySQL, H2, … |
| `@ExposeDocument` | MongoDB document class (`@Document`) | always MongoDB | MongoDB document collections |
| `@ExposeField` | field of either | both | hiding a field from the API, filters for the search endpoint — see [hidden](#exposefieldhidden--true--hiding-fields-from-the-api), [search filters](#search--post-apipathsearch) |

The annotation you choose determines the persistence backend — there is no `store` parameter to set.
`@ExposeDocument` additionally omits the JPA-specific `relationMode` attribute (MongoDB documents do not have JPA relations).

---

## `@ExposeEntity` — Full example

```java
@Entity
@ExposeEntity(
    path          = "orders",
    expose        = {Operation.FIND_ALL, Operation.FIND_BY_ID,
                     Operation.CREATE, Operation.UPDATE, Operation.DELETE,
                     Operation.SEARCH},                 // SEARCH is opt-in: POST /api/orders/search
    relationMode  = RelationMode.IDS_FOR_LIST_OBJECT_FOR_SINGLE,
    authType      = AuthType.BASIC,
    readRoles     = {"CUSTOMER", "ADMIN"},
    writeRoles    = {"ADMIN"},
    customMapper  = MyOrderMapper.class,             // optional — omit for MapStruct auto-impl
    validator     = OrderValidator.class             // optional — Spring Validator run on create/update
)
public class Order {
    @ExposeField(filterOperations = {FilterOperation.EQ, FilterOperation.IN})
    private String status;                                       // {"status": "NEW"}  {"status_in": ["NEW", "PAID"]}
    @ExposeField(hidden = true) private String internalNote;   // hidden from both response AND request DTO
    @ExposeField(hidden = true) private String auditTrail;
    ...
}
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
| `expose` | `Operation[]` | the five CRUD operations | Which HTTP operations to generate. `SEARCH` is not a default — list it explicitly |
| `authType` | `AuthType` | `NONE` | Authentication mechanism |
| `roles` | `String[]` | `{}` | Roles required for all operations |
| `readRoles` | `String[]` | `{}` | Roles for read requests — `GET` and `POST /search` (overrides `roles`) |
| `writeRoles` | `String[]` | `{}` | Roles for `POST` (create) / `PUT` / `DELETE` (overrides `roles`) |
| `pageable` | `boolean` | `false` | When `true`, `findAll` and `search` accept `Pageable` and return `Page<Dto>` |
| `customMapper` | `Class<?>` | `void.class` | Optional custom Spring bean to use instead of the MapStruct-generated mapper |
| `validator` | `Class<?>` | `void.class` | Optional Spring `Validator` bean run on create and update — see [Custom Validation](#custom-validation-optional) |

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

| Value | HTTP Method | Path | Response | Default |
|---|---|---|---|---|
| `FIND_ALL` | `GET` | `/api/{path}` | `200 OK` — every record: `List<EntityDto>`, or `Page<EntityDto>` with `pageable = true` | ✅ |
| `FIND_BY_ID` | `GET` | `/api/{path}/{id}` | `200 OK` / `404 Not Found` | ✅ |
| `CREATE` | `POST` | `/api/{path}` | `201 Created` — `@RequestBody` is `EntityRequestDto` | ✅ |
| `UPDATE` | `PUT` | `/api/{path}/{id}` | `200 OK` / `404 Not Found` — `@RequestBody` is `EntityRequestDto` | ✅ |
| `DELETE` | `DELETE` | `/api/{path}/{id}` | `204 No Content` / `404 Not Found` | ✅ |
| `SEARCH` | `POST` | `/api/{path}/search` | `200 OK` — records matching the JSON body (`EntitySearchRequest`), `List` or `Page` like `FIND_ALL`; `400` for an unknown key or bad value. A read: secured with `readRoles` | ❌ opt-in |

To filter, expose `SEARCH` and declare filters on fields — see [Search filters](#search--post-apipathsearch).

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

## `@ExposeField(hidden = true)` — Hiding fields from the API

Fields marked `@ExposeField(hidden = true)` are **excluded from both the response DTO and the request DTO**. The entity retains them for persistence — they are never exposed in API responses and cannot be set via the API.

```java
@ExposeEntity(path = "users")
public class User {
    @Id private Long id;
    private String email;

    @ExposeField(hidden = true)
    private String passwordHash;   // stored in DB, never returned or accepted by the API

    @ExposeField(hidden = true)
    private String internalScore;  // same
}
```

`UserDto` contains `id` + `email`. `UserRequestDto` contains `email` only (no `id`, no hidden fields).

- Works on scalar fields, JPA relations (`@ManyToOne @ExposeField(hidden = true) Category category` drops `categoryId` / `category` from both DTOs) and MongoDB document fields.
- Has no effect on the `@Id` field, which is always returned.
- **A hidden field cannot have `filterOperations`** — that is a compile error, because a filter would let clients guess the value the API hides (`{"passwordHash": "…"}` returning rows or not). Sending a hidden field's name as a search key is rejected like any unknown key.

### Migrating from `ignoredFields`

`ignoredFields` on `@ExposeEntity` / `@ExposeDocument` has been **removed**; mark each field instead. This is a breaking change — code still using `ignoredFields` no longer compiles.

```java
// before
@ExposeEntity(path = "users", ignoredFields = {"passwordHash"})
public class User { private String passwordHash; ... }

// after
@ExposeEntity(path = "users")
public class User { @ExposeField(hidden = true) private String passwordHash; ... }
```

---

## Search — `POST /api/<path>/search`

List `Operation.SEARCH` in `expose` and spring-xpose generates **`POST /api/<path>/search`**: a read-only endpoint that returns the records matching a JSON body of filters. Which filters a field accepts is declared on the field with `@ExposeField(filterOperations = …)`. It works on both `@ExposeEntity` (JPA) and `@ExposeDocument` (MongoDB) classes.

| Attribute | Type | Default | Description |
|---|---|---|---|
| `filterOperations` | `FilterOperation[]` | `{}` | Operations exposed as search keys. Empty means the field is not filterable. |
| `hidden` | `boolean` | `false` | Leave the field out of both DTOs — see [Hiding fields](#exposefieldhidden--true--hiding-fields-from-the-api). Cannot be combined with `filterOperations`. |

### Declaring filters

```java
import static io.github.notablogger.springxpose.annotation.FilterOperation.*;

@Entity
@ExposeEntity(path = "products", pageable = true,
              expose = {Operation.FIND_ALL, Operation.FIND_BY_ID, Operation.CREATE,
                        Operation.UPDATE, Operation.DELETE, Operation.SEARCH})
public class Product {
    @Id @GeneratedValue private Long id;

    @ExposeField(filterOperations = {EQ, NE, LIKE})
    private String name;            // "name"  "name_ne"  "name_like"

    @ExposeField(filterOperations = {GT, GTE, LT, LTE})
    private BigDecimal price;       // "price_gt"  "price_gte"  "price_lt"  "price_lte"

    @ExposeField(filterOperations = {IN, NOT_IN})
    private Status status;          // "status_in": ["ACTIVE", "PENDING"]  "status_not_in": ["DELETED"]

    @ExposeField(filterOperations = IS_NULL)
    private Instant deletedAt;      // "deletedAt_is_null": true

    private String description;     // no @ExposeField → not filterable
}
```

```http
POST /api/products/search?page=0&size=20&sort=price,desc
Content-Type: application/json

{ "price_gt": 10, "price_lt": 100, "name_like": "widget", "status_in": ["ACTIVE"] }
```

- **Key names:** `EQ` uses the field name; every other operation uses `<field>_<operation>` in lower case (`price_gt`, `status_not_in`, `deletedAt_is_null`).
- **All keys are optional.** A key that is not sent, or sent as `null`, adds no condition. `{}` — or no body at all — returns every record.
- **Keys are AND-ed.** There is no OR.
- **Paging and sorting** (`pageable = true`) are query parameters — `page`, `size`, `sort` — as on `GET /api/<path>`; the response is a `Page`. Without `pageable` the response is a list.
- **It is a read.** The endpoint changes nothing, is safe to retry, and is secured with `readRoles` (or `roles`), not `writeRoles`. See [why it is a POST](#why-search-is-a-post).

### Operations

| `FilterOperation` | Search key | Matches | JPA | MongoDB | Allowed field types |
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

### Values

Values are JSON, read by Jackson into the field's Java type:

| Field / operation | JSON | Example |
|---|---|---|
| Numbers | number (a numeric string also works) | `"price_gt": 9.99` |
| `String` | string; `EQ`, `NE`, `IN`, `NOT_IN` are **case-sensitive** | `"name": "Widget"` |
| Enums | constant name | `"status": "ACTIVE"` |
| `LocalDate` | ISO-8601 date string | `"launched_gte": "2026-01-15"` |
| `Instant` | ISO-8601 instant string | `"createdAt_gt": "2026-01-15T00:00:00Z"` |
| `IN` / `NOT_IN` | array — a single value is a one-element array | `"status_in": ["A", "B"]` |
| `IS_NULL` | `true` / `false` | `"deletedAt_is_null": false` |
| `LIKE` | any text; `%`, `_` and regex characters are matched literally | `"name_like": "50%"` does not match `500` |

- **An empty `IN` / `NOT_IN`** (`"status_in": []`) adds no condition.
- **Errors return `400 Bad Request` before any query runs**, as an RFC 9457 problem (`urn:springxpose:malformed-body`) with the failing key in `errors`:

  | Request body | `errors` |
  |---|---|
  | `{"price_gt": "cheap"}` | `"price_gt": "Cannot deserialize value of type \`java.math.BigDecimal\` from String \"cheap\": not a valid representation"` |
  | `{"launched_gte": "15/01/2026"}` | `"launched_gte": "Text '15/01/2026' could not be parsed at index 0"` |
  | `{"categoryId_in": [1, "x"]}` | `"categoryId_in[1]": "Cannot deserialize value of type \`java.lang.Long\` …"` |
  | `{"pirce_gt": 10}` (typo) | `"pirce_gt": "Unknown search key 'pirce_gt' for Product — allowed keys: name, name_ne, …"` |
  | `{"stock": 5}` (operation not declared) | `"stock": "Unknown search key 'stock' …"` |
  | `{"status_in": "ACTIVE"}` (not an array) | `"status_in": "Cannot deserialize value of type \`java.util.ArrayList<…>\` from String value …"` |

- **Unknown keys are rejected,** whatever the application's Jackson `FAIL_ON_UNKNOWN_PROPERTIES` setting: a typo, the Java property name (`priceGt` is not `price_gt`), an operation the field does not declare, or a hidden field. A misspelled filter therefore never silently returns every record.

### Collection fields

On a collection field — a JPA `@ElementCollection` or a MongoDB array — `IN`, `NOT_IN` and `IS_NULL` work on the elements:

```java
@ElementCollection
@ExposeField(filterOperations = {IN, NOT_IN, IS_NULL})
private List<String> tags;    // "tags_in": ["sale", "new"]  "tags_not_in": ["clearance"]  "tags_is_null": true
```

| Operation | Meaning | JPA | MongoDB |
|---|---|---|---|
| `IN` | has **any** of the values | `EXISTS` subquery on the collection | `in` |
| `NOT_IN` | has **none** of the values (empty collections match) | `NOT EXISTS` | `nin` |
| `IS_NULL` | `true`: null or empty, `false`: has elements | `isEmpty` / `isNotEmpty` | `tags.0` does not exist / exists |

- The key takes an array of the element type (`List<Integer>` for `Set<Integer>`).
- JPA uses a subquery rather than a join, so a row matching several values is returned once and paging totals stay correct.
- **JPA collections must be `@ElementCollection`.** Without it the list is stored in a single column — Hibernate 6 maps an unannotated `List<String>` to a native array column, and converters or JSON mappings store text — and standard JPA queries cannot look inside that column.
- **Arrays** (`String[]`) support only `IS_NULL`; declare the field as a `List` to filter its elements.

### Relation filters

`@ExposeField` on a JPA relation filters by the **related entity's id**:

```java
@ManyToOne
@ExposeField(filterOperations = {EQ, NE, IN, NOT_IN, IS_NULL})
private Category category;          // "categoryId": 5  "categoryId_in": [5, 7]  "categoryId_is_null": true

@ManyToMany
@ExposeField(filterOperations = {IN, NOT_IN, IS_NULL})
private Set<Supplier> suppliers;    // "suppliers_in": [1, 2]  "suppliers_not_in": [3]  "suppliers_is_null": true
```

| Relation | Key names | Operations | Meaning | JPA |
|---|---|---|---|---|
| `@ManyToOne`, owning `@OneToOne` | `categoryId`, `categoryId_ne`, `categoryId_in`, `categoryId_not_in`, `categoryId_is_null` | `EQ`, `NE`, `IN`, `NOT_IN`, `IS_NULL` | compare the related id; `NE` / `NOT_IN` include rows with no related entity | `root.get("category").get("id")` — the foreign-key column, no join |
| `@OneToMany`, `@ManyToMany` | `suppliers_in`, `suppliers_not_in`, `suppliers_is_null` | `IN`, `NOT_IN`, `IS_NULL` | has **any** / **none** of the ids (none includes empty); `IS_NULL`: no related entities | `EXISTS` subquery, like collection fields — no duplicate rows, correct paging totals |

- **To-one keys are named `<field>Id`** — the same name as the id in the request DTO and in `ALWAYS_IDS` / list responses. To-many keys keep the field name, like collection fields.
- **The id type comes from the related entity's `@Id`** (`Long`, `UUID`, …), including an `@Id` inherited from a `@MappedSuperclass`. A value of the wrong type returns `400`.
- **Not supported:** ranges and `LIKE` (ids are not ordered or text), `EQ` / `NE` on to-many relations, the inverse side of a `@OneToOne` (`mappedBy` — it has no foreign key; filter on the owning side), related entities with a composite id, and filtering by a related entity's *attributes* (`"category_name": "Books"` — see the [workaround](#workaround-filtering-by-a-related-entitys-attributes)).
- MongoDB documents have no relations in spring-xpose.

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
| Range or `LIKE` on a relation; `EQ` / `NE` on a to-many relation | `GT is not supported on relation 'category' — relation filters compare ids: use EQ, NE, IN, NOT_IN or IS_NULL` |
| Filter on the inverse side of a `@OneToOne` (`mappedBy`) | `relation field 'passport' is the inverse side of a @OneToOne (mappedBy) — it has no foreign key to filter on; filter on the owning side instead` |
| Related entity without a single `@Id` field (e.g. `@EmbeddedId`) | `relation field 'category' cannot be filtered — no single @Id field found on … (composite ids are not supported)` |
| `hidden = true` together with `filterOperations` | `hidden field 'secret' cannot have filterOperations — a filter would let clients guess the value the API hides` |
| Two filters mapping to the same key or property, e.g. `price` + `GT` next to a field `priceGt` + `EQ` | `filter 'priceGt EQ' on Product clashes with 'price GT' — …` |

Two mismatches are **warnings**, not errors — the build succeeds:

| Declaration | Warning | Effect |
|---|---|---|
| `filterOperations` on a class whose `expose` does not list `SEARCH` | `Product declares @ExposeField filterOperations but @ExposeEntity does not expose Operation.SEARCH — the filters are not used` | no search endpoint; the filters do nothing |
| `SEARCH` in `expose` but no field declares `filterOperations` | `Product exposes Operation.SEARCH but no field declares @ExposeField(filterOperations = ...) — the search endpoint returns every record` | `POST /search` accepts only `{}` and returns every record |

`@ExposeField` without `filterOperations` (or with an empty array) is not filterable; with `hidden = false` as well it has no effect.

### Why search is a POST

Search is a read, so `GET` with query parameters (`?price_gt=10`) would be the textbook REST choice. spring-xpose uses `POST /api/<path>/search` with a JSON body instead:

- **A body is the only good place for structured filters.** `GET` requests cannot reliably carry a body — HTTP gives a `GET` body no meaning, and proxies, caches and client libraries drop or reject it. A body keeps values typed (numbers, booleans, arrays) instead of comma-separated strings.
- **No URL length limit.** Long `IN` lists (`"categoryId_in": [ … 500 ids … ]`) do not run into the 2–8 KB limits of servers and proxies.
- **Unknown keys can be rejected.** A JSON body maps to one generated type, so a typo is a `400`; unknown query parameters are usually ignored, which turns a misspelled filter into "return everything".
- **Room to grow.** Grouped conditions (OR) can be added to the body later without breaking existing requests.
- **It is an established pattern** for search APIs — Elasticsearch's `_search`, Jira's `POST /search` — and Google's API design guide ([AIP-136](https://google.aip.dev/136)) allows custom methods like `:search`.

The trade-offs, compared with a `GET`:

- **No HTTP caching.** Responses to `POST` are not cached by browsers or proxies.
- **No shareable URL.** A search cannot be bookmarked or linked; send the body again instead.
- **Retries need care.** Clients and proxies treat `POST` as unsafe and do not retry it automatically. The generated endpoint *is* safe and idempotent — it only reads — and its OpenAPI description says so.

**Future: the HTTP `QUERY` method.** [RFC 10008](https://www.rfc-editor.org/rfc/rfc10008) (June 2026) defines `QUERY` — a method that is safe and idempotent like `GET`, so it can be cached and retried, but carries a request body like `POST`. It is made for exactly this kind of endpoint. Support is only arriving: Spring Framework recognises `QUERY` from 7.1 ([spring-framework#34993](https://github.com/spring-projects/spring-framework/pull/34993)), while spring-xpose builds on Spring Boot 4.0 / Spring Framework 7.0, and many proxies, API gateways, CDNs and HTTP clients do not handle it yet. Once that support is in place, spring-xpose plans to serve search over `QUERY /api/<path>` as well, with the **same JSON body** — so clients can move over without changing their requests, and `POST /search` stays for clients that cannot send `QUERY`.

### Limitations

- **Relations filter by id only.** Filtering by a related entity's attributes (`"category_name": "Books"`) is not supported — see the workaround below.
- **AND only.** Keys cannot be combined with OR or grouped.
- **`LIKE` is "contains" only** — no prefix/suffix match and no user-supplied wildcards. Because it lower-cases the column (JPA) or uses an unanchored case-insensitive regex (MongoDB), it cannot use a normal index; on large tables add a functional/text index or prefer `EQ`.
- **`EQ` / `IN` on text is case-sensitive.**
- **Collections:** only `IN`, `NOT_IN` and `IS_NULL`; no "has all of". JPA needs `@ElementCollection`.
- **Very long `IN` lists** are passed to the database as-is; some databases limit the number of bind parameters per statement.
- **`POST`, not `GET`** — see [Why search is a POST](#why-search-is-a-post) for what that means for caching and links.
- **MongoDB field names** in queries are the Java property names; Spring Data maps them to `@Field` names as usual.

> ⚠️ **Filters reveal values.** Any filterable field lets clients test values, even if they never read the field. Hidden fields therefore cannot be filtered (compile error); for other fields, only add filters where clients may learn the values.

### Workaround: filtering by a related entity's attributes

Write that query yourself in a small controller next to the generated one, and reuse the generated mapper (and `Spec`, if you want the declared filters too):

```java
public interface ProductQueries extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {}

@RestController
@RequestMapping("/api/product-queries")
class ProductQueryController {
    private final ProductQueries products;
    private final ProductMapper mapper;     // generated mapper

    ProductQueryController(ProductQueries products, ProductMapper mapper) {
        this.products = products; this.mapper = mapper;
    }

    /** The generated filters plus one condition on the category's name. */
    @PostMapping("/by-category-name")
    List<ProductDto> search(@RequestParam String categoryName,
                            @RequestBody(required = false) ProductSearchRequest request) {
        Specification<Product> byName = (root, q, cb) -> cb.equal(root.get("category").get("name"), categoryName);
        SerializationContext.set(SerializationContext.Mode.LIST);   // same JSON shape as generated endpoints
        try {
            return mapper.toDtoList(products.findAll(
                ProductSpec.withFilters(request != null ? request : new ProductSearchRequest()).and(byName)));
        } finally {
            SerializationContext.clear();
        }
    }
}
```

Use a path outside `/api/<path>/**`: the generated security chain for `/api/products/**` allows only the generated endpoints and denies every other request, so secure your own path with your own `SecurityFilterChain` (or the application's default one).

### Generated code and OpenAPI

- `<Entity>SearchRequest` — the JSON body: one property per (field, operation) with `@JsonProperty("price_gt")` (the key) and `@Schema(description = …)` (OpenAPI), plus a `@JsonAnySetter` that rejects unknown keys. Primitives are boxed, `IN` / `NOT_IN` are `List`s, `IS_NULL` is `Boolean`.
- `<Entity>Spec` — `withFilters(SearchRequest)` returning a JPA `Specification` or a MongoDB `Query`.
- Controller method `search` — `@PostMapping("/search")` with `@RequestBody(required = false)`, plus `Pageable` when `pageable = true`.
- JPA repositories also extend `JpaSpecificationExecutor<Entity>`; MongoDB controllers get a `MongoTemplate`.
- Security — `POST /api/<path>/search` gets the read rule (`readRoles`, `roles`, `authenticated()` or `permitAll()`), separate from `POST /api/<path>` (create), which keeps the write rule.
- **OpenAPI:** Swagger UI shows the search body as one JSON schema — every key with its type (`status_in` as an array, `deletedAt_is_null` as a boolean, dates with `format: date`, …) and a description — and one example, `example`, that lists **every search key with a placeholder of its type** (`"string"`, `0`, `true`, the first enum constant, `"2026-01-01"` for dates, one-element arrays for `_in` / `_not_in`, `true` for `_is_null`). Every placeholder has the right type, so the example can be sent as is, but all keys are AND-ed, so it rarely matches a record: remove the keys you don't need (`{}` returns everything). `page` / `size` / `sort` appear as optional query parameters.

  ```json
  { "name": "string", "name_ne": "string", "name_like": "string",
    "price_gt": 0, "price_gte": 0, "price_lt": 0, "price_lte": 0,
    "status_in": ["ACTIVE"], "status_not_in": ["ACTIVE"], "deletedAt_is_null": true }
  ```

Details and generated source examples: [`generated-artifacts.md`](generated-artifacts.md#7-entitysearchrequest).

---

## Request DTO

For entities with `CREATE` or `UPDATE` operations, spring-xpose generates a **`<Entity>RequestDto`** as the `@RequestBody` type. This cleanly separates what clients _send_ from what the API _returns_.

| | `<Entity>Dto` (response) | `<Entity>RequestDto` (request body) |
|---|---|---|
| `id` field | ✅ included | ❌ excluded — ID comes from the path variable |
| Hidden fields (`@ExposeField(hidden = true)`) | ❌ excluded | ❌ excluded |
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

---

## Custom Validation (Optional)

Annotations like `@NotBlank` and `@Size` are copied to the request DTO and checked before the controller runs. For rules they can't express — cross-field checks, or lookups against a database or another service — set `validator` to a Spring `org.springframework.validation.Validator` bean.

### How it works

1. The generated controller gets your validator through its constructor (so it must be a Spring bean, e.g. `@Component`).
2. On `CREATE` and `UPDATE` the controller maps the request to the entity, resolves relations, then calls `validator.validate(entity, errors)` **before saving**. On update the validator sees the loaded entity with the request already merged in.
3. If the validator rejects anything, the controller throws a `BindException`, which `SpringXposeExceptionHandler` turns into the same `400` problem as bean validation.
4. At startup the controller calls `validator.supports(Entity.class)` and fails fast with an `IllegalStateException` if it returns `false`.
5. The build fails if the class does not implement `org.springframework.validation.Validator`.

Bean validation runs first: if `@NotBlank` fails, the custom validator is not called.

### Example

```java
@Component
public class OrderValidator implements Validator {

    private final CustomerRepository customers;

    public OrderValidator(CustomerRepository customers) { this.customers = customers; }

    @Override
    public boolean supports(Class<?> type) { return Order.class.equals(type); }

    @Override
    public void validate(Object target, Errors errors) {
        Order order = (Order) target;
        if (order.getShipDate() != null && order.getShipDate().isBefore(order.getOrderDate())) {
            errors.rejectValue("shipDate", "order.shipDate.before", "must not be before orderDate");
        }
        if (order.getTotal() != null && order.getTotal().signum() > 0 && order.getLines().isEmpty()) {
            errors.reject("order.empty", "an order with a total needs at least one line");
        }
    }
}

@Entity
@ExposeEntity(path = "orders", validator = OrderValidator.class)
public class Order { ... }
```

Response to `POST /api/orders` with a bad ship date:

```json
{
  "type": "urn:springxpose:validation-error",
  "title": "Validation failed",
  "status": 400,
  "detail": "One or more fields failed validation.",
  "errors": {
    "shipDate": "must not be before orderDate",
    "_global": "an order with a total needs at least one line"
  }
}
```

- `errors.rejectValue("field", code, message)` is keyed by the field name. `errors.reject(code, message)` (not tied to a field) is listed under `_global`. Pass a default message — codes are not resolved against a `MessageSource`.
- Several errors on one key are joined with `; `.
- The validator sees the **entity**, not the request DTO, so it can also check fields hidden with `@ExposeField(hidden = true)`; a rejection on a hidden field still shows its name and message in `errors`.
- Errors are read with direct field access, so entities without getters work.
- `validator` has no effect when neither `CREATE` nor `UPDATE` is exposed.
- On JPA, `UPDATE` is transactional and the loaded entity is already modified when the validator runs. Rejecting rolls the change back, but a validator should only read — never save or modify the entity.

### OpenAPI

`CREATE` and `UPDATE` document their `400` response with the `ProblemDetail` schema (`application/problem+json`) and an example including the `errors` map; `SEARCH` documents its `400` the same way with the malformed-body example.

