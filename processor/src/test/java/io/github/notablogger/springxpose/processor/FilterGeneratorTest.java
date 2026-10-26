package io.github.notablogger.springxpose.processor;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Compile-tests for {@code @ExposeField} filters on {@code Operation.SEARCH}: SearchRequest, Spec, repository, controller and security output. */
class FilterGeneratorTest {

    private static final JavaFileObject CATEGORY = JavaFileObjects.forSourceString(
        "com.example.entity.Category",
        """
        package com.example.entity;
        import jakarta.persistence.*;
        @Entity
        public class Category {
            @Id @GeneratedValue private Long id;
            public Long getId() { return id; }
            public void setId(Long id) { this.id = id; }
        }
        """
    );

    /** Every operation on a JPA entity; succeeding compilation also type-checks the generated code. */
    private static final JavaFileObject PRODUCT = product("""
            @NotBlank
            @ExposeField(filterOperations = {FilterOperation.EQ, FilterOperation.NE, FilterOperation.LIKE})
            private String name;

            @ExposeField(filterOperations = {FilterOperation.EQ, FilterOperation.GT, FilterOperation.GTE,
                                       FilterOperation.LT, FilterOperation.LTE})
            private java.math.BigDecimal price;

            @ExposeField(filterOperations = {FilterOperation.GT, FilterOperation.LT})
            private int stock;

            @ExposeField(filterOperations = {FilterOperation.EQ, FilterOperation.IN, FilterOperation.NOT_IN})
            private Status status;

            @ExposeField(filterOperations = FilterOperation.IS_NULL)
            private java.time.Instant deletedAt;

            @ExposeField
            private String sku;

            private String description;

            public enum Status { ACTIVE, DISCONTINUED }
            """);

    private static final JavaFileObject NOTE_DOCUMENT = JavaFileObjects.forSourceString(
        "com.example.document.Note",
        """
        package com.example.document;
        import io.github.notablogger.springxpose.annotation.*;
        import org.springframework.data.annotation.Id;

        @ExposeDocument(path = "notes", expose = {Operation.FIND_ALL, Operation.SEARCH})
        public class Note {
            @Id private String id;

            @ExposeField(filterOperations = {FilterOperation.EQ, FilterOperation.NE, FilterOperation.LIKE,
                                       FilterOperation.IN, FilterOperation.NOT_IN})
            private String author;

            @ExposeField(filterOperations = {FilterOperation.GT, FilterOperation.LTE, FilterOperation.IS_NULL})
            private java.time.Instant createdAt;

            @ExposeField(filterOperations = {FilterOperation.IN, FilterOperation.NOT_IN, FilterOperation.IS_NULL})
            private java.util.List<String> labels;

            public String getId() { return id; }
            public void setId(String id) { this.id = id; }
            public String getAuthor() { return author; }
            public void setAuthor(String author) { this.author = author; }
            public java.util.List<String> getLabels() { return labels; }
            public void setLabels(java.util.List<String> labels) { this.labels = labels; }
            public java.time.Instant getCreatedAt() { return createdAt; }
            public void setCreatedAt(java.time.Instant createdAt) { this.createdAt = createdAt; }
        }
        """
    );

    /** A Product entity exposing the CRUD operations and SEARCH, with the given fields. */
    private static JavaFileObject product(String fields) {
        return product("""
            path = "products", expose = {Operation.FIND_ALL, Operation.FIND_BY_ID, Operation.CREATE,
                                         Operation.UPDATE, Operation.DELETE, Operation.SEARCH}""", fields);
    }

    private static JavaFileObject product(String exposeEntity, String fields) {
        return JavaFileObjects.forSourceString("com.example.entity.Product",
            """
            package com.example.entity;
            import io.github.notablogger.springxpose.annotation.*;
            import jakarta.persistence.*;
            import jakarta.validation.constraints.NotBlank;

            @Entity
            @ExposeEntity(""" + exposeEntity + """
            )
            public class Product {
                @Id @GeneratedValue private Long id;
                public Long getId() { return id; }
                public void setId(Long id) { this.id = id; }
            """ + fields + """
            }
            """
        );
    }

