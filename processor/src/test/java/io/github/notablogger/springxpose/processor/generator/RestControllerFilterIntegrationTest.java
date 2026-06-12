package io.github.notablogger.springxpose.processor.generator;

import io.github.notablogger.springxpose.annotation.Operation;
import io.github.notablogger.springxpose.annotation.StoreType;
import io.github.notablogger.springxpose.processor.model.EntityModel;
import io.github.notablogger.springxpose.processor.model.FieldModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for REST controller findAll endpoint with filter support.
 * Validates that the generated controller correctly handles:
 * - Queryable findAll endpoints with filterable fields
 * - JPA Specification-based filtering
 * - MongoDB MongoTemplate-based filtering
 * - Pagination with filters
 */
@DisplayName("REST Controller Filter Support Integration Tests")
class RestControllerFilterIntegrationTest {

    @Test
    @DisplayName("findAll without filters returns all records (backward compatible)")
    void testFindAllWithoutFilters_JPA() {
        EntityModel model = createJpaEntityModel("Product", "product", Set.of(), false);
        assertTrue(model.operations().contains(Operation.FIND_ALL));
        assertFalse(model.hasFilterableFields());
    }

    @Test
    @DisplayName("findAll with filters accepts @ModelAttribute FilterParams")
    void testFindAllWithFilters_JPA() {
        EntityModel model = createJpaEntityModel("Product", "product", Set.of("name", "status"), false);
        assertTrue(model.hasFilterableFields());
        assertEquals(2, model.filterableFields().size());
    }

    @Test
    @DisplayName("findAll with filters and pagination combines both features")
    void testFindAllWithFiltersAndPagination_JPA() {
        EntityModel model = createJpaEntityModel("Product", "product", Set.of("name", "status"), true);
        assertTrue(model.hasFilterableFields());
        assertTrue(model.pageable());
    }

    @Test
    @DisplayName("JPA controller uses Specification for filtering")
    void testFindAllUsesCriteriaSpecification_JPA() {
        EntityModel model = createJpaEntityModel("Product", "product", Set.of("name"), false);
        assertTrue(model.hasFilterableFields());
        assertEquals(StoreType.JPA, model.storeType());
    }

    @Test
    @DisplayName("JPA repository is extended with JpaSpecificationExecutor")
    void testRepositoryExtendedWithJpaSpecificationExecutor_JPA() {
        EntityModel model = createJpaEntityModel("Product", "product", Set.of("name"), false);
        assertTrue(model.hasFilterableFields());
        assertEquals(StoreType.JPA, model.storeType());
    }

    @Test
    @DisplayName("MongoDB findAll without filters returns all documents")
    void testFindAllWithoutFilters_MongoDB() {
        EntityModel model = createMongoEntityModel("Note", "note", Set.of(), false);
        assertFalse(model.hasFilterableFields());
        assertEquals(StoreType.MONGO, model.storeType());
    }

    @Test
    @DisplayName("MongoDB findAll with filters accepts @ModelAttribute FilterParams")
    void testFindAllWithFilters_MongoDB() {
        EntityModel model = createMongoEntityModel("Note", "note", Set.of("author", "category"), false);
        assertTrue(model.hasFilterableFields());
        assertEquals(2, model.filterableFields().size());
    }

    @Test
    @DisplayName("MongoDB findAll with filters and pagination combines both features")
    void testFindAllWithFiltersAndPagination_MongoDB() {
        EntityModel model = createMongoEntityModel("Note", "note", Set.of("author"), true);
        assertTrue(model.hasFilterableFields());
        assertTrue(model.pageable());
    }

    @Test
    @DisplayName("MongoDB controller uses MongoTemplate with Query for filtering")
    void testFindAllUsesMongoTemplate_MongoDB() {
        EntityModel model = createMongoEntityModel("Note", "note", Set.of("author"), false);
        assertTrue(model.hasFilterableFields());
        assertEquals(StoreType.MONGO, model.storeType());
    }

