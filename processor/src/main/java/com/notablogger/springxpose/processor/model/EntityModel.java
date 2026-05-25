package com.notablogger.springxpose.processor.model;

import com.notablogger.springxpose.annotation.*;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.util.*;

import static com.notablogger.springxpose.processor.util.EntityNameUtils.toBasePath;

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
    Set<String> writeRoles
) {
    public static EntityModel parse(TypeElement element, ProcessingEnvironment env) {
        ExposeEntity annotation = element.getAnnotation(ExposeEntity.class);

        String qualifiedName = element.getQualifiedName().toString();
        String simpleName = element.getSimpleName().toString();
        String packageName = qualifiedName.contains(".")
            ? qualifiedName.substring(0, qualifiedName.lastIndexOf('.'))
            : "";

        String basePath = annotation.path().isEmpty() ? toBasePath(simpleName) : annotation.path();

        // Collect fields
        List<FieldModel> fields = new ArrayList<>();
        List<RelationFieldModel> relations = new ArrayList<>();
        String idFieldName = null;
        String idClassName = null;

        for (Element enclosed : element.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.FIELD) continue;
            VariableElement field = (VariableElement) enclosed;
            if (field.getModifiers().contains(Modifier.STATIC)) continue;

            String fieldName = field.getSimpleName().toString();
            String fieldType = field.asType().toString();

            boolean isId = field.getAnnotation(jakarta.persistence.Id.class) != null;
            boolean isManyToOne = field.getAnnotation(jakarta.persistence.ManyToOne.class) != null;
            boolean isOneToMany = field.getAnnotation(jakarta.persistence.OneToMany.class) != null;
            boolean isManyToMany = field.getAnnotation(jakarta.persistence.ManyToMany.class) != null;
            boolean isOneToOne = field.getAnnotation(jakarta.persistence.OneToOne.class) != null;

            if (isId) {
                idFieldName = fieldName;
                idClassName = fieldType;
            }

            if (isManyToOne || isOneToMany || isManyToMany || isOneToOne) {
                String relationType = isManyToOne || isOneToOne ? "SINGLE" : "COLLECTION";
                // Get the simple entity name for relation
                String relatedEntityType = resolveRelatedEntityType(field, env);
                relations.add(new RelationFieldModel(fieldName, fieldType, relationType, relatedEntityType));
            } else {
                fields.add(new FieldModel(fieldName, fieldType, isId));
            }
        }

        if (idFieldName == null) {
            env.getMessager().printMessage(
                Diagnostic.Kind.ERROR,
                "@ExposeEntity: no @Id field found on " + element.getSimpleName(),
                element
            );
            return null;
        }

        Set<Operation> operations = new LinkedHashSet<>(Arrays.asList(annotation.expose()));
        Set<String> roles = new LinkedHashSet<>(Arrays.asList(annotation.roles()));
        Set<String> readRoles = new LinkedHashSet<>(Arrays.asList(annotation.readRoles()));
        Set<String> writeRoles = new LinkedHashSet<>(Arrays.asList(annotation.writeRoles()));

        return new EntityModel(
            qualifiedName, simpleName, packageName,
            idFieldName, idClassName, basePath,
            fields, relations, operations,
            annotation.relationMode(), annotation.authType(),
            roles, readRoles, writeRoles
        );
    }

    private static String resolveRelatedEntityType(VariableElement field, ProcessingEnvironment env) {
        TypeMirror type = field.asType();
        if (type instanceof DeclaredType dt) {
            if (!dt.getTypeArguments().isEmpty()) {
                // Collection<RelatedEntity> — grab first type arg
                return dt.getTypeArguments().get(0).toString();
            }
            return dt.toString();
        }
        return type.toString();
    }
}