    private static Compilation compile(JavaFileObject... sources) {
        return javac().withProcessors(new ExposeEntityProcessor()).compile(sources);
    }

    private static String source(Compilation c, String className) throws IOException {
        return c.generatedSourceFile(className).orElseThrow().getCharContent(false).toString();
    }

    /** Asserts the SearchRequest maps JSON key {@code key} to the field {@code declaration}. */
    private static void assertKey(String src, String key, String declaration) {
        assertTrue(Pattern.compile("@JsonProperty\\(\"" + Pattern.quote(key) + "\"\\)\\s+@Schema\\(\\s*description = \"[^\"]*\"\\s*\\)\\s+private "
                + Pattern.quote(declaration) + ";").matcher(src).find(),
            "expected key " + key + " → " + declaration + " in:\n" + src);
    }

    // ── SearchRequest ─────────────────────────────────────────────────────────

    @Test
    void searchRequestHasOnePropertyPerOperation() throws Exception {
        Compilation c = compile(PRODUCT);

        assertThat(c).succeeded();
        String src = source(c, "com.example.entity.generated.ProductSearchRequest");
        for (String getter : new String[] {
                "getName()", "getNameNe()", "getNameLike()",
                "getPrice()", "getPriceGt()", "getPriceGte()", "getPriceLt()", "getPriceLte()",
                "getStockGt()", "getStockLt()", "getStatus()", "getStatusIn()", "getStatusNotIn()",
                "getDeletedAtIsNull()"}) {
            assertTrue(src.contains(getter), "SearchRequest must have " + getter);
        }
        assertFalse(src.contains("getSku"), "@ExposeField without operations must not be filterable");
        assertFalse(src.contains("getDescription"), "fields without @ExposeField must not be filterable");
        assertFalse(src.contains("getStock()"), "EQ was not declared on stock");
    }

    @Test
    void suffixedKeysCarryJsonPropertyAndOpenApiDescription() throws Exception {
        Compilation c = compile(PRODUCT);

        assertThat(c).succeeded();
        String src = source(c, "com.example.entity.generated.ProductSearchRequest");
        // Jackson reads "price_gt" into priceGt, and springdoc documents the key, not the Java name
        assertKey(src, "price_gt", "BigDecimal priceGt");
        assertKey(src, "name_like", "String nameLike");
        assertKey(src, "price", "BigDecimal price");
        assertTrue(src.contains("description = \"price greater than\""), src);
    }

    @Test
    void primitiveFieldsAreBoxedSoAbsentParamsAreNull() throws Exception {
        Compilation c = compile(PRODUCT);

        assertThat(c).succeeded();
        String src = source(c, "com.example.entity.generated.ProductSearchRequest");
        assertKey(src, "stock_gt", "Integer stockGt");
    }

    @Test
    void inAndNotInAreListsAndIsNullIsBoolean() throws Exception {
        Compilation c = compile(PRODUCT);

        assertThat(c).succeeded();
        String src = source(c, "com.example.entity.generated.ProductSearchRequest");
        assertKey(src, "status_in", "List<Product.Status> statusIn");
        assertKey(src, "status_not_in", "List<Product.Status> statusNotIn");
        assertKey(src, "deletedAt_is_null", "Boolean deletedAtIsNull");
    }

    // ── JPA Spec ──────────────────────────────────────────────────────────────

    @Test
    void jpaSpecMapsEachOperation() throws Exception {
        Compilation c = compile(PRODUCT);

        assertThat(c).succeeded();
        String src = source(c, "com.example.entity.generated.ProductSpec");
        assertTrue(src.contains("cb.equal(root.get(\"price\"), f.getPrice())"), src);
        assertTrue(src.contains("cb.or(cb.notEqual(root.get(\"name\"), f.getNameNe()), cb.isNull(root.get(\"name\")))"), src);
        assertTrue(src.contains("cb.greaterThan(root.<BigDecimal>get(\"price\"), f.getPriceGt())"), src);
        assertTrue(src.contains("cb.greaterThanOrEqualTo(root.<BigDecimal>get(\"price\"), f.getPriceGte())"), src);
        assertTrue(src.contains("cb.lessThan(root.<BigDecimal>get(\"price\"), f.getPriceLt())"), src);
        assertTrue(src.contains("cb.lessThanOrEqualTo(root.<BigDecimal>get(\"price\"), f.getPriceLte())"), src);
        assertTrue(src.contains("cb.greaterThan(root.<Integer>get(\"stock\"), f.getStockGt())"), src);
        assertTrue(src.contains("cb.like(cb.lower(root.<String>get(\"name\")), likePattern(f.getNameLike()), '\\\\')"), src);
        assertTrue(src.contains("root.get(\"status\").in(f.getStatusIn())"), src);
        assertTrue(src.contains("cb.or(cb.not(root.get(\"status\").in(f.getStatusNotIn())), cb.isNull(root.get(\"status\")))"), src);
        assertTrue(src.contains("f.getDeletedAtIsNull() ? cb.isNull(root.get(\"deletedAt\")) : cb.isNotNull(root.get(\"deletedAt\"))"), src);
    }

