package io.github.notablogger.springxpose.processor.generator;

import io.github.notablogger.springxpose.annotation.FilterOperation;
import io.github.notablogger.springxpose.processor.model.EntityModel;
import io.github.notablogger.springxpose.processor.model.FilterFieldModel;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the OpenAPI example of a generated search request body: every search key
 * with a placeholder value derived from its type — {@code "string"}, {@code 0}, {@code true}, the
 * first enum constant, ISO-8601 date/time strings, a one-element array for {@code IN} /
 * {@code NOT_IN}, {@code true} for {@code IS_NULL}. Every value has the key's type, so the example
 * can be sent as is; it shows the shape of the body rather than a useful query.
 */
final class SearchExamples {

    private SearchExamples() {}

    /** Compact JSON with every search key of {@code model}; {@code {}} without filters. */
    static String allKeys(EntityModel model, ProcessingEnvironment env) {
        List<String> pairs = new ArrayList<>();
        for (FilterFieldModel filter : model.filters()) {
            for (FilterOperation op : filter.operations()) {
                pairs.add(json(filter.keyName(op)) + ":" + value(filter, op, env));
            }
        }
        return "{" + String.join(",", pairs) + "}";
    }

    /** Placeholder JSON value for one key. */
    private static String value(FilterFieldModel filter, FilterOperation op, ProcessingEnvironment env) {
        return switch (op) {
            case IS_NULL    -> "true";
            case IN, NOT_IN -> "[" + scalar(filter.isCollection() ? filter.elementType() : filter.type(), env) + "]";
            default         -> scalar(filter.type(), env);
        };
    }

    /** Placeholder JSON value for a scalar type. */
    private static String scalar(TypeMirror type, ProcessingEnvironment env) {
        if (type.getKind().isPrimitive()) {
            return switch (type.getKind()) {
                case BOOLEAN -> "true";
                case CHAR    -> json("a");
                default      -> "0";
            };
        }
        Element element = env.getTypeUtils().asElement(type);
        if (element != null && element.getKind() == ElementKind.ENUM) {
            return element.getEnclosedElements().stream()
                .filter(e -> e.getKind() == ElementKind.ENUM_CONSTANT)
                .findFirst()
                .map(e -> json(e.getSimpleName().toString()))
                .orElse(json("string"));
        }
        return switch (env.getTypeUtils().erasure(type).toString()) {
            case "java.lang.Boolean" -> "true";
            case "java.lang.Character" -> json("a");
            case "java.lang.Byte", "java.lang.Short", "java.lang.Integer", "java.lang.Long",
                 "java.lang.Float", "java.lang.Double", "java.math.BigDecimal", "java.math.BigInteger" -> "0";
            case "java.time.LocalDate" -> json("2026-01-01");
            case "java.time.LocalDateTime" -> json("2026-01-01T00:00:00");
            case "java.time.LocalTime" -> json("00:00:00");
            case "java.time.Instant", "java.time.OffsetDateTime", "java.time.ZonedDateTime" -> json("2026-01-01T00:00:00Z");
            case "java.util.UUID" -> json("00000000-0000-0000-0000-000000000000");
            default -> json("string");
        };
    }

    private static String json(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
