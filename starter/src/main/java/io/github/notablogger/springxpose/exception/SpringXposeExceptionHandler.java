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
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tools.jackson.core.JacksonException;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Global exception handler for all spring-xpose-generated controllers.
 *
 * <p>Maps common exception types to consistent RFC 9457 Problem Detail responses:
 * <ul>
 *   <li>{@code 400} — malformed JSON body, an invalid value, or an unknown search key —
 *       keyed by the JSON property in {@code errors} when Jackson reports one</li>
 *   <li>{@code 400} — bean validation failures ({@code @NotBlank}, {@code @Positive}, etc.)
 *       and rejections from a custom {@code Validator}</li>
 *   <li>{@code 409} — database constraint violation (duplicate key, FK violation on save)</li>
 *   <li>{@code 409} — optimistic locking conflict ({@code @Version} mismatch)</li>
 *   <li>{@code 422} — relation ID refers to a non-existent entity</li>
 * </ul>
 */
@RestControllerAdvice
public class SpringXposeExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(SpringXposeExceptionHandler.class);

    /**
     * Malformed / unreadable JSON body. When Jackson knows which property failed — a value of the
     * wrong type, an unparseable date, or an unknown key rejected by a generated
     * {@code SearchRequest} — the response names it: {@code "errors": {"price_gt": "..."}}.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadable(HttpMessageNotReadableException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setType(URI.create("urn:springxpose:malformed-body"));
        pd.setTitle("Malformed request body");

        JacksonException failed = jsonErrorWithPath(ex);
        if (failed != null) {
            String property = jsonPath(failed);
            Throwable root = rootCause(ex);
            // Jackson's own messages carry source locations; its original message is the readable part
            String message = root instanceof JacksonException je ? je.getOriginalMessage() : rootMessage(ex);
            pd.setDetail("The request body is invalid at '" + property + "': " + message);
            pd.setProperty("errors", Map.of(property, message));
        } else {
            pd.setDetail("The request body could not be parsed as JSON: " + rootMessage(ex));
        }
        return ResponseEntity.badRequest().body(pd);
    }

    /** Jakarta Validation failures — @NotBlank, @Positive, @Size, etc. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        return validationProblem(ex.getBindingResult());
    }

    /**
     * Rejections from the entity's custom {@code Validator} ({@code validator = ...}), thrown by the
     * generated controller. Same problem as bean validation; errors rejected on the object rather than
     * a field ({@code errors.reject(...)}) are listed under {@code _global}.
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ProblemDetail> handleBind(BindException ex) {
        return validationProblem(ex.getBindingResult());
    }

    private static ResponseEntity<ProblemDetail> validationProblem(BindingResult result) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (ObjectError error : result.getAllErrors()) {
            String key = error instanceof FieldError fe ? fe.getField() : "_global";
            String message = error.getDefaultMessage() != null ? error.getDefaultMessage() : "invalid";
            fieldErrors.merge(key, message, (a, b) -> a + "; " + b);
        }

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
        Throwable cause = rootCause(ex);
        return cause.getMessage() != null ? cause.getMessage() : ex.getClass().getSimpleName();
    }

    private static Throwable rootCause(Throwable ex) {
        Throwable cause = ex;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause;
    }

    /** The first Jackson exception in the cause chain that knows the failing property, or {@code null}. */
    private static JacksonException jsonErrorWithPath(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof JacksonException je && !je.getPath().isEmpty()) return je;
        }
        return null;
    }

    /** The failing property as a JSON path: {@code price_gt}, {@code items[2].sku}. */
    private static String jsonPath(JacksonException ex) {
        StringBuilder path = new StringBuilder();
        for (JacksonException.Reference ref : ex.getPath()) {
            if (ref.getPropertyName() != null) {
                if (!path.isEmpty()) path.append('.');
                path.append(ref.getPropertyName());
            } else if (ref.getIndex() >= 0) {
                path.append('[').append(ref.getIndex()).append(']');
            }
        }
        return path.toString();
    }
}