    @Test
    void emptyInListAddsNoCondition() throws Exception {
        Compilation c = compile(PRODUCT);

        assertThat(c).succeeded();
        String src = source(c, "com.example.entity.generated.ProductSpec");
        assertTrue(src.contains("if (f.getStatusIn() != null && !f.getStatusIn().isEmpty())"), src);
        assertTrue(src.contains("if (f.getStatusNotIn() != null && !f.getStatusNotIn().isEmpty())"), src);
    }

    @Test
    void likePatternIsCaseInsensitiveContainsWithEscapedWildcards() throws Exception {
        Compilation c = compile(PRODUCT);

        assertThat(c).succeeded();
        Method likePattern = loadGenerated(c, "com.example.entity.generated.ProductSpec")
            .getDeclaredMethod("likePattern", String.class);
        likePattern.setAccessible(true);

        assertEquals("%widget%", likePattern.invoke(null, "Widget"));
        // %, _ and \ in user input are matched literally, not as LIKE wildcards
        assertEquals("%50\\%\\_off\\\\x%", likePattern.invoke(null, "50%_off\\X"));
    }

    @Test
    void likeHelperOnlyGeneratedWhenLikeIsUsed() throws Exception {
        Compilation c = compile(product("""
            @ExposeField(filterOperations = FilterOperation.EQ)
            private String name;
            """));

        assertThat(c).succeeded();
        assertFalse(source(c, "com.example.entity.generated.ProductSpec").contains("likePattern"));
    }

    // ── MongoDB Spec ──────────────────────────────────────────────────────────

    @Test
    void mongoSpecMapsEachOperation() throws Exception {
        Compilation c = compile(NOTE_DOCUMENT);

        assertThat(c).succeeded();
        String src = source(c, "com.example.document.generated.NoteSpec");
        assertTrue(src.contains("Criteria.where(\"author\").is(f.getAuthor())"), src);
        assertTrue(src.contains("Criteria.where(\"author\").ne(f.getAuthorNe())"), src);
        assertTrue(src.contains("Criteria.where(\"author\").regex(Pattern.quote(f.getAuthorLike()), \"i\")"), src);
        assertTrue(src.contains("Criteria.where(\"createdAt\").gt(f.getCreatedAtGt())"), src);
        assertTrue(src.contains("Criteria.where(\"createdAt\").lte(f.getCreatedAtLte())"), src);
        assertTrue(src.contains("Criteria.where(\"author\").in(f.getAuthorIn())"), src);
        assertTrue(src.contains("Criteria.where(\"author\").nin(f.getAuthorNotIn())"), src);
        assertTrue(src.contains("f.getCreatedAtIsNull() ? Criteria.where(\"createdAt\").is(null) : Criteria.where(\"createdAt\").ne(null)"), src);

        String controller = source(c, "com.example.document.generated.NoteController");
        assertTrue(controller.contains("mongoTemplate.find(NoteSpec.withFilters(filters)"), controller);
    }

    // ── Collection fields ─────────────────────────────────────────────────────

    private static final JavaFileObject PRODUCT_WITH_TAGS = product("""
            @ElementCollection
            @ExposeField(filterOperations = {FilterOperation.IN, FilterOperation.NOT_IN, FilterOperation.IS_NULL})
            private java.util.List<String> tags;

            @ElementCollection
            @ExposeField(filterOperations = FilterOperation.IN)
            private java.util.Set<Integer> codes;
            """);

