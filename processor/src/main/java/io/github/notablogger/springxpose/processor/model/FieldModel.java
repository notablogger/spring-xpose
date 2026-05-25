package io.github.notablogger.springxpose.processor.model;

public record FieldModel(
    String name,
    String typeName,
    boolean isId
) {}

