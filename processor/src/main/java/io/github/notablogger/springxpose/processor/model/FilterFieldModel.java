package io.github.notablogger.springxpose.processor.model;

import io.github.notablogger.springxpose.annotation.FilterOperation;

import javax.lang.model.type.TypeMirror;
import java.util.Locale;
import java.util.Set;

/**
 * A field declared filterable via {@code @ExposeField(operations = ...)}.
 * Each operation becomes one query parameter / one {@code FilterParams} property.
 */
public record FilterFieldModel(
    String name,
    TypeMirror type,
    Set<FilterOperation> operations
) {
    /** Query parameter name: {@code price} for EQ, {@code price_gt} for GT, etc. */
    public String paramName(FilterOperation op) {
        return op == FilterOperation.EQ ? name : name + "_" + op.name().toLowerCase(Locale.ROOT);
    }

    /** Java property name in {@code FilterParams}: {@code price} for EQ, {@code priceGt} for GT, etc. */
    public String propertyName(FilterOperation op) {
        if (op == FilterOperation.EQ) return name;
        String suffix = op.name().toLowerCase(Locale.ROOT);
        return name + Character.toUpperCase(suffix.charAt(0)) + suffix.substring(1);
    }

    /** Getter name in {@code FilterParams}: {@code getPrice}, {@code getPriceGt}, etc. */
    public String getterName(FilterOperation op) {
        String property = propertyName(op);
        return "get" + Character.toUpperCase(property.charAt(0)) + property.substring(1);
    }
}
