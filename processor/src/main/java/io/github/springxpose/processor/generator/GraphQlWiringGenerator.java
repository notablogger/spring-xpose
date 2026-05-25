package io.github.springxpose.processor.generator;

import com.squareup.javapoet.*;
import io.github.springxpose.annotation.Operation;
import io.github.springxpose.annotation.RelationMode;
import io.github.springxpose.processor.model.EntityModel;
import io.github.springxpose.processor.model.FieldModel;
import io.github.springxpose.processor.model.RelationFieldModel;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.util.Map;

public class GraphQlWiringGenerator {

    private final ProcessingEnvironment processingEnv;

    public GraphQlWiringGenerator(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
    }

    public void generate(EntityModel model) {
        generateWiring(model);
        generateSchema(model);
    }

    private void generateWiring(EntityModel model) {
        ClassName entityClass = ClassName.bestGuess(model.entityClassName());
        ClassName repositoryClass = ClassName.get(model.packageName(), model.entitySimpleName() + "Repository");
        ClassName mapClass = ClassName.get(Map.class);
        String entity = model.entitySimpleName();
        String entityVar = Character.toLowerCase(entity.charAt(0)) + entity.substring(1);

        MethodSpec.Builder configure = MethodSpec.methodBuilder("configure")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addParameter(ClassName.get("graphql.schema.idl", "RuntimeWiring").nestedClass("Builder"), "builder");

        if (model.operations().contains(Operation.FIND_ALL) || model.operations().contains(Operation.FIND_BY_ID)) {
            CodeBlock.Builder queryBlock = CodeBlock.builder()
                .add("builder.type(\"Query\", wiring -> wiring\n");
            boolean first = true;
            if (model.operations().contains(Operation.FIND_ALL)) {
                queryBlock.add("    .dataFetcher(\"findAll$L\", env -> repository.findAll())\n", entity);
                first = false;
            }
            if (model.operations().contains(Operation.FIND_BY_ID)) {
                queryBlock.add("    .dataFetcher(\"find$LById\", env -> repository.findById(\n        $T.valueOf(env.getArgument(\"id\").toString())\n    ).orElse(null))\n", entity, Long.class);
            }
            queryBlock.add(")");
            configure.addStatement(queryBlock.build());
        }

        boolean hasMutations = model.operations().contains(Operation.CREATE)
            || model.operations().contains(Operation.UPDATE)
            || model.operations().contains(Operation.DELETE);

        if (hasMutations) {
            CodeBlock.Builder mutBlock = CodeBlock.builder()
                .add("builder.type(\"Mutation\", wiring -> wiring\n");
            if (model.operations().contains(Operation.CREATE)) {
                mutBlock.add("    .dataFetcher(\"create$L\", env -> {\n        $T<String, Object> input = env.getArgument(\"input\");\n        return repository.save(mapToEntity(input));\n    })\n", entity, Map.class);
            }
            if (model.operations().contains(Operation.UPDATE)) {
                mutBlock.add("    .dataFetcher(\"update$L\", env -> {\n        $T<String, Object> input = env.getArgument(\"input\");\n        $T id = $T.valueOf(input.get(\"id\").toString());\n        if (!repository.existsById(id)) return null;\n        return repository.save(mapToEntity(input));\n    })\n", entity, Map.class, Long.class, Long.class);
            }
            if (model.operations().contains(Operation.DELETE)) {
                mutBlock.add("    .dataFetcher(\"delete$L\", env -> {\n        $T id = $T.valueOf(env.getArgument(\"id\").toString());\n        repository.deleteById(id);\n        return true;\n    })\n", entity, Long.class, Long.class);
            }
            mutBlock.add(")");
            configure.addStatement(mutBlock.build());
        }

        // mapToEntity helper
        MethodSpec.Builder mapToEntity = MethodSpec.methodBuilder("mapToEntity")
            .addModifiers(Modifier.PRIVATE)
            .returns(entityClass)
            .addParameter(ParameterizedTypeName.get(ClassName.get(Map.class), ClassName.get(String.class), ClassName.get(Object.class)), "input")
            .addStatement("$T entity = new $T()", entityClass, entityClass);

        for (FieldModel field : model.fields()) {
            if (field.isId()) continue;
            mapToEntity.beginControlFlow("if (input.containsKey($S))", field.name())
                .addStatement("entity.set$L(($T) input.get($S))", capitalize(field.name()), Object.class, field.name())
                .endControlFlow();
        }
        for (RelationFieldModel rel : model.relations()) {
            if ("SINGLE".equals(rel.relationType())) {
                String idKey = rel.name() + "Id";
                mapToEntity.beginControlFlow("if (input.containsKey($S))", idKey)
                    .addComment("Relation field: set by ID reference — full wiring requires repository lookup")
                    .endControlFlow();
            }
        }
        mapToEntity.addStatement("return entity");

        TypeSpec wiring = TypeSpec.classBuilder(entity + "GraphQlWiring")
            .addModifiers(Modifier.PUBLIC)
            .addJavadoc("Generated by spring-xpose — do not edit\n")
            .addAnnotation(ClassName.get("org.springframework.stereotype", "Component"))
            .addSuperinterface(ClassName.get("org.springframework.graphql.execution", "RuntimeWiringConfigurer"))
            .addField(FieldSpec.builder(repositoryClass, "repository", Modifier.PRIVATE, Modifier.FINAL).build())
            .addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(repositoryClass, "repository")
                .addStatement("this.repository = repository")
                .build())
            .addMethod(configure.build())
            .addMethod(mapToEntity.build())
            .build();

