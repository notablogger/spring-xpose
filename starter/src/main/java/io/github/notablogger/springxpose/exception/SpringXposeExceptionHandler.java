package io.github.notablogger.springxpose.exception;

import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global exception handler for all spring-xpose-generated controllers.
 *
 * <p>Maps common exception types to consistent RFC 9457 Problem Detail responses:
 * <ul>
 *   <li>{@code 400} — malformed JSON body</li>
 *   <li>{@code 400} — bean validation failures ({@code @NotBlank}, {@code @Positive}, etc.)</li>
 *   <li>{@code 409} — database constraint violation (duplicate key, FK violation on save)</li>
 *   <li>{@code 409} — optimistic locking conflict ({@code @Version} mismatch)</li>
 *   <li>{@code 422} — relation ID refers to a non-existent entity</li>
 * </ul>
 */
@RestControllerAdvice
public class SpringXposeExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(SpringXposeExceptionHandler.class);

    /** Malformed / unreadable JSON body. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadable(HttpMessageNotReadableException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setType(URI.create("urn:springxpose:malformed-body"));
        pd.setTitle("Malformed request body");
        pd.setDetail("The request body could not be parsed as JSON: " + rootMessage(ex));
        return ResponseEntity.badRequest().body(pd);
    }

    /** Jakarta Validation failures — @NotBlank, @Positive, @Size, etc. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
            .collect(Collectors.toMap(
                FieldError::getField,
                fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid",
                (a, b) -> a + "; " + b
            ));

        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setType(URI.create("urn:springxpose:validation-error"));
        pd.setTitle("Validation failed");
        pd.setDetail("One or more fields failed validation.");
        pd.setProperty("errors", fieldErrors);
        return ResponseEntity.badRequest().body(pd);
    }

    /**
     * Relation ID points to a non-existent entity.
     * {@code EntityManager.getReference()} defers loading to flush time; JPA throws
     * {@code EntityNotFoundException} when the proxy is first accessed.
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleEntityNotFound(EntityNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        pd.setType(URI.create("urn:springxpose:relation-not-found"));
        pd.setTitle("Related entity not found");
        pd.setDetail("A relation ID in the request body refers to an entity that does not exist: "
            + rootMessage(ex));
        return ResponseEntity.unprocessableEntity().body(pd);
    }

    /**
     * Database constraint violation — duplicate key, FK violation, NOT NULL column, etc.
     * Maps to 409 Conflict.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation while processing request", ex);

        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        pd.setType(URI.create("urn:springxpose:constraint-violation"));
        pd.setTitle("Data integrity violation");
        pd.setDetail("The request can not be processed.");
        pd.setProperty("errorCode", "CONSTRAINT_VIOLATION");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(pd);
    }

    /**
     * Optimistic locking conflict — two concurrent updates raced; the losing one gets 409.
     * Clients should re-fetch and retry.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLock(OptimisticLockingFailureException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        pd.setType(URI.create("urn:springxpose:optimistic-lock"));
        pd.setTitle("Concurrent update conflict");
        pd.setDetail("This record was modified by another request. Re-fetch and retry.");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(pd);
    }

    private static String rootMessage(Throwable ex) {
        Throwable cause = ex;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() != null ? cause.getMessage() : ex.getClass().getSimpleName();
    }
}