    @Test
    void collectionInAndNotInTakeTheElementType() throws Exception {
        Compilation c = compile(PRODUCT_WITH_TAGS);

        assertThat(c).succeeded();
        String src = source(c, "com.example.entity.generated.ProductSearchRequest");
        assertKey(src, "tags_in", "List<String> tagsIn");
        assertKey(src, "tags_not_in", "List<String> tagsNotIn");
        assertKey(src, "tags_is_null", "Boolean tagsIsNull");
        assertKey(src, "codes_in", "List<Integer> codesIn");
        assertTrue(src.contains("tags has any of\""), src);
    }

    @Test
    void jpaCollectionUsesExistsSubquery() throws Exception {
        Compilation c = compile(PRODUCT_WITH_TAGS);

        assertThat(c).succeeded();
        String src = source(c, "com.example.entity.generated.ProductSpec");
        assertTrue(src.contains("predicates.add(tagsHasAny(root, query, cb, f.getTagsIn()))"), src);
        assertTrue(src.contains("predicates.add(cb.not(tagsHasAny(root, query, cb, f.getTagsNotIn())))"), src);
        assertTrue(src.contains("cb.isEmpty(root.<List<String>>get(\"tags\")) : cb.isNotEmpty(root.<List<String>>get(\"tags\"))"), src);
        assertTrue(src.contains("Root<Product> correlated = sub.correlate(root)"), src);
        assertTrue(src.contains("sub.select(cb.literal(1)).where(correlated.join(\"tags\").in(values))"), src);
        assertTrue(src.contains("predicates.add(codesHasAny(root, query, cb, f.getCodesIn()))"), src);
    }

    @Test
    void mongoCollectionUsesArrayOperators() throws Exception {
        Compilation c = compile(NOTE_DOCUMENT);

        assertThat(c).succeeded();
        String spec = source(c, "com.example.document.generated.NoteSpec");
        assertTrue(spec.contains("Criteria.where(\"labels\").in(f.getLabelsIn())"), spec);
        assertTrue(spec.contains("Criteria.where(\"labels\").nin(f.getLabelsNotIn())"), spec);
        assertTrue(spec.contains("Criteria.where(\"labels.0\").exists(!f.getLabelsIsNull())"), spec);
        assertKey(source(c, "com.example.document.generated.NoteSearchRequest"), "labels_in", "List<String> labelsIn");
    }

    @Test
    void scalarOperationsOnCollectionFail() {
        Compilation c = compile(product("""
            @ElementCollection
            @ExposeField(filterOperations = {FilterOperation.EQ, FilterOperation.LIKE, FilterOperation.GT})
            private java.util.List<String> tags;
            """));

        assertThat(c).failed();
        assertThat(c).hadErrorContaining("EQ does not apply to a collection — use IN (has any of) or NOT_IN (has none of)");
        assertThat(c).hadErrorContaining("LIKE does not apply to a collection");
        assertThat(c).hadErrorContaining("GT does not apply to a collection");
    }

    @Test
    void jpaCollectionWithoutElementCollectionFails() {
        Compilation c = compile(product("""
            @ExposeField(filterOperations = FilterOperation.IN)
            private java.util.List<String> tags;
            """));

        assertThat(c).failed();
        assertThat(c).hadErrorContaining("collection field 'tags' must be an @ElementCollection to be filtered");
        assertThat(c).hadErrorContaining("stored in a single column (Hibernate's array mapping");
    }

    @Test
    void rawCollectionFails() {
        Compilation c = compile(product("""
            @ElementCollection
            @ExposeField(filterOperations = FilterOperation.IN)
            private java.util.List tags;
            """));

        assertThat(c).failed();
        assertThat(c).hadErrorContaining("collection field 'tags' needs a concrete element type");
    }

    @Test
    void arrayFieldOnlySupportsIsNull() {
        Compilation c = compile(product("""
            @ExposeField(filterOperations = {FilterOperation.IN, FilterOperation.IS_NULL})
            private String[] codes;
            """));

        assertThat(c).failed();
        assertThat(c).hadErrorContaining("IN does not apply to an array — declare the field as a List");
        assertThat(c).hadErrorCount(1);
    }

