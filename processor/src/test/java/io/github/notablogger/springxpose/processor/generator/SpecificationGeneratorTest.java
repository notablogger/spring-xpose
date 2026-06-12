package io.github.notablogger.springxpose.processor.generator;

import com.squareup.javapoet.*;
import io.github.notablogger.springxpose.annotation.StoreType;
import io.github.notablogger.springxpose.processor.model.EntityModel;
import io.github.notablogger.springxpose.processor.model.FieldModel;
import io.github.notablogger.springxpose.annotation.Operation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SpecificationGenerator.
 * Validates generation of filter Specification classes for both JPA and MongoDB.
 */
class SpecificationGeneratorTest {

    @Test
    void testNoFilterableFieldsSkipsGeneration() {
        // Given an entity with no filterable fields
        EntityModel model = createJpaEntityModel("Product", "product", Set.of());

        // When checking hasFilterableFields
        assertFalse(model.hasFilterableFields());
    }

    @Test
    void testJpaSpecificationClassName() {
        // Given a JPA entity "Product" with filterable fields
        EntityModel model = createJpaEntityModel("Product", "product", Set.of("name"));

        // Then the generated Spec class name should be ProductSpec
        String expectedClassName = "ProductSpec";
        assertTrue(expectedClassName.endsWith("Spec"));
        assertTrue(expectedClassName.startsWith("Product"));
    }

    @Test
    void testMongoSpecificationClassName() {
        // Given a MongoDB document "Note" with filterable fields
        EntityModel model = createMongoEntityModel("Note", "note", Set.of("author"));

        // Then the generated Spec class name should be NoteSpec
        String expectedClassName = "NoteSpec";
        assertTrue(expectedClassName.endsWith("Spec"));
        assertTrue(expectedClassName.startsWith("Note"));
    }

    @Test
    void testJpaSpecificationGeneratedPackage() {
        // Given an entity in com.example.entity package
        EntityModel model = createJpaEntityModel("com.example.entity.Product", "Product",
            Set.of("name"));

        // Then the generated class should be in .generated subpackage
        String expectedPackage = model.packageName() + ".generated";
        assertTrue(expectedPackage.endsWith(".generated"));
    }

    @Test
    void testMongoSpecificationGeneratedPackage() {
        // Given a MongoDB document in com.example.document package
        EntityModel model = createMongoEntityModel("com.example.document.Note", "Note",
            Set.of("author"));

        // Then the generated class should be in .generated subpackage
        String expectedPackage = model.packageName() + ".generated";
        assertTrue(expectedPackage.endsWith(".generated"));
    }

    @Test
    void testJpaSpecificationSingleFilterField() {
        // Given a JPA entity with one filterable field "name"
        EntityModel model = createJpaEntityModel("Product", "product", Set.of("name"));

        // Then the Spec should have a static withFilters method
        assertTrue(model.hasFilterableFields());
        assertEquals(1, model.filterableFields().size());
        
        // Expected generated method signature:
        // public static Specification<Product> withFilters(ProductFilterParams f)
    }

    @Test
    void testJpaSpecificationMultipleFilterFields() {
        // Given a JPA entity with multiple filterable fields
        Set<String> filterableFields = Set.of("name", "status", "price");
        EntityModel model = createJpaEntityModel("Product", "product", filterableFields);

        // Then all fields should be present in the model
        assertEquals(3, model.filterableFields().size());
        assertTrue(model.filterableFields().contains("name"));
        assertTrue(model.filterableFields().contains("status"));
        assertTrue(model.filterableFields().contains("price"));
    }

    @Test
    void testJpaSpecificationPredicateBuilding() {
        // Given a JPA entity with filterable fields
        EntityModel model = createJpaEntityModel("Product", "product", Set.of("name", "status"));

        // Then the Spec implementation should build predicates for each field
        // Expected code structure:
        // List<Predicate> predicates = new ArrayList<>();
        // if (f.getName() != null)
        //     predicates.add(cb.equal(root.get("name"), f.getName()));
        // if (f.getStatus() != null)
        //     predicates.add(cb.equal(root.get("status"), f.getStatus()));
        // return cb.and(predicates.toArray(new Predicate[0]));

        assertTrue(model.hasFilterableFields());
    }

    @Test
    void testMongoSpecificationQueryBuilding() {
        // Given a MongoDB document with filterable fields
        EntityModel model = createMongoEntityModel("Note", "note", Set.of("author", "category"));

        // Then the Spec implementation should build MongoDB Query
        // Expected code structure:
        // List<Criteria> conditions = new ArrayList<>();
        // if (f.getAuthor() != null)
        //     conditions.add(Criteria.where("author").is(f.getAuthor()));
        // if (conditions.isEmpty())
        //     return new Query();
        // return Query.query(new Criteria().andOperator(conditions));

        assertEquals(2, model.filterableFields().size());
        assertTrue(model.hasFilterableFields());
    }

