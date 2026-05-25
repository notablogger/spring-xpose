package com.notablogger.springxpose.processor.model;

public record RelationFieldModel(
    String name,
    String typeName,
    String relationType,   // "SINGLE" or "COLLECTION"
    String relatedEntityType
) {}

