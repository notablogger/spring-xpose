package io.github.notablogger.springxpose.processor.generator;

import com.squareup.javapoet.*;
import io.github.notablogger.springxpose.annotation.FilterOperation;
import io.github.notablogger.springxpose.annotation.StoreType;
import io.github.notablogger.springxpose.processor.model.EntityModel;
import io.github.notablogger.springxpose.processor.model.FilterFieldModel;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates a {@code <Entity>Spec} class that builds a filter query from a
 * {@code <Entity>FilterParams} object. Each non-null filter param adds one condition;
 * conditions are AND-ed.
 *
 * <ul>
 *   <li><b>JPA</b> — returns a {@code Specification<Entity>} (lambda over {@code CriteriaBuilder}).
 *       The repository must also extend {@code JpaSpecificationExecutor}
 *       (handled by {@link RepositoryGenerator}).</li>
 *   <li><b>MongoDB</b> — returns a Spring Data {@code Query} built from {@code Criteria}.
 *       The generated controller uses {@code MongoTemplate} to execute the query.</li>
 * </ul>
 *
 * <p>Operation mapping:
 * <pre>
 *        JPA                                       MongoDB
 * EQ     cb.equal                                  Criteria.is
 * NE     cb.notEqual OR cb.isNull                  Criteria.ne   (matches null/missing too)
 * GT     cb.greaterThan                            Criteria.gt
 * GTE    cb.greaterThanOrEqualTo                   Criteria.gte
 * LT     cb.lessThan                               Criteria.lt
 * LTE    cb.lessThanOrEqualTo                      Criteria.lte
 * LIKE   cb.like(cb.lower(..), %escaped%)          Criteria.regex(Pattern.quote(..), "i")
 * IN     path.in(list)                             Criteria.in
 * NOT_IN NOT path.in(list) OR cb.isNull           Criteria.nin  (matches null/missing too)
 * IS_NULL cb.isNull / cb.isNotNull                 Criteria.is(null) / ne(null)
 * </pre>
 * IN / NOT_IN apply only when the list is non-empty.
 *
 * <p>On collection fields ({@code @ElementCollection List<String> tags}, Mongo arrays) IN means
 * "has any of" (JPA: EXISTS subquery, Mongo: {@code in}), NOT_IN "has none of" (NOT EXISTS /
 * {@code nin}; empty collections match) and IS_NULL "null or empty" (JPA: {@code isEmpty},
 * Mongo: {@code tags.0} does not exist).
 *
 * <p>Example JPA output for {@code @ExposeField(filterOperations = {EQ, GT}) BigDecimal price}:
 * <pre>
 * public class ProductSpec {
 *     public static Specification&lt;Product&gt; withFilters(ProductFilterParams f) {
 *         return (root, query, cb) -> {
 *             List&lt;Predicate&gt; predicates = new ArrayList&lt;&gt;();
 *             if (f.getPrice() != null)
 *                 predicates.add(cb.equal(root.get("price"), f.getPrice()));
 *             if (f.getPriceGt() != null)
 *                 predicates.add(cb.greaterThan(root.&lt;BigDecimal&gt;get("price"), f.getPriceGt()));
 *             return cb.and(predicates.toArray(new Predicate[0]));
 *         };
 *     }
 * }
 * </pre>
 *
 * <p>Example MongoDB output for {@code @ExposeField(filterOperations = EQ) String author}:
 * <pre>
 * public class NoteSpec {
 *     public static Query withFilters(NoteFilterParams f) {
 *         List&lt;Criteria&gt; conditions = new ArrayList&lt;&gt;();
 *         if (f.getAuthor() != null) {
 *             conditions.add(Criteria.where("author").is(f.getAuthor()));
 *         }
 *         if (conditions.isEmpty()) {
 *             return new Query();
 *         }
 *         return Query.query(new Criteria().andOperator(conditions));
 *     }
 * }
 * </pre>
 */
public class SpecificationGenerator {

    private final ProcessingEnvironment processingEnv;

    public SpecificationGenerator(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
    }