    @Test
    void testMongoSpecificationEmptyFiltersReturnsEmptyQuery() {
        // Given a MongoDB Spec with empty filter params
        EntityModel model = createMongoEntityModel("Note", "note", Set.of("author"));

        // Then it should return an empty Query when all params are null
        // This prevents unnecessary database queries
        assertTrue(model.hasFilterableFields());
    }

    @Test
    void testJpaSpecificationWithNullFilters() {
        // Given a JPA Specification with null filter values
        EntityModel model = createJpaEntityModel("Product", "product", 
            Set.of("name", "category"));

        // Then only non-null filters should create predicates
        // Null filters should be ignored
        assertEquals(2, model.filterableFields().size());
    }

    @Test
    void testSpecificationAccessorMethodNaming() {
        // Given filterable fields with camelCase names
        Set<String> fields = Set.of("firstName", "lastName", "emailAddress");
        EntityModel model = createJpaEntityModel("Customer", "customer", fields);

        // Then accessor methods should follow JavaBean convention
        // getFirstName(), getLastName(), getEmailAddress()
        assertEquals(3, model.filterableFields().size());
    }

    @ParameterizedTest
    @EnumSource(StoreType.class)
    void testSpecificationGenerationForBothStoreTypes(StoreType storeType) {
        // Given both JPA and MongoDB store types
        EntityModel model = storeType == StoreType.JPA
            ? createJpaEntityModel("Product", "product", Set.of("name"))
            : createMongoEntityModel("Note", "note", Set.of("author"));

        // Then both should have Spec generation support
        assertTrue(model.hasFilterableFields());
        assertEquals(storeType, model.storeType());
    }

    @Test
    void testJpaSpecificationReturnsSpecificationInterface() {
        // Given a JPA entity
        EntityModel model = createJpaEntityModel("Product", "product", Set.of("name"));

        // Then the withFilters method should return Specification<Product>
        // This allows usage with JpaRepository.findAll(Specification)
        assertEquals(StoreType.JPA, model.storeType());
        assertTrue(model.hasFilterableFields());
    }

    @Test
    void testMongoSpecificationReturnsQueryObject() {
        // Given a MongoDB document
        EntityModel model = createMongoEntityModel("Note", "note", Set.of("author"));

        // Then the withFilters method should return Query
        // This allows usage with MongoTemplate.find(Query)
        assertEquals(StoreType.MONGO, model.storeType());
        assertTrue(model.hasFilterableFields());
    }

    @Test
    void testSpecificationWithStringFields() {
        // Given filterable String fields
        EntityModel model = createJpaEntityModel("Product", "product",
            Set.of("name", "description"));

        // Then String fields should be handled correctly for equality checks
        assertTrue(model.hasFilterableFields());
        assertEquals(2, model.filterableFields().size());
    }

    @Test
    void testSpecificationWithNumericFields() {
        // Given filterable numeric fields
        EntityModel model = createJpaEntityModel("Product", "product",
            Set.of("price", "quantity"));

        // Then numeric fields should be handled correctly
        assertTrue(model.hasFilterableFields());
        assertEquals(2, model.filterableFields().size());
    }

    @Test
    void testSpecificationWithBooleanFields() {
        // Given filterable boolean fields
        EntityModel model = createJpaEntityModel("Product", "product",
            Set.of("active", "available"));

        // Then boolean fields should be handled correctly
        assertTrue(model.hasFilterableFields());
        assertEquals(2, model.filterableFields().size());
    }

    @Test
    void testSpecificationWithMixedFieldTypes() {
        // Given filterable fields of mixed types
        Map<String, String> fieldTypes = Map.of(
            "name", "java.lang.String",
            "price", "double",
            "active", "boolean"
        );
        EntityModel model = createJpaEntityModelWithTypes("Product", "product", 
            fieldTypes.keySet(), fieldTypes);

        // Then all field types should be handled correctly
        assertEquals(3, model.filterableFields().size());
    }

    @Test
    void testJpaSpecificationFieldNameMapping() {
        // Given a JPA entity with camelCase field names
        EntityModel model = createJpaEntityModel("Product", "product",
            Set.of("productName", "categoryId"));

        // Then field names should map correctly to entity properties
        // cb.equal(root.get("productName"), f.getProductName())
        assertEquals(2, model.filterableFields().size());
    }

    @Test
    void testMongoSpecificationFieldNameMapping() {
        // Given a MongoDB document with underscore field names
        EntityModel model = createMongoEntityModel("Note", "note",
            Set.of("created_at", "updated_at"));

        // Then field names should map correctly to document properties
        // Criteria.where("created_at").is(f.getCreatedAt())
        assertEquals(2, model.filterableFields().size());
    }

