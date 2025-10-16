package com.example.wmbservice.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralized logger utility for statement-based budgeting microservice.
 * Logs messages with transaction ID for traceability.
 * Use in all controllers and services for consistent logging.
 */
public class TransactionLogger {

    private final Logger logger;
    private final String serviceComponent;

    /**
     * Constructs a TransactionLogger for the given class and component name.
     * @param clazz Logging class context.
     * @param serviceComponent Logical service/component name for logs.
     */
    public TransactionLogger(Class<?> clazz, String serviceComponent) {
        this.logger = LoggerFactory.getLogger(clazz);
        this.serviceComponent = serviceComponent;
    }

    /**
     * Logs an informational message with transaction ID.
     * @param transactionId Transaction/request ID from header.
     * @param message Log message.
     */
    public void info(String transactionId, String message) {
        logger.info("[serviceComponent={}] [transactionId={}] {}", serviceComponent, transactionId, message);
    }

    /**
     * Logs a warning message with transaction ID.
     * @param transactionId Transaction/request ID from header.
     * @param message Log message.
     */
    public void warn(String transactionId, String message) {
        logger.warn("[serviceComponent={}] [transactionId={}] {}", serviceComponent, transactionId, message);
    }

    /**
     * Logs an error message with transaction ID and exception.
     * @param transactionId Transaction/request ID from header.
     * @param message Log message.
     * @param throwable Exception/error object.
     */
    public void error(String transactionId, String message, Throwable throwable) {
        logger.error("[serviceComponent={}] [transactionId={}] {} Exception: {}", serviceComponent, transactionId, message, throwable.toString());
    }

    /**
     * Logs a debug message with transaction ID.
     * @param transactionId Transaction/request ID from header.
     * @param message Log message.
     */
    public void debug(String transactionId, String message) {
        logger.debug("[serviceComponent={}] [transactionId={}] {}", serviceComponent, transactionId, message);
    }
}