    public void generate(EntityModel model) {
        if (!model.hasFilters()) return;

        if (model.storeType() == StoreType.JPA) {
            generateJpaSpec(model);
        } else {
            generateMongoSpec(model);
        }
    }

    // ── JPA ─────────────────────────────────────────────────────────────────

    private void generateJpaSpec(EntityModel model) {
        ClassName entityClass     = ClassName.bestGuess(model.entityClassName());
        ClassName filterParamsCls = ClassName.get(model.packageName() + ".generated",
            model.entitySimpleName() + "FilterParams");
        ClassName specification   = ClassName.get("org.springframework.data.jpa.domain", "Specification");
        ClassName predicate       = ClassName.get("jakarta.persistence.criteria", "Predicate");
        ClassName arrayList       = ClassName.get("java.util", "ArrayList");
        ClassName list            = ClassName.get("java.util", "List");
        ClassName locale          = ClassName.get("java.util", "Locale");
        TypeName  specType        = ParameterizedTypeName.get(specification, entityClass);

        // Build the lambda body as raw code so the indented multi-statement lambda compiles cleanly.
        MethodSpec.Builder withFilters = MethodSpec.methodBuilder("withFilters")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(specType)
            .addParameter(filterParamsCls, "f")
            .addCode("return (root, query, cb) -> {\n")
            .addCode("    $T<$T> predicates = new $T<>();\n", list, predicate, arrayList);

        boolean usesLike = false;
        List<MethodSpec> hasAnyHelpers = new ArrayList<>();
        for (FilterFieldModel filter : model.filters()) {
            TypeName type = TypeName.get(filter.type()).box();
            String field = filter.name();
            if (filter.isCollection() && (filter.operations().contains(FilterOperation.IN)
                    || filter.operations().contains(FilterOperation.NOT_IN))) {
                hasAnyHelpers.add(jpaHasAnyHelper(field, filter.relatedIdField(), entityClass));
            }
            // A to-one relation compares the related id: root.get("category").get("id") reads the FK column
            CodeBlock path = filter.isRelation() && !filter.isCollection()
                ? CodeBlock.of("root.get($S).get($S)", field, filter.relatedIdField())
                : CodeBlock.of("root.get($S)", field);
            for (FilterOperation op : filter.operations()) {
                String getter = filter.getterName(op);
                CodeBlock condition = filter.isCollection()
                    ? jpaCollectionCondition(op, field, type, getter)
                    : switch (op) {
                    case EQ   -> CodeBlock.of("cb.equal($L, f.$L())", path, getter);
                    case NE   -> CodeBlock.of("cb.or(cb.notEqual($L, f.$L()), cb.isNull(root.get($S)))",
                                     path, getter, field);
                    case GT   -> CodeBlock.of("cb.greaterThan(root.<$T>get($S), f.$L())", type, field, getter);
                    case GTE  -> CodeBlock.of("cb.greaterThanOrEqualTo(root.<$T>get($S), f.$L())", type, field, getter);
                    case LT   -> CodeBlock.of("cb.lessThan(root.<$T>get($S), f.$L())", type, field, getter);
                    case LTE  -> CodeBlock.of("cb.lessThanOrEqualTo(root.<$T>get($S), f.$L())", type, field, getter);
                    case LIKE -> CodeBlock.of("cb.like(cb.lower(root.<$T>get($S)), likePattern(f.$L()), '\\\\')",
                                     String.class, field, getter);
                    case IN   -> CodeBlock.of("$L.in(f.$L())", path, getter);
                    case NOT_IN -> CodeBlock.of("cb.or(cb.not($L.in(f.$L())), cb.isNull(root.get($S)))",
                                     path, getter, field);
                    case IS_NULL -> CodeBlock.of("f.$L() ? cb.isNull(root.get($S)) : cb.isNotNull(root.get($S))",
                                     getter, field, field);
                    };
                usesLike |= op == FilterOperation.LIKE;
                withFilters
                    .addCode("    if ($L)\n", guard(op, getter))
                    .addCode("        predicates.add($L);\n", condition);
            }
        }

        withFilters
            .addCode("    return cb.and(predicates.toArray(new $T[0]));\n", predicate)
            .addCode("};\n");

        TypeSpec.Builder spec = TypeSpec.classBuilder(model.entitySimpleName() + "Spec")
            .addModifiers(Modifier.PUBLIC)
            .addJavadoc("Generated by spring-xpose — do not edit.\n"
                + "JPA {@link $T} builder for filtering on "
                + "{@code GET /api/" + model.basePath() + "}.\n", specification)
            .addMethod(withFilters.build())
            .addMethods(hasAnyHelpers);

        if (usesLike) {
            // Lower-cased "contains" pattern; LIKE wildcards in the value are escaped with '\'
            spec.addMethod(MethodSpec.methodBuilder("likePattern")
                .addModifiers(Modifier.STATIC)
                .returns(String.class)
                .addParameter(String.class, "value")
                .addStatement("return \"%\" + value.toLowerCase($T.ROOT)\n"
                    + ".replace(\"\\\\\", \"\\\\\\\\\")\n"
                    + ".replace(\"%\", \"\\\\%\")\n"
                    + ".replace(\"_\", \"\\\\_\") + \"%\"", locale)
                .build());
        }

        write(model, spec.build());
    }

