package io.github.notablogger.springxpose.processor.model;

import io.github.notablogger.springxpose.annotation.*;
import io.github.notablogger.springxpose.annotation.StoreType;
import io.github.notablogger.springxpose.annotation.ExposeDocument;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.util.*;

import static io.github.notablogger.springxpose.processor.util.EntityNameUtils.toBasePath;

public record EntityModel(
    String entityClassName,
    String entitySimpleName,
    String packageName,
    String idFieldName,
    String idClassName,
    String basePath,
    List<FieldModel> fields,
    List<RelationFieldModel> relations,
    Set<Operation> operations,
    RelationMode relationMode,
    AuthType authType,
    Set<String> roles,
    Set<String> readRoles,
    Set<String> writeRoles,
    /** Fields marked {@code @ExposeField(hidden = true)} — left out of the response and request DTOs. */
    Set<String> hiddenFields,
    /** Fields declared filterable via {@code @ExposeField}, in declaration order. */
    List<FilterFieldModel> filters,
    /** Fully-qualified class name of the custom mapper, or {@code null} for the generated one. */
    String customMapperClassName,
    boolean pageable,
    /** Name of the {@code @Version} field, or {@code null} if none. */
    String versionFieldName,
    /** Persistence store type — JPA (default) or MONGO. */
    StoreType storeType
) {
    public static EntityModel parse(TypeElement element, ProcessingEnvironment env) {
        ExposeEntity exposeEntity   = element.getAnnotation(ExposeEntity.class);
        ExposeDocument exposeDocument = element.getAnnotation(ExposeDocument.class);

        if (exposeEntity != null) {
            return parseExposeEntity(element, exposeEntity, env);
        } else if (exposeDocument != null) {
            // Warn if @ExposeDocument is mistakenly placed on a JPA @Entity
            if (element.getAnnotation(jakarta.persistence.Entity.class) != null) {
                env.getMessager().printMessage(Diagnostic.Kind.WARNING,
                    "spring-xpose: @ExposeDocument is intended for MongoDB documents. "
                    + element.getSimpleName() + " is annotated with @jakarta.persistence.Entity "
                    + "— use @ExposeEntity for JPA entities.",
                    element);
            }
            return parseExposeDocument(element, exposeDocument, env);
        }
        return null;
    }

    private static EntityModel parseExposeEntity(TypeElement element, ExposeEntity annotation,
                                                  ProcessingEnvironment env) {
        String qualifiedName = element.getQualifiedName().toString();
        String simpleName    = element.getSimpleName().toString();
        String packageName   = qualifiedName.contains(".")
            ? qualifiedName.substring(0, qualifiedName.lastIndexOf('.')) : "";

        String basePath = annotation.path().isEmpty() ? toBasePath(simpleName) : annotation.path();
        String customMapperClassName = resolveCustomMapper(element, env,
            "io.github.notablogger.springxpose.annotation.ExposeEntity");
        StoreType storeType = StoreType.JPA; // @ExposeEntity is always JPA

        List<FieldModel> fields = new ArrayList<>();
        List<RelationFieldModel> relations = new ArrayList<>();
        String idFieldName = null;
        String idClassName = null;
        List<FilterFieldModel> filters = new ArrayList<>();
        Set<String> hiddenFields = new LinkedHashSet<>();
        boolean filtersValid = true;
        String versionFieldName = null;

        for (Element enclosed : element.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.FIELD) continue;
            VariableElement field = (VariableElement) enclosed;
            if (field.getModifiers().contains(Modifier.STATIC)) continue;

            String fieldName = field.getSimpleName().toString();
            String fieldType = field.asType().toString();

            // Detect @Id from both JPA and Spring Data (for MongoDB)
            boolean isId = field.getAnnotation(jakarta.persistence.Id.class) != null
                        || field.getAnnotation(org.springframework.data.annotation.Id.class) != null;
            // @Version — JPA only; Mongo uses its own versioning but we don't auto-detect it
            boolean isVersion = storeType == StoreType.JPA
                && field.getAnnotation(jakarta.persistence.Version.class) != null;

            // JPA relations (@ManyToOne etc.) — not applicable for MongoDB
            boolean isManyToOne  = false;
            boolean isOneToMany  = false;
            boolean isManyToMany = false;
            boolean isOneToOne   = false;
            if (storeType == StoreType.JPA) {
                isManyToOne  = field.getAnnotation(jakarta.persistence.ManyToOne.class)  != null;
                isOneToMany  = field.getAnnotation(jakarta.persistence.OneToMany.class)  != null;
                isManyToMany = field.getAnnotation(jakarta.persistence.ManyToMany.class) != null;
                isOneToOne   = field.getAnnotation(jakarta.persistence.OneToOne.class)   != null;
            }

            if (isHidden(field)) hiddenFields.add(fieldName);
            String relationType = isManyToOne || isOneToOne ? "SINGLE"
                : isOneToMany || isManyToMany ? "COLLECTION" : null;
            filtersValid &= collectFilter(field, relationType, storeType, filters, env);

            if (isId) { idFieldName = fieldName; idClassName = fieldType; }
            if (isVersion) { versionFieldName = fieldName; }

            if (relationType != null) {
                String relatedEntityType = resolveRelatedEntityType(field, env);
                relations.add(new RelationFieldModel(fieldName, fieldType, relationType, relatedEntityType));
            } else {
                // Collect jakarta.validation annotations for RequestDto generation
                List<? extends AnnotationMirror> validationAnnotations = field.getAnnotationMirrors().stream()
                    .filter(am -> am.getAnnotationType().toString().startsWith("jakarta.validation"))
                    .toList();
                fields.add(new FieldModel(fieldName, fieldType, isId, validationAnnotations, field.asType()));
            }
        }

        if (idFieldName == null) {
            env.getMessager().printMessage(Diagnostic.Kind.ERROR,
                "@ExposeEntity: no @Id field found on " + element.getSimpleName()
                + " — annotate the ID field with @jakarta.persistence.Id (JPA) or "
                + "@org.springframework.data.annotation.Id (MongoDB)", element);
            return null;
        }
        if (!filtersValid || !checkFilterNames(element, filters, env)) return null;

        Set<Operation> operations  = new LinkedHashSet<>(Arrays.asList(annotation.expose()));
        warnSearchMismatch(element, "@ExposeEntity", operations, filters, env);
        Set<String> roles          = new LinkedHashSet<>(Arrays.asList(annotation.roles()));
        Set<String> readRoles      = new LinkedHashSet<>(Arrays.asList(annotation.readRoles()));
        Set<String> writeRoles     = new LinkedHashSet<>(Arrays.asList(annotation.writeRoles()));

        return new EntityModel(
            qualifiedName, simpleName, packageName,
            idFieldName, idClassName, basePath,
            fields, relations, operations,
            annotation.relationMode(), annotation.authType(),
            roles, readRoles, writeRoles, hiddenFields,
            filters,
            customMapperClassName,
            annotation.pageable(),
            versionFieldName,
            storeType
        );
    }

    private static EntityModel parseExposeDocument(TypeElement element, ExposeDocument annotation,
                                                    ProcessingEnvironment env) {
        String qualifiedName = element.getQualifiedName().toString();
        String simpleName    = element.getSimpleName().toString();
        String packageName   = qualifiedName.contains(".")
            ? qualifiedName.substring(0, qualifiedName.lastIndexOf('.')) : "";

        String basePath = annotation.path().isEmpty() ? toBasePath(simpleName) : annotation.path();
        String customMapperClassName = resolveCustomMapper(element, env,
            "io.github.notablogger.springxpose.annotation.ExposeDocument");
        StoreType storeType = StoreType.MONGO; // always MONGO for @ExposeDocument

        List<FieldModel> fields = new ArrayList<>();
        List<RelationFieldModel> relations = new ArrayList<>(); // always empty for documents
        String idFieldName = null;
        String idClassName = null;
        List<FilterFieldModel> filters = new ArrayList<>();
        Set<String> hiddenFields = new LinkedHashSet<>();
        boolean filtersValid = true;

        for (Element enclosed : element.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.FIELD) continue;
            VariableElement field = (VariableElement) enclosed;
            if (field.getModifiers().contains(Modifier.STATIC)) continue;

            String fieldName = field.getSimpleName().toString();
            String fieldType = field.asType().toString();

            boolean isId = field.getAnnotation(jakarta.persistence.Id.class) != null
                        || field.getAnnotation(org.springframework.data.annotation.Id.class) != null;

            if (isId) { idFieldName = fieldName; idClassName = fieldType; }

            if (isHidden(field)) hiddenFields.add(fieldName);
            filtersValid &= collectFilter(field, null, storeType, filters, env);

            List<? extends AnnotationMirror> validationAnnotations = field.getAnnotationMirrors().stream()
                .filter(am -> am.getAnnotationType().toString().startsWith("jakarta.validation"))
                .toList();
            fields.add(new FieldModel(fieldName, fieldType, isId, validationAnnotations, field.asType()));
        }

        if (idFieldName == null) {
            env.getMessager().printMessage(Diagnostic.Kind.ERROR,
                "@ExposeDocument: no @Id field found on " + element.getSimpleName()
                + " — annotate the ID field with @org.springframework.data.annotation.Id",
                element);
            return null;
        }
        if (!filtersValid || !checkFilterNames(element, filters, env)) return null;

        Set<Operation> operations = new LinkedHashSet<>(Arrays.asList(annotation.expose()));
        warnSearchMismatch(element, "@ExposeDocument", operations, filters, env);
        Set<String> roles         = new LinkedHashSet<>(Arrays.asList(annotation.roles()));
        Set<String> readRoles     = new LinkedHashSet<>(Arrays.asList(annotation.readRoles()));
        Set<String> writeRoles    = new LinkedHashSet<>(Arrays.asList(annotation.writeRoles()));

        return new EntityModel(
            qualifiedName, simpleName, packageName,
            idFieldName, idClassName, basePath,
            fields, relations, operations,
            RelationMode.IDS_FOR_LIST_OBJECT_FOR_SINGLE, // not used for MONGO
            annotation.authType(),
            roles, readRoles, writeRoles, hiddenFields,
            filters,
            customMapperClassName,
            annotation.pageable(),
            null, // no @Version for MongoDB documents
            storeType
        );
    }

    private static boolean isHidden(VariableElement field) {
        ExposeField exposeField = field.getAnnotation(ExposeField.class);
        return exposeField != null && exposeField.hidden();
    }

    /**
     * Adds a {@link FilterFieldModel} for a field carrying {@code @ExposeField} with at least one
     * operation. Reports a compile error and returns {@code false} when an operation does not
     * fit the field (relation field, range operation on a non-comparable type, LIKE on a non-String,
     * a scalar operation on a collection, ...).
     */
    private static boolean collectFilter(VariableElement field, String relationType, StoreType storeType,
                                         List<FilterFieldModel> filters, ProcessingEnvironment env) {
        ExposeField exposeField = field.getAnnotation(ExposeField.class);
        if (exposeField == null || exposeField.filterOperations().length == 0) return true;

        if (exposeField.hidden()) {
            env.getMessager().printMessage(Diagnostic.Kind.ERROR,
                "@ExposeField: hidden field '" + field.getSimpleName() + "' cannot have filterOperations — "
                + "a filter would let clients guess the value the API hides", field);
            return false;
        }

        Set<FilterOperation> operations = EnumSet.noneOf(FilterOperation.class);
        operations.addAll(Arrays.asList(exposeField.filterOperations()));
        String fieldName = field.getSimpleName().toString();

        if (relationType != null) {
            return collectRelationFilter(field, fieldName, "SINGLE".equals(relationType), operations, filters, env);
        }

        TypeMirror type = field.asType();
        boolean isArray = type.getKind() == TypeKind.ARRAY;
        boolean isCollection = !isArray && isAssignableTo(type, "java.util.Collection", env);
        TypeMirror elementType = isCollection ? collectionElementType(type) : null;

        if (isCollection && elementType == null) {
            env.getMessager().printMessage(Diagnostic.Kind.ERROR,
                "@ExposeField: collection field '" + fieldName + "' needs a concrete element type, "
                + "e.g. List<String>", field);
            return false;
        }
        // Element-wise filtering joins the collection table. Without @ElementCollection the list lives in
        // one column (Hibernate's array mapping, a converter or JSON), which JPA queries can't look inside.
        if (isCollection && storeType == StoreType.JPA
                && field.getAnnotation(jakarta.persistence.ElementCollection.class) == null) {
            env.getMessager().printMessage(Diagnostic.Kind.ERROR,
                "@ExposeField: collection field '" + fieldName + "' must be an @ElementCollection to be filtered"
                + " — without it the list is stored in a single column (Hibernate's array mapping,"
                + " a converter or JSON), which these filters can't search element by element",
                field);
            return false;
        }

        boolean valid = true;
        for (FilterOperation op : operations) {
            String requirement;
            if (isCollection) {
                requirement = switch (op) {
                    case IN, NOT_IN, IS_NULL -> null;
                    default -> "does not apply to a collection — use IN (has any of) or NOT_IN (has none of)";
                };
            } else if (isArray) {
                requirement = op == FilterOperation.IS_NULL ? null
                    : "does not apply to an array — declare the field as a List to use IN / NOT_IN";
            } else {
                requirement = switch (op) {
                    case EQ, NE, IN, NOT_IN -> null;
                    case GT, GTE, LT, LTE -> isComparable(type, env) ? null : "requires a primitive or Comparable type";
                    case LIKE -> isString(type, env) ? null : "requires a String field";
                    case IS_NULL -> type.getKind().isPrimitive() ? "cannot apply to a primitive field, which is never null" : null;
                };
            }
            if (requirement != null) {
                env.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "@ExposeField: " + op + " is not supported on '" + fieldName + "' of type " + type
                    + " — " + op + " " + requirement,
                    field);
                valid = false;
            }
        }
        if (valid) filters.add(new FilterFieldModel(fieldName, fieldName, type, elementType, null, operations));
        return valid;
    }

    /**
     * Relation filters compare the related entity's id. To-one ({@code @ManyToOne}, owning {@code @OneToOne}):
     * {@code {"categoryId": 5}} with EQ, NE, IN, NOT_IN, IS_NULL. To-many ({@code @OneToMany}, {@code @ManyToMany}):
     * {@code {"tags_in": [1, 2]}} with IN (has any of), NOT_IN (has none of), IS_NULL (empty).
     */
    private static boolean collectRelationFilter(VariableElement field, String fieldName, boolean single,
                                                 Set<FilterOperation> operations,
                                                 List<FilterFieldModel> filters, ProcessingEnvironment env) {
        jakarta.persistence.OneToOne oneToOne = field.getAnnotation(jakarta.persistence.OneToOne.class);
        if (oneToOne != null && !oneToOne.mappedBy().isEmpty()) {
            env.getMessager().printMessage(Diagnostic.Kind.ERROR,
                "@ExposeField: relation field '" + fieldName + "' is the inverse side of a @OneToOne (mappedBy) — "
                + "it has no foreign key to filter on; filter on the owning side instead", field);
            return false;
        }

        TypeMirror relatedType = single ? field.asType() : collectionElementType(field.asType());
        Element related = relatedType == null ? null : env.getTypeUtils().asElement(relatedType);
        VariableElement relatedId = related instanceof TypeElement te ? findIdField(te, env) : null;
        if (relatedId == null) {
            env.getMessager().printMessage(Diagnostic.Kind.ERROR,
                "@ExposeField: relation field '" + fieldName + "' cannot be filtered — no single @Id field found on "
                + (relatedType == null ? "the related entity" : relatedType) + " (composite ids are not supported)",
                field);
            return false;
        }

        boolean valid = true;
        for (FilterOperation op : operations) {
            boolean fits = switch (op) {
                case EQ, NE -> single;
                case IN, NOT_IN, IS_NULL -> true;
                default -> false;
            };
            if (!fits) {
                env.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "@ExposeField: " + op + " is not supported on relation '" + fieldName + "' — relation filters "
                    + "compare ids: use " + (single ? "EQ, NE, IN, NOT_IN or IS_NULL"
                                                     : "IN (has any of), NOT_IN (has none of) or IS_NULL"),
                    field);
                valid = false;
            }
        }
        if (!valid) return false;

        String idName = relatedId.getSimpleName().toString();
        TypeMirror idType = relatedId.asType();
        filters.add(single
            ? new FilterFieldModel(fieldName, fieldName + "Id", idType, null, idName, operations)
            : new FilterFieldModel(fieldName, fieldName, field.asType(), idType, idName, operations));
        return true;
    }

    /** The {@code @Id} field of an entity, searching superclasses ({@code @MappedSuperclass}); {@code null} if none. */
    private static VariableElement findIdField(TypeElement type, ProcessingEnvironment env) {
        for (TypeElement current = type; current != null; ) {
            for (Element enclosed : current.getEnclosedElements()) {
                if (enclosed.getKind() == ElementKind.FIELD
                        && enclosed.getAnnotation(jakarta.persistence.Id.class) != null) {
                    return (VariableElement) enclosed;
                }
            }
            TypeMirror superclass = current.getSuperclass();
            current = superclass.getKind() == TypeKind.DECLARED
                ? (TypeElement) env.getTypeUtils().asElement(superclass) : null;
        }
        return null;
    }

    /** Element type of a {@code Collection<E>} field, or {@code null} for a raw or wildcard element type. */
    private static TypeMirror collectionElementType(TypeMirror type) {
        if (type instanceof DeclaredType dt && dt.getTypeArguments().size() == 1) {
            TypeMirror element = dt.getTypeArguments().get(0);
            if (element.getKind() == TypeKind.DECLARED) return element;
        }
        return null;
    }

    private static boolean isAssignableTo(TypeMirror type, String typeName, ProcessingEnvironment env) {
        TypeMirror target = env.getElementUtils().getTypeElement(typeName).asType();
        return env.getTypeUtils().isAssignable(
            env.getTypeUtils().erasure(type), env.getTypeUtils().erasure(target));
    }

    private static boolean isString(TypeMirror type, ProcessingEnvironment env) {
        TypeMirror string = env.getElementUtils().getTypeElement("java.lang.String").asType();
        return env.getTypeUtils().isSameType(type, string);
    }

    private static boolean isComparable(TypeMirror type, ProcessingEnvironment env) {
        if (type.getKind().isPrimitive()) return true;
        TypeMirror comparable = env.getElementUtils().getTypeElement("java.lang.Comparable").asType();
        return env.getTypeUtils().isAssignable(
            env.getTypeUtils().erasure(type), env.getTypeUtils().erasure(comparable));
    }

    /**
     * Warns when filters and {@link Operation#SEARCH} don't go together: filters without SEARCH
     * are never used, and SEARCH without filters returns every record.
     */
    private static void warnSearchMismatch(TypeElement element, String annotationName,
                                           Set<Operation> operations, List<FilterFieldModel> filters,
                                           ProcessingEnvironment env) {
        boolean search = operations.contains(Operation.SEARCH);
        if (!search && !filters.isEmpty()) {
            env.getMessager().printMessage(Diagnostic.Kind.WARNING,
                "spring-xpose: " + element.getSimpleName() + " declares @ExposeField filterOperations but "
                + annotationName + " does not expose Operation.SEARCH — the filters are not used. "
                + "Add SEARCH to expose, or remove the filterOperations.",
                element);
        } else if (search && filters.isEmpty()) {
            env.getMessager().printMessage(Diagnostic.Kind.WARNING,
                "spring-xpose: " + element.getSimpleName() + " exposes Operation.SEARCH but no field declares "
                + "@ExposeField(filterOperations = ...) — the search endpoint returns every record.",
                element);
        }
    }

    /**
     * Reports a compile error when two filter operations map to the same search key or
     * {@code SearchRequest} property — e.g. a field {@code priceGt} next to {@code price} with GT.
     */
    private static boolean checkFilterNames(TypeElement element, List<FilterFieldModel> filters,
                                            ProcessingEnvironment env) {
        Map<String, String> params = new HashMap<>();
        Map<String, String> properties = new HashMap<>();
        boolean valid = true;
        for (FilterFieldModel filter : filters) {
            for (FilterOperation op : filter.operations()) {
                String owner = filter.name() + " " + op;
                String clash = params.putIfAbsent(filter.keyName(op), owner);
                if (clash == null) clash = properties.putIfAbsent(filter.propertyName(op), owner);
                if (clash != null) {
                    env.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "@ExposeField: filter '" + owner + "' on " + element.getSimpleName()
                        + " clashes with '" + clash + "' — both map to search key '"
                        + filter.keyName(op) + "' / property '" + filter.propertyName(op) + "'",
                        element);
                    valid = false;
                }
            }
        }
        return valid;
    }

    /**
     * Reads {@code customMapper()} safely via AnnotationMirror to avoid
     * {@link MirroredTypeException}. Returns {@code null} when the default {@code void.class}
     * is set (meaning "use the generated mapper").
     * Works for both {@link ExposeEntity} and {@link ExposeDocument}.
     */
    private static String resolveCustomMapper(TypeElement element, ProcessingEnvironment env,
                                               String annotationFqcn) {
        for (AnnotationMirror am : element.getAnnotationMirrors()) {
            String annoType = am.getAnnotationType().toString();
            if (!annoType.equals(annotationFqcn)) continue;
            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry
                    : am.getElementValues().entrySet()) {
                if (!entry.getKey().getSimpleName().contentEquals("customMapper")) continue;
                Object value = entry.getValue().getValue();
                if (value instanceof TypeMirror tm) {
                    String typeName = tm.toString();
                    if ("void".equals(typeName)) return null;
                    return typeName;
                }
            }
        }
        return null; // default void.class — use generated mapper
    }

    private static String resolveRelatedEntityType(VariableElement field, ProcessingEnvironment env) {
        TypeMirror type = field.asType();
        if (type instanceof DeclaredType dt) {
            if (!dt.getTypeArguments().isEmpty()) {
                return dt.getTypeArguments().get(0).toString();
            }
            return dt.toString();
        }
        return type.toString();
    }

    /** True when a user-supplied mapper should be used instead of the generated one. */
    public boolean hasCustomMapper() {
        return customMapperClassName != null;
    }

    /** True when this entity has a {@code @Version} field. */
    public boolean hasVersion() {
        return versionFieldName != null;
    }

    /** True when at least one field declares {@code @ExposeField} operations. */
    public boolean hasFilters() {
        return filters != null && !filters.isEmpty();
    }

    /**
     * True when {@link Operation#SEARCH} is exposed: the {@code SearchRequest}, {@code Spec} and
     * {@code POST /search} endpoint are generated (with no filters the search matches everything).
     */
    public boolean searchable() {
        return operations.contains(Operation.SEARCH);
    }
}