        try {
            JavaFile.builder(model.packageName() + ".generated", wiring)
                .addFileComment("Generated by spring-xpose — do not edit")
                .build()
                .writeTo(processingEnv.getFiler());
        } catch (IOException e) {
            throw new RuntimeException("Failed to write GraphQL wiring for " + model.entitySimpleName(), e);
        }
    }

    private void generateSchema(EntityModel model) {
        String entity = model.entitySimpleName();
        String entityLower = Character.toLowerCase(entity.charAt(0)) + entity.substring(1);

        StringBuilder sdl = new StringBuilder();
        sdl.append("type ").append(entity).append(" {\n");
        sdl.append("  id: ID\n");
        for (FieldModel field : model.fields()) {
            if (field.isId()) continue;
            sdl.append("  ").append(field.name()).append(": ").append(toGraphQlType(field.typeName())).append("\n");
        }
        for (RelationFieldModel rel : model.relations()) {
            if (model.relationMode() == RelationMode.ALWAYS_OBJECT) {
                String relatedSimple = simpleNameOf(rel.relatedEntityType());
                sdl.append("  ").append(rel.name()).append(": ").append(relatedSimple).append("\n");
            } else {
                sdl.append("  ").append(rel.name()).append("Id: ID\n");
            }
        }
        sdl.append("}\n\n");

        // Input types
        sdl.append("input ").append(entity).append("Input {\n");
        for (FieldModel field : model.fields()) {
            if (field.isId()) continue;
            sdl.append("  ").append(field.name()).append(": ").append(toGraphQlType(field.typeName())).append("!\n");
        }
        for (RelationFieldModel rel : model.relations()) {
            if ("SINGLE".equals(rel.relationType())) sdl.append("  ").append(rel.name()).append("Id: ID\n");
        }
        sdl.append("}\n\n");

        sdl.append("input ").append(entity).append("UpdateInput {\n");
        sdl.append("  id: ID!\n");
        for (FieldModel field : model.fields()) {
            if (field.isId()) continue;
            sdl.append("  ").append(field.name()).append(": ").append(toGraphQlType(field.typeName())).append("\n");
        }
        for (RelationFieldModel rel : model.relations()) {
            if ("SINGLE".equals(rel.relationType())) sdl.append("  ").append(rel.name()).append("Id: ID\n");
        }
        sdl.append("}\n\n");

        if (model.operations().contains(Operation.FIND_ALL) || model.operations().contains(Operation.FIND_BY_ID)) {
            sdl.append("extend type Query {\n");
            if (model.operations().contains(Operation.FIND_ALL))
                sdl.append("  findAll").append(entity).append("s: [").append(entity).append("]\n");
            if (model.operations().contains(Operation.FIND_BY_ID))
                sdl.append("  find").append(entity).append("ById(id: ID!): ").append(entity).append("\n");
            sdl.append("}\n\n");
        }

        boolean hasMutations = model.operations().contains(Operation.CREATE)
            || model.operations().contains(Operation.UPDATE)
            || model.operations().contains(Operation.DELETE);
        if (hasMutations) {
            sdl.append("extend type Mutation {\n");
            if (model.operations().contains(Operation.CREATE))
                sdl.append("  create").append(entity).append("(input: ").append(entity).append("Input!): ").append(entity).append("\n");
            if (model.operations().contains(Operation.UPDATE))
                sdl.append("  update").append(entity).append("(input: ").append(entity).append("UpdateInput!): ").append(entity).append("\n");
            if (model.operations().contains(Operation.DELETE))
                sdl.append("  delete").append(entity).append("(id: ID!): Boolean\n");
            sdl.append("}\n");
        }

        String sdlContent = sdl.toString();

        ClassName byteArrayResource = ClassName.get("org.springframework.core.io", "ByteArrayResource");
        ClassName graphQlSourceBuilderCustomizer = ClassName.get("org.springframework.graphql.execution", "GraphQlSource");

        MethodSpec customize = MethodSpec.methodBuilder("customize")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addParameter(ClassName.get("org.springframework.graphql.execution", "GraphQlSource").nestedClass("SchemaResourceBuilder"), "builder")
            .addStatement("builder.schemaResources(new $T($S.getBytes()))", byteArrayResource, sdlContent)
            .build();

        TypeSpec schema = TypeSpec.classBuilder(entity + "GraphQlSchema")
            .addModifiers(Modifier.PUBLIC)
            .addJavadoc("Generated by spring-xpose — do not edit\n")
            .addAnnotation(ClassName.get("org.springframework.stereotype", "Component"))
            .addSuperinterface(ClassName.get("org.springframework.graphql.execution", "GraphQlSourceBuilderCustomizer"))
            .addMethod(customize)
            .build();

        try {
            JavaFile.builder(model.packageName() + ".generated", schema)
                .addFileComment("Generated by spring-xpose — do not edit")
                .build()
                .writeTo(processingEnv.getFiler());
        } catch (IOException e) {
            throw new RuntimeException("Failed to write GraphQL schema for " + model.entitySimpleName(), e);
        }
    }

    private static String toGraphQlType(String javaType) {
        return switch (javaType) {
            case "java.lang.String", "String" -> "String";
            case "java.lang.Integer", "int", "Integer" -> "Int";
            case "java.lang.Long", "long", "Long" -> "ID";
            case "java.lang.Boolean", "boolean", "Boolean" -> "Boolean";
            case "java.math.BigDecimal", "double", "float", "java.lang.Double", "java.lang.Float" -> "Float";
            default -> "String";
        };
    }

    private static String simpleNameOf(String fqn) {
        int dot = fqn.lastIndexOf('.');
        return dot >= 0 ? fqn.substring(dot + 1) : fqn;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}

