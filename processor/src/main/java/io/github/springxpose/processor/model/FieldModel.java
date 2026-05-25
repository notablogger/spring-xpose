package io.github.springxpose.processor.model;

public record FieldModel(
    String name,
    String typeName,
    boolean isId
) {}