    // ── Repository, controller & security wiring ──────────────────────────────

    /** Same filters as {@link #PRODUCT}'s name field, but SEARCH is not exposed. */
    private static final JavaFileObject PRODUCT_WITHOUT_SEARCH = product("path = \"products\"", """
            @ExposeField(filterOperations = FilterOperation.EQ)
            private String name;
            """);

    @Test
    void jpaRepositoryExtendsSpecificationExecutorOnlyWithSearch() throws Exception {
        Compilation withSearch = compile(PRODUCT);
        assertThat(withSearch).succeeded();
        assertTrue(source(withSearch, "com.example.entity.generated.ProductRepository")
            .contains("JpaSpecificationExecutor<Product>"));

        Compilation withoutSearch = compile(PRODUCT_WITHOUT_SEARCH);
        assertThat(withoutSearch).succeeded();
        assertFalse(source(withoutSearch, "com.example.entity.generated.ProductRepository")
            .contains("JpaSpecificationExecutor"));
        assertTrue(withoutSearch.generatedSourceFile("com.example.entity.generated.ProductSearchRequest").isEmpty(),
            "no SearchRequest without Operation.SEARCH");
        assertTrue(withoutSearch.generatedSourceFile("com.example.entity.generated.ProductSpec").isEmpty(),
            "no Spec without Operation.SEARCH");
    }

    @Test
    void searchIsAPostWithAnOptionalJsonBody() throws Exception {
        Compilation c = compile(PRODUCT);

        assertThat(c).succeeded();
        String src = source(c, "com.example.entity.generated.ProductController");
        assertTrue(src.contains("@PostMapping(\"/search\")"), src);
        assertTrue(src.contains("@RequestBody(required = false)"), src);
        assertTrue(src.contains("ProductSearchRequest request"), src);
        // a missing body searches without filters, like {}
        assertTrue(src.contains("ProductSearchRequest filters = request != null ? request : new ProductSearchRequest()"), src);
        assertTrue(src.contains("repository.findAll(ProductSpec.withFilters(filters))"), src);
        assertTrue(src.contains("examples = @ExampleObject(name = \"noFilters\", summary = \"No filters: every record\", description = \"Add keys from the schema to narrow the result\", value = \"{}\")"), src);
    }

    @Test
    void findAllNoLongerTakesFilters() throws Exception {
        Compilation c = compile(PRODUCT);

        assertThat(c).succeeded();
        String src = source(c, "com.example.entity.generated.ProductController");
        assertTrue(src.contains("public ResponseEntity<List<ProductDto>> findAll() {"), src);
        assertTrue(src.contains("mapper.toDtoList(repository.findAll())"), src);
        assertFalse(src.contains("@ModelAttribute"), src);
    }

    @Test
    void searchUsesReadRolesNotWriteRoles() throws Exception {
        Compilation c = compile(product("""
            path = "products", authType = AuthType.BASIC, readRoles = "VIEWER", writeRoles = "ADMIN",
            expose = {Operation.FIND_ALL, Operation.CREATE, Operation.SEARCH}""", """
            @ExposeField(filterOperations = FilterOperation.EQ)
            private String name;
            """));

        assertThat(c).succeeded();
        String src = source(c, "com.example.entity.generated.ProductSecurityConfigurer");
        assertTrue(src.contains(".requestMatchers(HttpMethod.POST, \"/api/products/search\").hasAnyRole(\"VIEWER\")"), src);
        assertTrue(src.contains(".requestMatchers(HttpMethod.POST, \"/api/products\").hasAnyRole(\"ADMIN\")"), src);
    }

    @Test
    void publicSearchIsPermitted() throws Exception {
        Compilation c = compile(PRODUCT);

        assertThat(c).succeeded();
        assertTrue(source(c, "com.example.entity.generated.ProductSecurityConfigurer")
            .contains(".requestMatchers(HttpMethod.POST, \"/api/products/search\").permitAll()"));
    }

    @Test
    void filtersWithoutSearchWarn() {
        Compilation c = compile(PRODUCT_WITHOUT_SEARCH);

        assertThat(c).succeeded();
        assertThat(c).hadWarningContaining(
            "Product declares @ExposeField filterOperations but @ExposeEntity does not expose Operation.SEARCH — the filters are not used");
    }

