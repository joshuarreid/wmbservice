package com.example.wmbservice.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Exception thrown when a duplicate entity is detected (e.g., on insert).
 * Used to trigger 409 Conflict responses with standardized error details.
 * Supports logging of transaction ID for audit and traceability.
 */
public class DuplicateException extends RuntimeException {

    private static final Logger logger = LoggerFactory.getLogger(DuplicateException.class);

    private final Map<String, Object> details;
    private final String transactionId;

    /**
     * Constructs a DuplicateException with a message.
     * Logs instantiation with the message.
     * @param message Error message.
     */
    public DuplicateException(String message) {
        super(message);
        this.details = null;
        this.transactionId = null;
        logger.error("DuplicateException instantiated with message: {}", message);
    }

    /**
     * Constructs a DuplicateException with a message and details map.
     * Logs instantiation with message and details.
     * @param message Error message.
     * @param details Additional details for error response.
     */
    public DuplicateException(String message, Map<String, Object> details) {
        super(message);
        this.details = details;
        this.transactionId = null;
        logger.error("DuplicateException instantiated with message: {}, details: {}", message, details);
    }

    /**
     * Constructs a DuplicateException with message, details, and transaction ID.
     * Logs all arguments.
     * @param message Error message.
     * @param details Additional details for error response.
     * @param transactionId Transaction/request ID for traceability.
     */
    public DuplicateException(String message, Map<String, Object> details, String transactionId) {
        super(message);
        this.details = details;
        this.transactionId = transactionId;
        logger.error("DuplicateException instantiated with message: {}, details: {}, transactionId: {}", message, details, transactionId);
    }

    /**
     * @return Additional error details, if present.
     */
    public Map<String, Object> getDetails() {
        logger.debug("getDetails called, returning: {}", details);
        return details;
    }

    /**
     * @return Transaction/request ID, if present.
     */
    public String getTransactionId() {
        logger.debug("getTransactionId called, returning: {}", transactionId);
        return transactionId;
    }
}