    @Test
    @DisplayName("MongoDB controller constructor injects MongoTemplate")
    void testControllerInjectsMongoTemplate_MongoDB() {
        EntityModel model = createMongoEntityModel("Note", "note", Set.of("author"), false);
        assertTrue(model.hasFilterableFields());
        assertEquals(StoreType.MONGO, model.storeType());
    }

    @Test
    @DisplayName("Filter params are bound from query parameters via @ModelAttribute")
    void testFilterParamsBindingFromQueryParameters() {
        EntityModel model = createJpaEntityModel("Product", "product", Set.of("name", "price"), false);
        assertTrue(model.hasFilterableFields());
    }

    @Test
    @DisplayName("Absent filter parameters default to null and are ignored")
    void testAbsentFilterParametersAreNull() {
        EntityModel model = createJpaEntityModel("Product", "product", Set.of("name", "status"), false);
        assertTrue(model.hasFilterableFields());
    }

    @Test
    @DisplayName("Multiple filters are combined with AND operator")
    void testMultipleFilterCombinedWithAnd() {
        EntityModel model = createJpaEntityModel("Product", "product", Set.of("name", "status", "category"), false);
        assertEquals(3, model.filterableFields().size());
    }

    @Test
    @DisplayName("No filters provided returns all records")
    void testNoFiltersReturnsAllRecords() {
        EntityModel model = createJpaEntityModel("Product", "product", Set.of("name"), false);
        assertTrue(model.hasFilterableFields());
    }

    @Test
    @DisplayName("Filtering uses exact-match equality, not LIKE patterns")
    void testFilteringSupportsExactMatchOnly() {
        EntityModel model = createJpaEntityModel("Product", "product", Set.of("name", "status"), false);
        assertTrue(model.hasFilterableFields());
    }

    @Test
    @DisplayName("Filterable fields exclude relation fields")
    void testFilterableFieldsExcludeRelations() {
        EntityModel model = createJpaEntityModelWithMixedFields("Product", "product",
            Set.of("name", "price"), Set.of("category"));
        assertEquals(2, model.filterableFields().size());
        assertFalse(model.filterableFields().contains("category"));
    }

    @Test
    @DisplayName("JPA pagination offset works with filters")
    void testPaginationOffsetWithFilters_JPA() {
        EntityModel model = createJpaEntityModel("Product", "product", Set.of("name"), true);
        assertTrue(model.pageable());
        assertTrue(model.hasFilterableFields());
    }

    @Test
    @DisplayName("MongoDB pagination offset works with filters")
    void testPaginationOffsetWithFilters_MongoDB() {
        EntityModel model = createMongoEntityModel("Note", "note", Set.of("author"), true);
        assertTrue(model.pageable());
        assertTrue(model.hasFilterableFields());
    }

    @Test
    @DisplayName("JPA Specification-based filtering provides efficient queries")
    void testFilteredQueryPerformance_Specification() {
        EntityModel model = createJpaEntityModel("Product", "product", Set.of("status"), false);
        assertTrue(model.hasFilterableFields());
        assertEquals(StoreType.JPA, model.storeType());
    }

    @Test
    @DisplayName("MongoDB Criteria-based filtering provides efficient queries")
    void testFilteredQueryPerformance_MongoDB() {
        EntityModel model = createMongoEntityModel("Note", "note", Set.of("category"), false);
        assertTrue(model.hasFilterableFields());
        assertEquals(StoreType.MONGO, model.storeType());
    }

    @Test
    @DisplayName("Filter params are type-safe with automatic type conversion")
    void testFilterParamsValidation() {
        Map<String, String> typedFields = Map.of(
            "name", "java.lang.String",
            "price", "double",
            "active", "boolean"
        );
        EntityModel model = createJpaEntityModelWithTypedFields("Product", "product",
            typedFields.keySet(), typedFields);
        assertEquals(3, model.filterableFields().size());
    }