    @Test
    void searchWithoutFiltersWarnsAndMatchesEverything() throws Exception {
        Compilation c = compile(product("private String name;\n"));

        assertThat(c).succeeded();
        assertThat(c).hadWarningContaining(
            "Product exposes Operation.SEARCH but no field declares @ExposeField(filterOperations = ...) — the search endpoint returns every record");
        assertTrue(source(c, "com.example.entity.generated.ProductSearchRequest")
            .contains("allowed keys: none (no field declares filterOperations)"));
    }

    @Test
    void searchAndFiltersTogetherDoNotWarn() {
        Compilation c = compile(PRODUCT);

        assertThat(c).succeeded();
        assertThat(c).hadWarningCount(0);
    }

    // ── SearchRequest JSON binding ────────────────────────────────────────────

    /** A mapper like Spring Boot's: unknown properties are not a failure by default. */
    private static final JsonMapper LENIENT_MAPPER = JsonMapper.builder()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build();

    @Test
    void searchRequestReadsKeysIntoProperties() throws Exception {
        Compilation c = compile(PRODUCT);
        assertThat(c).succeeded();
        Class<?> requestClass = loadGenerated(c, "com.example.entity.generated.ProductSearchRequest");

        Object request = LENIENT_MAPPER.readValue(
            "{\"price_gt\": 10.5, \"status_in\": [\"ACTIVE\"], \"deletedAt_is_null\": true}", requestClass);

        assertEquals(new java.math.BigDecimal("10.5"), requestClass.getMethod("getPriceGt").invoke(request));
        assertEquals("[ACTIVE]", requestClass.getMethod("getStatusIn").invoke(request).toString());
        assertEquals(Boolean.TRUE, requestClass.getMethod("getDeletedAtIsNull").invoke(request));
        assertEquals(null, requestClass.getMethod("getName").invoke(request));
    }

    @Test
    void searchRequestRejectsUnknownKeysEvenWhenTheMapperIgnoresThem() throws Exception {
        Compilation c = compile(PRODUCT);
        assertThat(c).succeeded();
        Class<?> requestClass = loadGenerated(c, "com.example.entity.generated.ProductSearchRequest");

        // a typo, and an operation the field does not declare (stock has GT and LT only)
        for (String key : new String[] {"pirce_gt", "stock"}) {
            Exception e = assertThrows(Exception.class,
                () -> LENIENT_MAPPER.readValue("{\"" + key + "\": 1}", requestClass));
            assertTrue(e.getMessage().contains("Unknown search key '" + key + "' for Product — allowed keys: name, name_ne, name_like, price,"),
                e.getMessage());
        }
    }

    @Test
    void searchRequestRejectsValuesOfTheWrongType() throws Exception {
        Compilation c = compile(PRODUCT);
        assertThat(c).succeeded();
        Class<?> requestClass = loadGenerated(c, "com.example.entity.generated.ProductSearchRequest");

        Exception e = assertThrows(Exception.class,
            () -> LENIENT_MAPPER.readValue("{\"price_gt\": \"cheap\"}", requestClass));
        assertTrue(e.getMessage().contains("price_gt"), e.getMessage());
    }

    // ── Compile-time validation ───────────────────────────────────────────────

    @Test
    void rangeOperationOnNonComparableTypeFails() {
        Compilation c = compile(product("""
            @ExposeField(filterOperations = FilterOperation.GT)
            private java.util.Map<String, String> attributes;
            """));

        assertThat(c).failed();
        assertThat(c).hadErrorContaining("GT is not supported on 'attributes'");
        assertThat(c).hadErrorContaining("requires a primitive or Comparable type");
    }

    @Test
    void likeOnNonStringFails() {
        Compilation c = compile(product("""
            @ExposeField(filterOperations = FilterOperation.LIKE)
            private java.math.BigDecimal price;
            """));

        assertThat(c).failed();
        assertThat(c).hadErrorContaining("LIKE requires a String field");
    }

