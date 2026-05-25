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
}