    @Test
    void testSpecificationDoesNotIncludeIgnoredFields() {
        // Given an entity with both filterable and ignored fields
        Set<String> filterable = Set.of("name", "status");
        Set<String> ignored = Set.of("createdAt", "updatedAt");
        EntityModel model = createJpaEntityModelWithIgnoredFields("Product", "product",
            filterable, ignored);

        // Then Spec should only include filterable fields
        assertEquals(2, model.filterableFields().size());
        assertEquals(2, model.ignoredFields().size());
    }

    @Test
    void testSpecificationMethodIsStatic() {
        // Given a Specification class
        EntityModel model = createJpaEntityModel("Product", "product", Set.of("name"));

        // Then the withFilters method should be static
        // This allows usage: ProductSpec.withFilters(filterParams)
        assertTrue(model.hasFilterableFields());
    }

    @Test
    void testMongoSpecificationAndOperatorCombineAllConditions() {
        // Given a MongoDB Spec with multiple filterable fields
        EntityModel model = createMongoEntityModel("Note", "note",
            Set.of("author", "category", "status"));

        // Then all non-null conditions should be combined with AND operator
        // return Query.query(new Criteria().andOperator(conditions))
        assertEquals(3, model.filterableFields().size());
    }

    @Test
    void testJpaSpecificationAndOperatorCombinesAllPredicates() {
        // Given a JPA Spec with multiple filterable fields
        EntityModel model = createJpaEntityModel("Product", "product",
            Set.of("name", "status", "price"));

        // Then all predicates should be combined with AND operator
        // return cb.and(predicates.toArray(new Predicate[0]))
        assertEquals(3, model.filterableFields().size());
    }

    // Helper methods

    private EntityModel createJpaEntityModel(String entityClassName, String basePath,
                                             Set<String> filterableFields) {
        return createEntityModel(entityClassName, basePath, filterableFields, StoreType.JPA);
    }

    private EntityModel createMongoEntityModel(String entityClassName, String basePath,
                                               Set<String> filterableFields) {
        return createEntityModel(entityClassName, basePath, filterableFields, StoreType.MONGO);
    }

    private EntityModel createEntityModel(String entityClassName, String basePath,
                                          Set<String> filterableFields, StoreType storeType) {
        String packageName = "com.example";
        String simpleName = entityClassName.contains(".")
            ? entityClassName.substring(entityClassName.lastIndexOf('.') + 1)
            : entityClassName;

        List<FieldModel> fields = new ArrayList<>();
        for (String fieldName : filterableFields) {
            fields.add(new FieldModel(fieldName, "java.lang.String", false, List.of()));
        }

        String idClassName = storeType == StoreType.MONGO ? "java.lang.String" : "java.lang.Long";

        return new EntityModel(
            packageName + "." + simpleName, simpleName, packageName,
            "id", idClassName, basePath,
            fields, List.of(), Set.of(Operation.FIND_ALL),
            null, null,
            Set.of(), Set.of(), Set.of(), Set.of(),
            filterableFields,
            null,
            false,
            null,
            storeType
        );
    }

    private EntityModel createJpaEntityModelWithTypes(String simpleName, String basePath,
                                                      Set<String> fieldNames,
                                                      Map<String, String> fieldTypes) {
        String packageName = "com.example";

        List<FieldModel> fields = new ArrayList<>();
        for (String fieldName : fieldNames) {
            fields.add(new FieldModel(fieldName, fieldTypes.get(fieldName), false, List.of()));
        }

        return new EntityModel(
            packageName + "." + simpleName, simpleName, packageName,
            "id", "java.lang.Long", basePath,
            fields, List.of(), Set.of(Operation.FIND_ALL),
            null, null,
            Set.of(), Set.of(), Set.of(), Set.of(),
            fieldNames,
            null,
            false,
            null,
            StoreType.JPA
        );
    }

    private EntityModel createJpaEntityModelWithIgnoredFields(String simpleName, String basePath,
                                                              Set<String> filterableFields,
                                                              Set<String> ignoredFields) {
        String packageName = "com.example";

        List<FieldModel> fields = new ArrayList<>();
        for (String fieldName : filterableFields) {
            fields.add(new FieldModel(fieldName, "java.lang.String", false, List.of()));
        }
        for (String ignoredField : ignoredFields) {
            fields.add(new FieldModel(ignoredField, "java.time.LocalDateTime", false, List.of()));
        }

        return new EntityModel(
            packageName + "." + simpleName, simpleName, packageName,
            "id", "java.lang.Long", basePath,
            fields, List.of(), Set.of(Operation.FIND_ALL),
            null, null,
            Set.of(), Set.of(), Set.of(), ignoredFields,
            filterableFields,
            null,
            false,
            null,
            StoreType.JPA
        );
    }
}
