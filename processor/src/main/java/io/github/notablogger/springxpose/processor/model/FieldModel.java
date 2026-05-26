package io.github.notablogger.springxpose.processor.model;

import javax.lang.model.element.AnnotationMirror;
import java.util.List;

public record FieldModel(
    String name,
    String typeName,
    boolean isId,
    List<? extends AnnotationMirror> validationAnnotations
) {}