    // ── MongoDB ──────────────────────────────────────────────────────────────

    private void generateMongoSpec(EntityModel model) {
        ClassName filterParamsCls = ClassName.get(model.packageName() + ".generated",
            model.entitySimpleName() + "FilterParams");
        ClassName queryClass      = ClassName.get("org.springframework.data.mongodb.core.query", "Query");
        ClassName criteriaClass   = ClassName.get("org.springframework.data.mongodb.core.query", "Criteria");
        ClassName arrayList       = ClassName.get("java.util", "ArrayList");
        ClassName list            = ClassName.get("java.util", "List");
        ClassName pattern         = ClassName.get("java.util.regex", "Pattern");

        MethodSpec.Builder withFilters = MethodSpec.methodBuilder("withFilters")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(queryClass)
            .addParameter(filterParamsCls, "f")
            .addStatement("$T<$T> conditions = new $T<>()", list, criteriaClass, arrayList);

        for (FilterFieldModel filter : model.filters()) {
            String field = filter.name();
            for (FilterOperation op : filter.operations()) {
                String getter = filter.getterName(op);
                CodeBlock condition = switch (op) {
                    case EQ   -> CodeBlock.of("$T.where($S).is(f.$L())", criteriaClass, field, getter);
                    case NE   -> CodeBlock.of("$T.where($S).ne(f.$L())", criteriaClass, field, getter);
                    case GT   -> CodeBlock.of("$T.where($S).gt(f.$L())", criteriaClass, field, getter);
                    case GTE  -> CodeBlock.of("$T.where($S).gte(f.$L())", criteriaClass, field, getter);
                    case LT   -> CodeBlock.of("$T.where($S).lt(f.$L())", criteriaClass, field, getter);
                    case LTE  -> CodeBlock.of("$T.where($S).lte(f.$L())", criteriaClass, field, getter);
                    case LIKE -> CodeBlock.of("$T.where($S).regex($T.quote(f.$L()), \"i\")",
                                     criteriaClass, field, pattern, getter);
                    case IN   -> CodeBlock.of("$T.where($S).in(f.$L())", criteriaClass, field, getter);
                    case NOT_IN -> CodeBlock.of("$T.where($S).nin(f.$L())", criteriaClass, field, getter);
                    // On arrays "null" means null, missing or empty: no element at index 0
                    case IS_NULL -> filter.isCollection()
                        ? CodeBlock.of("$T.where($S).exists(!f.$L())", criteriaClass, field + ".0", getter)
                        : CodeBlock.of("f.$L() ? $T.where($S).is(null) : $T.where($S).ne(null)",
                              getter, criteriaClass, field, criteriaClass, field);
                };
                withFilters
                    .beginControlFlow("if ($L)", guard(op, getter))
                    .addStatement("conditions.add($L)", condition)
                    .endControlFlow();
            }
        }

        withFilters
            .beginControlFlow("if (conditions.isEmpty())")
            .addStatement("return new $T()", queryClass)
            .endControlFlow()
            .addStatement("return $T.query(new $T().andOperator(conditions))", queryClass, criteriaClass);

        TypeSpec spec = TypeSpec.classBuilder(model.entitySimpleName() + "Spec")
            .addModifiers(Modifier.PUBLIC)
            .addJavadoc("Generated by spring-xpose — do not edit.\n"
                + "MongoDB {@link $T} builder for filtering on "
                + "{@code GET /api/" + model.basePath() + "}.\n", queryClass)
            .addMethod(withFilters.build())
            .build();

        write(model, spec);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * Condition for an {@code @ElementCollection} field: IN → has any of (EXISTS subquery),
     * NOT_IN → has none of (NOT EXISTS, so empty collections match), IS_NULL → empty / not empty.
     */
    private static CodeBlock jpaCollectionCondition(FilterOperation op, String field, TypeName type, String getter) {
        return switch (op) {
            case IN      -> CodeBlock.of("$L(root, query, cb, f.$L())", field + "HasAny", getter);
            case NOT_IN  -> CodeBlock.of("cb.not($L(root, query, cb, f.$L()))", field + "HasAny", getter);
            case IS_NULL -> CodeBlock.of("f.$L() ? cb.isEmpty(root.<$T>get($S)) : cb.isNotEmpty(root.<$T>get($S))",
                                getter, type, field, type, field);
            default      -> throw new IllegalStateException(op + " on collection " + field);
        };
    }

    /**
     * {@code <field>HasAny}: EXISTS subquery joining the element collection. A subquery rather than a
     * join on the outer query, so a row matching several values is returned once and paging stays correct.
     */
    private static MethodSpec jpaHasAnyHelper(String field, String relatedIdField, ClassName entityClass) {
        ClassName criteria = ClassName.get("jakarta.persistence.criteria", "CriteriaQuery");
        ClassName builder  = ClassName.get("jakarta.persistence.criteria", "CriteriaBuilder");
        ClassName rootCls  = ClassName.get("jakarta.persistence.criteria", "Root");
        ClassName subquery = ClassName.get("jakarta.persistence.criteria", "Subquery");
        ClassName predicate = ClassName.get("jakarta.persistence.criteria", "Predicate");
        TypeName  rootType = ParameterizedTypeName.get(rootCls, entityClass);
        return MethodSpec.methodBuilder(field + "HasAny")
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
            .returns(predicate)
            .addParameter(rootType, "root")
            .addParameter(ParameterizedTypeName.get(criteria, WildcardTypeName.subtypeOf(Object.class)), "query")
            .addParameter(builder, "cb")
            .addParameter(ParameterizedTypeName.get(ClassName.get("java.util", "Collection"),
                WildcardTypeName.subtypeOf(Object.class)), "values")
            .addStatement("$T<$T> sub = query.subquery($T.class)", subquery, Integer.class, Integer.class)
            .addStatement("$T correlated = sub.correlate(root)", rootType)
            .addStatement("sub.select(cb.literal(1)).where($L.in(values))", relatedIdField == null
                ? CodeBlock.of("correlated.join($S)", field)
                : CodeBlock.of("correlated.join($S).get($S)", field, relatedIdField))
            .addStatement("return cb.exists(sub)")
            .build();
    }

    /** Condition under which a filter param applies: set, and for IN / NOT_IN also non-empty. */
    private static CodeBlock guard(FilterOperation op, String getter) {
        return op == FilterOperation.IN || op == FilterOperation.NOT_IN
            ? CodeBlock.of("f.$L() != null && !f.$L().isEmpty()", getter, getter)
            : CodeBlock.of("f.$L() != null", getter);
    }

    private void write(EntityModel model, TypeSpec spec) {
        try {
            JavaFile.builder(model.packageName() + ".generated", spec)
                .addFileComment("Generated by spring-xpose — do not edit")
                .build()
                .writeTo(processingEnv.getFiler());
        } catch (IOException e) {
            throw new RuntimeException("Failed to write Spec for " + model.entitySimpleName(), e);
        }
    }
}
