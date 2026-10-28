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
     * When {@code true}, the generated {@code findAll} and {@code search} endpoints accept Spring
     * Data {@code Pageable} query parameters ({@code ?page=0&size=20&sort=name,asc}) and
     * return a {@code Page<Dto>} instead of a flat {@code List<Dto>}.
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

    /**
     * Optional custom validator — a Spring bean implementing
     * {@code org.springframework.validation.Validator} — run on the entity of every
     * {@code CREATE} and {@code UPDATE}, after bean validation and mapping and before saving.
     * Use it for rules annotations can't express: cross-field checks, or lookups against
     * a database or another service.
     * <p>
     * The validator receives the mapped entity (on update: the loaded entity with the request
     * merged in) and must support the entity class. Errors it rejects are returned as the
     * same {@code 400} {@code urn:springxpose:validation-error} problem as annotation failures.
     * The class must be a Spring bean (e.g. {@code @Component}).
     * <p>
     * Defaults to {@code void.class} — no custom validator.
     */
    Class<?> validator() default void.class;

}
