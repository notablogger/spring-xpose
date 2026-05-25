package com.notablogger.springxpose.processor.model;

public record FieldModel(
    String name,
    String typeName,
    boolean isId
) {}

