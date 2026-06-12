package io.github.notablogger.springxpose.annotation;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface ExposeEntity {

    String path() default "";

    Operation[] expose() default {
        Operation.FIND_ALL,
        Operation.FIND_BY_ID,
        Operation.CREATE,
        Operation.UPDATE,
        Operation.DELETE
    };

    RelationMode relationMode() default RelationMode.IDS_FOR_LIST_OBJECT_FOR_SINGLE;

    AuthType authType() default AuthType.NONE;

    String[] roles() default {};

    String[] readRoles() default {};

    String[] writeRoles() default {};

    /**
     * Entity field names to exclude from both the generated response DTO and request DTO.
     * The entity itself retains these fields for persistence.
     */
    String[] ignoredFields() default {};

    /**
     * Scalar field names to expose as optional exact-match query parameters on the
     * {@code GET /api/<path>} (findAll) endpoint.
     * <p>
     * Example: {@code filterableFields = {"status", "name"}} generates
     * {@code GET /api/products?status=ACTIVE&name=Widget}.
     * <p>
     * For each listed field spring-xpose generates:
     * <ul>
     *   <li>A {@code <Entity>FilterParams} POJO whose fields match the entity field types.</li>
     *   <li>A {@code <Entity>Spec} class with a {@code withFilters(FilterParams)} method
     *       returning a {@code Specification<Entity>} (JPA) or {@code Query} (MongoDB).</li>
     * </ul>
     * The repository is automatically extended with {@code JpaSpecificationExecutor} for JPA.
     * Only scalar (non-relation) fields are supported.
     */
    String[] filterableFields() default {};

    /**
     * When {@code true}, the generated {@code findAll} endpoint accepts Spring Data
     * {@code Pageable} query parameters ({@code ?page=0&size=20&sort=name,asc}) and
     * returns a {@code Page<Dto>} instead of a flat {@code List<Dto>}.
     * <p>
     * Defaults to {@code false} to preserve backward-compatible list responses.
     */
    boolean pageable() default false;

    /**
     * Optional custom MapStruct mapper class to use instead of the generated one.
     * <p>
     * When set, spring-xpose omits {@code @Mapper} from the generated interface so
     * MapStruct skips auto-generation. Your class must implement the generated
     * {@code <Entity>Mapper} interface and be annotated with {@code @Component}.
     * <p>
     * Defaults to {@code void.class} — use the MapStruct-generated implementation.
     */
    Class<?> customMapper() default void.class;

}
