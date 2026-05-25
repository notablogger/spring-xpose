package io.github.springxpose.processor.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EntityNameUtilsTest {

    @Test
    void pluralizesRegularNouns() {
        assertEquals("products", EntityNameUtils.pluralize("Product"));
        assertEquals("orders", EntityNameUtils.pluralize("Order"));
        assertEquals("widgets", EntityNameUtils.pluralize("Widget"));
    }

    @Test
    void pluralizesNounsEndingInY() {
        assertEquals("companies", EntityNameUtils.pluralize("Company"));
        assertEquals("categories", EntityNameUtils.pluralize("Category"));
    }

    @Test
    void pluralizesNounsEndingInVowelY() {
        assertEquals("days", EntityNameUtils.pluralize("Day"));
        assertEquals("keys", EntityNameUtils.pluralize("Key"));
        assertEquals("boys", EntityNameUtils.pluralize("Boy"));
    }

    @Test
    void pluralizesNounsEndingInSXZChSh() {
        assertEquals("statuses", EntityNameUtils.pluralize("Status"));
        assertEquals("boxes", EntityNameUtils.pluralize("Box"));
        assertEquals("churches", EntityNameUtils.pluralize("Church"));
        assertEquals("dishes", EntityNameUtils.pluralize("Dish"));
    }

    @Test
    void pluralizesPerson() {
        assertEquals("people", EntityNameUtils.pluralize("Person"));
    }

    @Test
    void toBasePathLowercasesAndPluralizesName() {
        assertEquals("products", EntityNameUtils.toBasePath("Product"));
        assertEquals("categories", EntityNameUtils.toBasePath("Category"));
    }

    @Test
    void toLowerCamelHandlesNormalNames() {
        assertEquals("product", EntityNameUtils.toLowerCamel("Product"));
        assertEquals("orderItem", EntityNameUtils.toLowerCamel("OrderItem"));
    }

    @Test
    void toLowerCamelHandlesNullAndEmpty() {
        assertEquals("", EntityNameUtils.toLowerCamel(""));
    }
}