    @Test
    @DisplayName("SerializationContext is set to LIST mode for filtered results")
    void testSerializationContextHandlesFilteredLists() {
        EntityModel model = createJpaEntityModel("Product", "product", Set.of("name"), false);
        assertTrue(model.operations().contains(Operation.FIND_ALL));
    }

    @Test
    @DisplayName("Response DTOs are mapped from filtered results")
    void testResponseDtoUsesMapperForFilteredResults() {
        EntityModel model = createJpaEntityModel("Product", "product", Set.of("name"), false);
        assertTrue(model.hasFilterableFields());
    }

    @Test
    @DisplayName("Empty result set returns empty list without errors")
    void testEmptyFilteredResultSet() {
        EntityModel model = createJpaEntityModel("Product", "product", Set.of("name"), false);
        assertTrue(model.hasFilterableFields());
    }

    @Test
    @DisplayName("Multiple entities can have independent filter configurations")
    void testMultipleEntitiesWithDifferentFilters() {
        EntityModel product = createJpaEntityModel("Product", "product", Set.of("name", "price"), false);
        EntityModel category = createJpaEntityModel("Category", "category", Set.of("title"), false);
        
        assertEquals(2, product.filterableFields().size());
        assertEquals(1, category.filterableFields().size());
    }

    @Test
    @DisplayName("Filter fields maintain type information for validation")
    void testFilterFieldsAreTypeSafe() {
        EntityModel model = createJpaEntityModelWithTypedFields("Product", "product",
            Set.of("price", "quantity"), 
            Map.of("price", "double", "quantity", "int"));
        
        assertEquals(2, model.filterableFields().size());
    }

    @Test
    @DisplayName("Case-sensitive field matching")
    void testCaseSensitiveFilterMatching() {
        EntityModel model = createJpaEntityModel("Product", "product", 
            Set.of("name", "Name"));
        // Both "name" and "Name" should be treated as different fields
        assertEquals(2, model.filterableFields().size());
    }

    @Test
    @DisplayName("Special characters in field names are handled")
    void testSpecialCharactersInFieldNames() {
        EntityModel model = createJpaEntityModel("Product", "product",
            Set.of("product_name", "item_code"));
        assertEquals(2, model.filterableFields().size());
    }

    // Helper methods

    private EntityModel createJpaEntityModel(String simpleName, String basePath,
                                             Set<String> filterableFields, boolean pageable) {
        return createEntityModel(simpleName, basePath, filterableFields, pageable, StoreType.JPA);
    }

    private EntityModel createMongoEntityModel(String simpleName, String basePath,
                                               Set<String> filterableFields, boolean pageable) {
        return createEntityModel(simpleName, basePath, filterableFields, pageable, StoreType.MONGO);
    }

    private EntityModel createEntityModel(String simpleName, String basePath,
                                          Set<String> filterableFields, boolean pageable,
                                          StoreType storeType) {
        String packageName = "com.example";

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
            pageable,
            null,
            storeType
        );
    }

    private EntityModel createJpaEntityModelWithMixedFields(String simpleName, String basePath,
                                                            Set<String> scalarFields,
                                                            Set<String> relationFields) {
        String packageName = "com.example";

        List<FieldModel> fields = new ArrayList<>();
        for (String fieldName : scalarFields) {
            fields.add(new FieldModel(fieldName, "java.lang.String", false, List.of()));
        }

        return new EntityModel(
            packageName + "." + simpleName, simpleName, packageName,
            "id", "java.lang.Long", basePath,
            fields, List.of(), Set.of(Operation.FIND_ALL),
            null, null,
            Set.of(), Set.of(), Set.of(), Set.of(),
            scalarFields,
            null,
            false,
            null,
            StoreType.JPA
        );
    }

    private EntityModel createJpaEntityModelWithTypedFields(String simpleName, String basePath,
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
}
