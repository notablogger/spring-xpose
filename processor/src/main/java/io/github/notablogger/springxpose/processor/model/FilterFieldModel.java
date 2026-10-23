package io.github.notablogger.springxpose.processor.model;

import io.github.notablogger.springxpose.annotation.FilterOperation;

import javax.lang.model.type.TypeMirror;
import java.util.Locale;
import java.util.Set;

/**
 * A field declared filterable via {@code @ExposeField(filterOperations = ...)}.
 * Each operation becomes one query parameter / one {@code FilterParams} property.
 */
public record FilterFieldModel(
    /** Entity attribute name, used in queries ({@code category}, {@code price}). */
    String name,
    /** Base of the query parameter / property names: the field name, or {@code categoryId} for a to-one relation. */
    String paramBase,
    /** Value type: the field type, or the related entity's id type for a to-one relation. */
    TypeMirror type,
    /** Element type for collections ({@code String} for {@code List<String>}; the related id type for a to-many relation), else {@code null}. */
    TypeMirror elementType,
    /** Id attribute of the related entity for relation filters ({@code id}), else {@code null}. */
    String relatedIdField,
    Set<FilterOperation> operations
) {
    /** True for relation filters, which compare the related entity's id. */
    public boolean isRelation() {
        return relatedIdField != null;
    }

    /** True for collection fields, where IN / NOT_IN mean "has any of" / "has none of". */
    public boolean isCollection() {
        return elementType != null;
    }

    /** Query parameter name: {@code price} for EQ, {@code price_gt} for GT, {@code status_not_in} for NOT_IN, etc. */
    public String paramName(FilterOperation op) {
        return op == FilterOperation.EQ ? paramBase : paramBase + "_" + suffix(op);
    }

    /** Java property name in {@code FilterParams}: {@code price} for EQ, {@code priceGt} for GT, {@code statusNotIn} for NOT_IN, etc. */
    public String propertyName(FilterOperation op) {
        if (op == FilterOperation.EQ) return paramBase;
        StringBuilder property = new StringBuilder(paramBase);
        for (String part : suffix(op).split("_")) {
            property.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return property.toString();
    }

    /** Query suffix for an operation: its lower-cased name ({@code gt}, {@code not_in}, {@code is_null}). */
    private static String suffix(FilterOperation op) {
        return op.name().toLowerCase(Locale.ROOT);
    }

    /** Getter name in {@code FilterParams}: {@code getPrice}, {@code getPriceGt}, etc. */
    public String getterName(FilterOperation op) {
        String property = propertyName(op);
        return "get" + Character.toUpperCase(property.charAt(0)) + property.substring(1);
    }
}
