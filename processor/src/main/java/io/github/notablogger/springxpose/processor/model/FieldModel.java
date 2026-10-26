package io.github.notablogger.springxpose.processor.model;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.type.TypeMirror;
import java.util.List;

public record FieldModel(
    String name,
    String typeName,
    boolean isId,
    List<? extends AnnotationMirror> validationAnnotations,
    /** The field's type as the compiler sees it — use this to emit types, it keeps generics and arrays. */
    TypeMirror type
) {}

