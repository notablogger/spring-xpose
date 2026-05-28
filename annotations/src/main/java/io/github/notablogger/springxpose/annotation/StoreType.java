package io.github.notablogger.springxpose.annotation;

/**
 * Internal persistence store discriminator used by the spring-xpose annotation processor.
 *
 * <p>This enum is <strong>not a user-facing configuration option</strong> on {@link ExposeEntity}
 * — the store is determined implicitly by which annotation you use:
 * <ul>
 *   <li>{@link #JPA}   — set automatically when the class carries {@link ExposeEntity}.
 *       Generates a {@code JpaRepository}, uses {@code EntityManager.getReference()} for
 *       relation resolution, and emits {@code @Transactional} on write operations.</li>
 *   <li>{@link #MONGO} — set automatically when the class carries
 *       {@link ExposeDocument}. Generates a {@code MongoRepository}, skips
 *       {@code EntityManager} injection, and does not emit {@code @Transactional}.</li>
 * </ul>
 */
public enum StoreType {
    JPA,
    MONGO
}
