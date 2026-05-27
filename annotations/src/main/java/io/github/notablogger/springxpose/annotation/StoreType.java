package io.github.notablogger.springxpose.annotation;

/**
 * Persistence store type for a {@code @ExposeEntity}-annotated class.
 *
 * <ul>
 *   <li>{@link #JPA}   — Spring Data JPA (default). Generates a {@code JpaRepository},
 *       uses {@code EntityManager.getReference()} for relation resolution, and emits
 *       {@code @Transactional} on write operations.</li>
 *   <li>{@link #MONGO} — Spring Data MongoDB. Generates a {@code MongoRepository},
 *       skips {@code EntityManager} injection, and does not attempt FK-style relation
 *       resolution.</li>
 * </ul>
 */
public enum StoreType {
    JPA,
    MONGO
}

