package io.github.notablogger.springxpose.serializer;

/**
 * ThreadLocal context set by generated controllers before each repository call
 * to control how relation fields are serialized.
 */
public class SerializationContext {

    public enum Mode { LIST, SINGLE }

    private static final ThreadLocal<Mode> CONTEXT = new ThreadLocal<>();

    public static void set(Mode mode) {
        CONTEXT.set(mode);
    }

    public static Mode get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}