    @Test
    void isNullOnPrimitiveFails() {
        Compilation c = compile(product("""
            @ExposeField(filterOperations = FilterOperation.IS_NULL)
            private int stock;
            """));

        assertThat(c).failed();
        assertThat(c).hadErrorContaining("IS_NULL cannot apply to a primitive field");
    }

    @Test
    void hiddenFieldCannotBeFiltered() {
        Compilation c = compile(product("""
            @ExposeField(hidden = true, filterOperations = FilterOperation.EQ)
            private String secret;
            """));

        assertThat(c).failed();
        assertThat(c).hadErrorContaining("hidden field 'secret' cannot have filterOperations");
    }

    // ── Relation filters (by id) ──────────────────────────────────────────────

    private static final JavaFileObject BASE_ENTITY = JavaFileObjects.forSourceString(
        "com.example.entity.BaseEntity",
        """
        package com.example.entity;
        import jakarta.persistence.*;
        @MappedSuperclass
        public abstract class BaseEntity {
            @Id private java.util.UUID uuid;
            public java.util.UUID getUuid() { return uuid; }
            public void setUuid(java.util.UUID uuid) { this.uuid = uuid; }
        }
        """
    );

    /** Id {@code uuid} of type UUID, inherited from a @MappedSuperclass. */
    private static final JavaFileObject TAG = JavaFileObjects.forSourceString(
        "com.example.entity.Tag",
        """
        package com.example.entity;
        import jakarta.persistence.*;
        @Entity
        public class Tag extends BaseEntity {}
        """
    );

    private static final JavaFileObject PRODUCT_WITH_RELATIONS = product("""
            @ManyToOne
            @ExposeField(filterOperations = {FilterOperation.EQ, FilterOperation.NE, FilterOperation.IN,
                                             FilterOperation.NOT_IN, FilterOperation.IS_NULL})
            private Category category;

            @ManyToMany
            @ExposeField(filterOperations = {FilterOperation.IN, FilterOperation.NOT_IN, FilterOperation.IS_NULL})
            private java.util.Set<Tag> tags;

            public Category getCategory() { return category; }
            public void setCategory(Category category) { this.category = category; }
            public java.util.Set<Tag> getTags() { return tags; }
            public void setTags(java.util.Set<Tag> tags) { this.tags = tags; }
            """);

    @Test
    void toOneRelationFiltersTakeTheRelatedIdAsCategoryId() throws Exception {
        Compilation c = compile(CATEGORY, BASE_ENTITY, TAG, PRODUCT_WITH_RELATIONS);

        assertThat(c).succeeded();
        String src = source(c, "com.example.entity.generated.ProductSearchRequest");
        assertKey(src, "categoryId", "Long categoryId");
        assertKey(src, "categoryId_ne", "Long categoryIdNe");
        assertKey(src, "categoryId_in", "List<Long> categoryIdIn");
        assertKey(src, "categoryId_not_in", "List<Long> categoryIdNotIn");
        assertKey(src, "categoryId_is_null", "Boolean categoryIdIsNull");
        assertTrue(src.contains("category id equals"), src);
    }

    @Test
    void toManyRelationFiltersTakeListsOfTheRelatedIdType() throws Exception {
        Compilation c = compile(CATEGORY, BASE_ENTITY, TAG, PRODUCT_WITH_RELATIONS);

        assertThat(c).succeeded();
        String src = source(c, "com.example.entity.generated.ProductSearchRequest");
        assertKey(src, "tags_in", "List<UUID> tagsIn");
        assertKey(src, "tags_not_in", "List<UUID> tagsNotIn");
        assertTrue(src.contains("tags has any of the ids\""), src);
    }

