package com.example.wmbservice.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles all exceptions globally for the budgeting microservice.
 * Returns standardized error responses and logs full context, including transaction ID.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles validation errors (e.g., @Valid, @NotNull failures).
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<Map<String, Object>> handleValidationException(Exception ex, HttpServletRequest request) {
        logger.info("Entered handleValidationException for request URI: {}", request.getRequestURI());
        String transactionId = getTransactionId(request);

        Map<String, Object> details = new HashMap<>();
        if (ex instanceof MethodArgumentNotValidException manve) {
            manve.getBindingResult().getFieldErrors()
                    .forEach(error -> details.put(error.getField(), error.getDefaultMessage()));
        } else if (ex instanceof BindException be) {
            be.getBindingResult().getFieldErrors()
                    .forEach(error -> details.put(error.getField(), error.getDefaultMessage()));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", Instant.now().toString());
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("error", "Bad Request");
        response.put("code", "VALIDATION_ERROR");
        response.put("message", "Validation failed for request.");
        response.put("transactionId", transactionId);
        response.put("details", details);

        logger.warn("Validation error occurred: {}, transactionId={}", details, transactionId);
        logger.info("Exiting handleValidationException with response: {}", response);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Handles custom DuplicateException (409 Conflict).
     */
    @ExceptionHandler(DuplicateException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateException(DuplicateException ex, HttpServletRequest request) {
        logger.info("Entered handleDuplicateException for request URI: {}", request.getRequestURI());
        String transactionId = getTransactionId(request);

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", Instant.now().toString());
        response.put("status", HttpStatus.CONFLICT.value());
        response.put("error", "Conflict");
        response.put("code", "DUPLICATE_ERROR");
        response.put("message", ex.getMessage());
        response.put("transactionId", transactionId);

        if (ex.getDetails() != null) {
            response.put("details", ex.getDetails());
        }

        logger.error("Duplicate error: {}, transactionId={}", ex.getMessage(), transactionId);
        logger.info("Exiting handleDuplicateException with response: {}", response);

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    /**
     * Handles all uncaught exceptions.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex, HttpServletRequest request) {
        logger.info("Entered handleGenericException for request URI: {}", request.getRequestURI());
        String transactionId = getTransactionId(request);

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", Instant.now().toString());
        response.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        response.put("error", "Internal Server Error");
        response.put("code", "GENERIC_ERROR");
        response.put("message", ex.getMessage());
        response.put("transactionId", transactionId);

        logger.error("Unhandled exception in request {}: transactionId={}, error={}", request.getRequestURI(), transactionId, ex, ex);
        logger.info("Exiting handleGenericException with response: {}", response);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * Extracts the X-Transaction-ID header from the request.
     * If not present, returns "N/A".
     */
    private String getTransactionId(HttpServletRequest request) {
        String transactionId = request.getHeader("X-Transaction-ID");
        if (transactionId == null || transactionId.isEmpty()) {
            logger.warn("X-Transaction-ID header missing for request URI: {}", request.getRequestURI());
            return "N/A";
        }
        return transactionId;
    }
}