package io.github.notablogger.springxpose.processor.model;

import io.github.notablogger.springxpose.annotation.*;
import io.github.notablogger.springxpose.annotation.StoreType;
import io.github.notablogger.springxpose.annotation.ExposeDocument;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.MirroredTypeException;
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
    Set<String> ignoredFields,
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

            if (isId) { idFieldName = fieldName; idClassName = fieldType; }
            if (isVersion) { versionFieldName = fieldName; }

            if (isManyToOne || isOneToMany || isManyToMany || isOneToOne) {
                String relationType = isManyToOne || isOneToOne ? "SINGLE" : "COLLECTION";
                String relatedEntityType = resolveRelatedEntityType(field, env);
                relations.add(new RelationFieldModel(fieldName, fieldType, relationType, relatedEntityType));
            } else {
                // Collect jakarta.validation annotations for RequestDto generation
                List<? extends AnnotationMirror> validationAnnotations = field.getAnnotationMirrors().stream()
                    .filter(am -> am.getAnnotationType().toString().startsWith("jakarta.validation"))
                    .toList();
                fields.add(new FieldModel(fieldName, fieldType, isId, validationAnnotations));
            }
        }

        if (idFieldName == null) {
            env.getMessager().printMessage(Diagnostic.Kind.ERROR,
                "@ExposeEntity: no @Id field found on " + element.getSimpleName()
                + " — annotate the ID field with @jakarta.persistence.Id (JPA) or "
                + "@org.springframework.data.annotation.Id (MongoDB)", element);
            return null;
        }

        Set<Operation> operations  = new LinkedHashSet<>(Arrays.asList(annotation.expose()));
        Set<String> roles          = new LinkedHashSet<>(Arrays.asList(annotation.roles()));
        Set<String> readRoles      = new LinkedHashSet<>(Arrays.asList(annotation.readRoles()));
        Set<String> writeRoles     = new LinkedHashSet<>(Arrays.asList(annotation.writeRoles()));
        Set<String> ignoredFields  = new LinkedHashSet<>(Arrays.asList(annotation.ignoredFields()));

        return new EntityModel(
            qualifiedName, simpleName, packageName,
            idFieldName, idClassName, basePath,
            fields, relations, operations,
            annotation.relationMode(), annotation.authType(),
            roles, readRoles, writeRoles, ignoredFields,
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

        for (Element enclosed : element.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.FIELD) continue;
            VariableElement field = (VariableElement) enclosed;
            if (field.getModifiers().contains(Modifier.STATIC)) continue;

            String fieldName = field.getSimpleName().toString();
            String fieldType = field.asType().toString();

            boolean isId = field.getAnnotation(jakarta.persistence.Id.class) != null
                        || field.getAnnotation(org.springframework.data.annotation.Id.class) != null;

            if (isId) { idFieldName = fieldName; idClassName = fieldType; }

            List<? extends AnnotationMirror> validationAnnotations = field.getAnnotationMirrors().stream()
                .filter(am -> am.getAnnotationType().toString().startsWith("jakarta.validation"))
                .toList();
            fields.add(new FieldModel(fieldName, fieldType, isId, validationAnnotations));
        }

        if (idFieldName == null) {
            env.getMessager().printMessage(Diagnostic.Kind.ERROR,
                "@ExposeDocument: no @Id field found on " + element.getSimpleName()
                + " — annotate the ID field with @org.springframework.data.annotation.Id",
                element);
            return null;
        }

        Set<Operation> operations = new LinkedHashSet<>(Arrays.asList(annotation.expose()));
        Set<String> roles         = new LinkedHashSet<>(Arrays.asList(annotation.roles()));
        Set<String> readRoles     = new LinkedHashSet<>(Arrays.asList(annotation.readRoles()));
        Set<String> writeRoles    = new LinkedHashSet<>(Arrays.asList(annotation.writeRoles()));
        Set<String> ignoredFields = new LinkedHashSet<>(Arrays.asList(annotation.ignoredFields()));

        return new EntityModel(
            qualifiedName, simpleName, packageName,
            idFieldName, idClassName, basePath,
            fields, relations, operations,
            RelationMode.IDS_FOR_LIST_OBJECT_FOR_SINGLE, // not used for MONGO
            annotation.authType(),
            roles, readRoles, writeRoles, ignoredFields,
            customMapperClassName,
            annotation.pageable(),
            null, // no @Version for MongoDB documents
            storeType
        );
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
}
