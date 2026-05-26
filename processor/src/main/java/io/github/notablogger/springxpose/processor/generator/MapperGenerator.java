package io.github.notablogger.springxpose.processor.generator;

import com.squareup.javapoet.*;
import io.github.notablogger.springxpose.annotation.RelationMode;
import io.github.notablogger.springxpose.processor.model.EntityModel;
import io.github.notablogger.springxpose.processor.model.RelationFieldModel;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.util.List;

/**
 * Generates a MapStruct mapper interface for each @ExposeEntity-annotated entity.
 *
 * <p><b>Generated methods:</b>
 * <ul>
 *   <li>{@code toDto(Entity)} → response DTO</li>
 *   <li>{@code toDtoList(List<Entity>)} → list of response DTOs</li>
 *   <li>{@code toEntity(RequestDto)} → entity (for CREATE / UPDATE)</li>
 * </ul>
 *
 * <p><b>Custom mapper:</b> When {@code @ExposeEntity(customMapper = MyMapper.class)} is set,
 * the {@code @Mapper} annotation is omitted so MapStruct does <em>not</em> generate an
 * implementation. The interface is still generated as a contract; the user's Spring
 * {@code @Component}-annotated bean is injected into the controller instead.
 */
public class MapperGenerator {

    private final ProcessingEnvironment processingEnv;

    public MapperGenerator(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
    }

