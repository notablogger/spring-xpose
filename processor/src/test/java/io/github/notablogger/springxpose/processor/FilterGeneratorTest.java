package io.github.notablogger.springxpose.processor;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Compile-tests for {@code @ExposeField} filters: FilterParams, Spec, repository and controller output. */
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
            @ExposeField(operations = {FilterOperation.EQ, FilterOperation.NE, FilterOperation.LIKE})
            private String name;

            @ExposeField(operations = {FilterOperation.EQ, FilterOperation.GT, FilterOperation.GTE,
                                       FilterOperation.LT, FilterOperation.LTE})
            private java.math.BigDecimal price;

            @ExposeField(operations = {FilterOperation.GT, FilterOperation.LT})
            private int stock;

            @ExposeField(operations = {FilterOperation.EQ, FilterOperation.IN, FilterOperation.NOT_IN})
            private Status status;

            @ExposeField(operations = FilterOperation.IS_NULL)
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

        @ExposeDocument(path = "notes")
        public class Note {
            @Id private String id;

            @ExposeField(operations = {FilterOperation.EQ, FilterOperation.NE, FilterOperation.LIKE,
                                       FilterOperation.IN, FilterOperation.NOT_IN})
            private String author;

            @ExposeField(operations = {FilterOperation.GT, FilterOperation.LTE, FilterOperation.IS_NULL})
            private java.time.Instant createdAt;

            @ExposeField(operations = {FilterOperation.IN, FilterOperation.NOT_IN, FilterOperation.IS_NULL})
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

    /** Stand-in for springdoc's annotation, to exercise the "springdoc on the classpath" branch. */
    private static final JavaFileObject SPRINGDOC_PARAMETER_OBJECT = JavaFileObjects.forSourceString(
        "org.springdoc.core.annotations.ParameterObject",
        """
        package org.springdoc.core.annotations;
        public @interface ParameterObject {}
        """
    );

    private static JavaFileObject product(String fields) {
        return JavaFileObjects.forSourceString("com.example.entity.Product",
            """
            package com.example.entity;
            import io.github.notablogger.springxpose.annotation.*;
            import jakarta.persistence.*;
            import jakarta.validation.constraints.NotBlank;

            @Entity
            @ExposeEntity(path = "products")
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

    // ── FilterParams ──────────────────────────────────────────────────────────

    @Test
    void filterParamsHasOnePropertyPerOperation() throws Exception {
        Compilation c = compile(PRODUCT);

        assertThat(c).succeeded();
        String src = source(c, "com.example.entity.generated.ProductFilterParams");
        for (String getter : new String[] {
                "getName()", "getNameNe()", "getNameLike()",
                "getPrice()", "getPriceGt()", "getPriceGte()", "getPriceLt()", "getPriceLte()",
                "getStockGt()", "getStockLt()", "getStatus()", "getStatusIn()", "getStatusNotIn()",
                "getDeletedAtIsNull()"}) {
            assertTrue(src.contains(getter), "FilterParams must have " + getter);
        }
        assertFalse(src.contains("getSku"), "@ExposeField without operations must not be filterable");
        assertFalse(src.contains("getDescription"), "fields without @ExposeField must not be filterable");
        assertFalse(src.contains("getStock()"), "EQ was not declared on stock");
    }

    @Test
    void suffixedParamsCarryBindParamAndOpenApiName() throws Exception {
        Compilation c = compile(PRODUCT);

        assertThat(c).succeeded();
        String src = source(c, "com.example.entity.generated.ProductFilterParams");
        // Spring MVC binds ?price_gt= to the priceGt constructor parameter ...
        assertTrue(src.contains("@BindParam(\"price_gt\") BigDecimal priceGt"), src);
        assertTrue(src.contains("@BindParam(\"name_like\") String nameLike"), src);
        // ... and springdoc documents it as price_gt, not priceGt
        assertTrue(src.contains("@Parameter(\n      name = \"price_gt\",\n      description = \"price greater than\"\n  )"), src);
        // EQ uses the plain field name — no @BindParam needed
        assertFalse(src.contains("@BindParam(\"price\")"), src);
    }

    @Test
    void primitiveFieldsAreBoxedSoAbsentParamsAreNull() throws Exception {
        Compilation c = compile(PRODUCT);

        assertThat(c).succeeded();
        String src = source(c, "com.example.entity.generated.ProductFilterParams");
        assertTrue(src.contains("Integer stockGt"), "int stock must become Integer in FilterParams");
    }

    @Test
    void inAndNotInAreListsAndIsNullIsBoolean() throws Exception {
        Compilation c = compile(PRODUCT);

        assertThat(c).succeeded();
        String src = source(c, "com.example.entity.generated.ProductFilterParams");
        assertTrue(src.contains("@BindParam(\"status_in\") List<Product.Status> statusIn"), src);
        assertTrue(src.contains("@BindParam(\"status_not_in\") List<Product.Status> statusNotIn"), src);
        assertTrue(src.contains("@BindParam(\"deletedAt_is_null\") Boolean deletedAtIsNull"), src);
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
            @ExposeField(operations = FilterOperation.EQ)
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
            @ExposeField(operations = {FilterOperation.IN, FilterOperation.NOT_IN, FilterOperation.IS_NULL})
            private java.util.List<String> tags;

            @ElementCollection
            @ExposeField(operations = FilterOperation.IN)
            private java.util.Set<Integer> codes;
            """);

    @Test
    void collectionInAndNotInBindTheElementType() throws Exception {
        Compilation c = compile(PRODUCT_WITH_TAGS);

        assertThat(c).succeeded();
        String src = source(c, "com.example.entity.generated.ProductFilterParams");
        assertTrue(src.contains("@BindParam(\"tags_in\") List<String> tagsIn"), src);
        assertTrue(src.contains("@BindParam(\"tags_not_in\") List<String> tagsNotIn"), src);
        assertTrue(src.contains("@BindParam(\"tags_is_null\") Boolean tagsIsNull"), src);
        assertTrue(src.contains("@BindParam(\"codes_in\") List<Integer> codesIn"), src);
        assertTrue(src.contains("tags has any of (comma-separated)"), src);
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
        assertTrue(source(c, "com.example.document.generated.NoteFilterParams")
            .contains("@BindParam(\"labels_in\") List<String> labelsIn"));
    }

    @Test
    void scalarOperationsOnCollectionFail() {
        Compilation c = compile(product("""
            @ElementCollection
            @ExposeField(operations = {FilterOperation.EQ, FilterOperation.LIKE, FilterOperation.GT})
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
            @ExposeField(operations = FilterOperation.IN)
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
            @ExposeField(operations = FilterOperation.IN)
            private java.util.List tags;
            """));

        assertThat(c).failed();
        assertThat(c).hadErrorContaining("collection field 'tags' needs a concrete element type");
    }

    @Test
    void arrayFieldOnlySupportsIsNull() {
        Compilation c = compile(product("""
            @ExposeField(operations = {FilterOperation.IN, FilterOperation.IS_NULL})
            private String[] codes;
            """));

        assertThat(c).failed();
        assertThat(c).hadErrorContaining("IN does not apply to an array — declare the field as a List");
        assertThat(c).hadErrorCount(1);
    }

    // ── Repository & controller wiring ────────────────────────────────────────

    @Test
    void jpaRepositoryExtendsSpecificationExecutorOnlyWithFilters() throws Exception {
        Compilation withFilters = compile(PRODUCT);
        assertThat(withFilters).succeeded();
        assertTrue(source(withFilters, "com.example.entity.generated.ProductRepository")
            .contains("JpaSpecificationExecutor<Product>"));

        Compilation withoutFilters = compile(product("private String name;\n"));
        assertThat(withoutFilters).succeeded();
        assertFalse(source(withoutFilters, "com.example.entity.generated.ProductRepository")
            .contains("JpaSpecificationExecutor"));
        assertTrue(withoutFilters.generatedSourceFile("com.example.entity.generated.ProductFilterParams").isEmpty(),
            "no FilterParams without @ExposeField operations");
    }

    @Test
    void controllerBindsFiltersFromModelAttribute() throws Exception {
        Compilation c = compile(PRODUCT);

        assertThat(c).succeeded();
        String src = source(c, "com.example.entity.generated.ProductController");
        assertTrue(src.contains("@ModelAttribute ProductFilterParams filters"), src);
        assertTrue(src.contains("ProductSpec.withFilters(filters)"), src);
        assertFalse(src.contains("@ParameterObject"), "springdoc is not on the classpath here");
    }

    @Test
    void controllerAddsParameterObjectWhenSpringdocIsPresent() throws Exception {
        Compilation c = compile(PRODUCT, SPRINGDOC_PARAMETER_OBJECT);

        assertThat(c).succeeded();
        assertTrue(source(c, "com.example.entity.generated.ProductController")
            .contains("@ParameterObject @ModelAttribute ProductFilterParams filters"));
    }

    // ── Compile-time validation ───────────────────────────────────────────────

    @Test
    void rangeOperationOnNonComparableTypeFails() {
        Compilation c = compile(product("""
            @ExposeField(operations = FilterOperation.GT)
            private java.util.Map<String, String> attributes;
            """));

        assertThat(c).failed();
        assertThat(c).hadErrorContaining("GT is not supported on 'attributes'");
        assertThat(c).hadErrorContaining("requires a primitive or Comparable type");
    }

    @Test
    void likeOnNonStringFails() {
        Compilation c = compile(product("""
            @ExposeField(operations = FilterOperation.LIKE)
            private java.math.BigDecimal price;
            """));

        assertThat(c).failed();
        assertThat(c).hadErrorContaining("LIKE requires a String field");
    }

    @Test
    void isNullOnPrimitiveFails() {
        Compilation c = compile(product("""
            @ExposeField(operations = FilterOperation.IS_NULL)
            private int stock;
            """));

        assertThat(c).failed();
        assertThat(c).hadErrorContaining("IS_NULL cannot apply to a primitive field");
    }

    @Test
    void relationFieldCannotBeFiltered() {
        Compilation c = compile(CATEGORY, product("""
            @ManyToOne
            @ExposeField(operations = FilterOperation.EQ)
            private Category category;
            """));

        assertThat(c).failed();
        assertThat(c).hadErrorContaining("relation field 'category' cannot be filtered");
    }

    @Test
    void clashingFilterNamesFail() {
        Compilation c = compile(product("""
            @ExposeField(operations = FilterOperation.GT)
            private java.math.BigDecimal price;

            @ExposeField(operations = FilterOperation.EQ)
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