    @Test
    void jpaRelationSpecComparesTheRelatedId() throws Exception {
        Compilation c = compile(CATEGORY, BASE_ENTITY, TAG, PRODUCT_WITH_RELATIONS);

        assertThat(c).succeeded();
        String src = source(c, "com.example.entity.generated.ProductSpec");
        assertTrue(src.contains("cb.equal(root.get(\"category\").get(\"id\"), f.getCategoryId())"), src);
        assertTrue(src.contains("cb.or(cb.notEqual(root.get(\"category\").get(\"id\"), f.getCategoryIdNe()), cb.isNull(root.get(\"category\")))"), src);
        assertTrue(src.contains("root.get(\"category\").get(\"id\").in(f.getCategoryIdIn())"), src);
        assertTrue(src.contains("cb.or(cb.not(root.get(\"category\").get(\"id\").in(f.getCategoryIdNotIn())), cb.isNull(root.get(\"category\")))"), src);
        assertTrue(src.contains("f.getCategoryIdIsNull() ? cb.isNull(root.get(\"category\")) : cb.isNotNull(root.get(\"category\"))"), src);
        assertTrue(src.contains("predicates.add(tagsHasAny(root, query, cb, f.getTagsIn()))"), src);
        assertTrue(src.contains("predicates.add(cb.not(tagsHasAny(root, query, cb, f.getTagsNotIn())))"), src);
        // the related id is found by its real name, on the @MappedSuperclass
        assertTrue(src.contains("correlated.join(\"tags\").get(\"uuid\").in(values)"), src);
        assertTrue(src.contains("cb.isEmpty(root.<Set<Tag>>get(\"tags\"))"), src);
    }

    @Test
    void rangeAndLikeOnRelationFail() {
        Compilation c = compile(CATEGORY, product("""
            @ManyToOne
            @ExposeField(filterOperations = {FilterOperation.GT, FilterOperation.LIKE})
            private Category category;
            """));

        assertThat(c).failed();
        assertThat(c).hadErrorContaining("GT is not supported on relation 'category' — relation filters compare ids: use EQ, NE, IN, NOT_IN or IS_NULL");
        assertThat(c).hadErrorContaining("LIKE is not supported on relation 'category'");
    }

    @Test
    void eqOnToManyRelationFails() {
        Compilation c = compile(CATEGORY, BASE_ENTITY, TAG, product("""
            @ManyToMany
            @ExposeField(filterOperations = FilterOperation.EQ)
            private java.util.Set<Tag> tags;
            """));

        assertThat(c).failed();
        assertThat(c).hadErrorContaining("EQ is not supported on relation 'tags' — relation filters compare ids: use IN (has any of), NOT_IN (has none of) or IS_NULL");
    }

    @Test
    void inverseOneToOneCannotBeFiltered() {
        Compilation c = compile(JavaFileObjects.forSourceString("com.example.entity.Passport",
            """
            package com.example.entity;
            import jakarta.persistence.*;
            @Entity
            public class Passport {
                @Id @GeneratedValue private Long id;
                @OneToOne private Product owner;
                public Long getId() { return id; }
                public void setId(Long id) { this.id = id; }
            }
            """), product("""
            @OneToOne(mappedBy = "owner")
            @ExposeField(filterOperations = FilterOperation.IS_NULL)
            private Passport passport;
            """));

        assertThat(c).failed();
        assertThat(c).hadErrorContaining("relation field 'passport' is the inverse side of a @OneToOne (mappedBy)");
    }

    @Test
    void clashingFilterNamesFail() {
        Compilation c = compile(product("""
            @ExposeField(filterOperations = FilterOperation.GT)
            private java.math.BigDecimal price;

            @ExposeField(filterOperations = FilterOperation.EQ)
            private java.math.BigDecimal priceGt;
            """));

        assertThat(c).failed();
        assertThat(c).hadErrorContaining("clashes with 'price GT'");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Loads a class compiled by compile-testing, defining every class from that compilation. */
    private static Class<?> loadGenerated(Compilation c, String className) throws ClassNotFoundException {
        Map<String, byte[]> classes = new HashMap<>();
        for (JavaFileObject file : c.generatedFiles()) {
            if (file.getKind() != JavaFileObject.Kind.CLASS) continue;
            String path = file.toUri().getPath();
            String binaryName = path.substring(path.indexOf("/com/") + 1, path.length() - ".class".length())
                .replace('/', '.');
            try (InputStream in = file.openInputStream()) {
                classes.put(binaryName, in.readAllBytes());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        ClassLoader loader = new ClassLoader(FilterGeneratorTest.class.getClassLoader()) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                byte[] bytes = classes.get(name);
                if (bytes == null) throw new ClassNotFoundException(name);
                return defineClass(name, bytes, 0, bytes.length);
            }
        };
        return loader.loadClass(className);
    }
}