    public void generate(EntityModel model) {
        String entity        = model.entitySimpleName();
        String dtoName       = entity + "Dto";
        String requestDtoName= entity + "RequestDto";
        String mapperName    = entity + "Mapper";

        ClassName entityClass     = ClassName.bestGuess(model.entityClassName());
        ClassName dtoClass        = ClassName.get(model.packageName() + ".generated", dtoName);
        ClassName requestDtoClass = ClassName.get(model.packageName() + ".generated", requestDtoName);
        ClassName mapperAnnot     = ClassName.get("org.mapstruct", "Mapper");
        ClassName mappingAnnot    = ClassName.get("org.mapstruct", "Mapping");
        ClassName listClass       = ClassName.get("java.util", "List");

        TypeSpec.Builder mapper = TypeSpec.interfaceBuilder(mapperName)
            .addModifiers(Modifier.PUBLIC)
            .addJavadoc(
                "Generated MapStruct mapper contract for {@link $L} — do not edit.\n"
                    + "<p>Implement this interface with a custom Spring bean via "
                    + "{@code @ExposeEntity(customMapper = YourMapper.class)}, or let spring-xpose "
                    + "generate the MapStruct implementation automatically.\n",
                entity);

        if (!model.hasCustomMapper()) {
            mapper.addAnnotation(AnnotationSpec.builder(mapperAnnot)
                .addMember("componentModel", "$S", "spring")
                .build());
        }

        // ── toDto ──
        MethodSpec.Builder toDto = MethodSpec.methodBuilder("toDto")
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .addParameter(entityClass, "entity")
            .returns(dtoClass);

        for (RelationFieldModel rel : model.relations()) {
            if ("COLLECTION".equals(rel.relationType())) continue;
            if (model.ignoredFields().contains(rel.name())) continue;
            if (model.relationMode() != RelationMode.ALWAYS_OBJECT) {
                toDto.addAnnotation(AnnotationSpec.builder(mappingAnnot)
                    .addMember("source", "$S", rel.name() + ".id")
                    .addMember("target", "$S", rel.name() + "Id")
                    .build());
            }
        }
        mapper.addMethod(toDto.build());

        // ── toDtoList ──
        TypeName entityListType = ParameterizedTypeName.get(listClass, entityClass);
        TypeName dtoListType    = ParameterizedTypeName.get(listClass, dtoClass);
        mapper.addMethod(MethodSpec.methodBuilder("toDtoList")
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .addParameter(entityListType, "entities")
            .returns(dtoListType)
            .build());

        // ── toEntity (CREATE — RequestDto → new Entity) ──
        MethodSpec.Builder toEntity = MethodSpec.methodBuilder("toEntity")
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .addParameter(requestDtoClass, "requestDto")
            .returns(entityClass);

        // id is never in RequestDto — tell MapStruct to ignore it explicitly
        toEntity.addAnnotation(AnnotationSpec.builder(mappingAnnot)
            .addMember("target", "$S", model.idFieldName())
            .addMember("ignore", "$L", true)
            .build());
        // @Version field is managed by JPA — never map from RequestDto
        if (model.hasVersion()) {
            toEntity.addAnnotation(AnnotationSpec.builder(mappingAnnot)
                .addMember("target", "$S", model.versionFieldName())
                .addMember("ignore", "$L", true)
                .build());
        }
        // ignoredFields are absent from RequestDto — must be explicitly ignored so MapStruct
        // does not warn about unmapped target properties
        for (String ignored : model.ignoredFields()) {
            toEntity.addAnnotation(AnnotationSpec.builder(mappingAnnot)
                .addMember("target", "$S", ignored)
                .addMember("ignore", "$L", true)
                .build());
        }
        // Relation objects are resolved from IDs by the controller, not by MapStruct
        for (RelationFieldModel rel : model.relations()) {
            if ("COLLECTION".equals(rel.relationType())) continue;
            if (model.ignoredFields().contains(rel.name())) continue;
            toEntity.addAnnotation(AnnotationSpec.builder(mappingAnnot)
                .addMember("target", "$S", rel.name())
                .addMember("ignore", "$L", true)
                .build());
        }
        mapper.addMethod(toEntity.build());

        // ── updateEntity (UPDATE — merges RequestDto into existing Entity via @MappingTarget) ──
        // This is the key for safe PUT: load the entity from DB, then overlay only the fields
        // present in the request. JPA-managed fields (id, @Version) and relations are ignored
        // here; the controller handles them separately.
        ClassName mappingTargetAnnot = ClassName.get("org.mapstruct", "MappingTarget");
        MethodSpec.Builder updateEntity = MethodSpec.methodBuilder("updateEntity")
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .addParameter(requestDtoClass, "requestDto")
            .addParameter(ParameterSpec.builder(entityClass, "entity")
                .addAnnotation(mappingTargetAnnot)
                .build())
            .returns(TypeName.VOID);

        // Ignore id — managed by JPA
        updateEntity.addAnnotation(AnnotationSpec.builder(mappingAnnot)
            .addMember("target", "$S", model.idFieldName())
            .addMember("ignore", "$L", true)
            .build());
        // Ignore @Version — managed by JPA
        if (model.hasVersion()) {
            updateEntity.addAnnotation(AnnotationSpec.builder(mappingAnnot)
                .addMember("target", "$S", model.versionFieldName())
                .addMember("ignore", "$L", true)
                .build());
        }
        // ignoredFields are absent from RequestDto — explicitly ignored to suppress MapStruct warnings
        for (String ignored : model.ignoredFields()) {
            updateEntity.addAnnotation(AnnotationSpec.builder(mappingAnnot)
                .addMember("target", "$S", ignored)
                .addMember("ignore", "$L", true)
                .build());
        }
        // Relation objects — controller resolves them from relation IDs
        for (RelationFieldModel rel : model.relations()) {
            if ("COLLECTION".equals(rel.relationType())) continue;
            if (model.ignoredFields().contains(rel.name())) continue;
            updateEntity.addAnnotation(AnnotationSpec.builder(mappingAnnot)
                .addMember("target", "$S", rel.name())
                .addMember("ignore", "$L", true)
                .build());
        }
        mapper.addMethod(updateEntity.build());

        try {
            JavaFile.builder(model.packageName() + ".generated", mapper.build())
                .addFileComment("Generated by spring-xpose — do not edit")
                .build()
                .writeTo(processingEnv.getFiler());
        } catch (IOException e) {
            throw new RuntimeException("Failed to write mapper for " + entity, e);
        }
    }
}
