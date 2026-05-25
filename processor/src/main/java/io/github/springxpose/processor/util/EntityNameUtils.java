package io.github.springxpose.processor.util;

public class EntityNameUtils {

    public static String pluralize(String name) {
        String lower = name.toLowerCase();
        if (lower.endsWith("person")) return lower.replace("person", "people");
        if (lower.endsWith("child")) return lower + "ren";
        if (lower.endsWith("s") || lower.endsWith("x") || lower.endsWith("z")
                || lower.endsWith("ch") || lower.endsWith("sh")) return lower + "es";
        if (lower.endsWith("ay") || lower.endsWith("ey") || lower.endsWith("iy")
                || lower.endsWith("oy") || lower.endsWith("uy")) return lower + "s";
        if (lower.endsWith("y")) return lower.substring(0, lower.length() - 1) + "ies";
        return lower + "s";
    }

    public static String toBasePath(String entitySimpleName) {
        return pluralize(entitySimpleName);
    }

    public static String toLowerCamel(String name) {
        if (name == null || name.isEmpty()) return name;
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}

