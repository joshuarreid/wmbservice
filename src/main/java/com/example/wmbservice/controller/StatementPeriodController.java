package com.example.wmbservice.controller;

import com.example.wmbservice.model.StatementPeriod;
import com.example.wmbservice.service.StatementPeriodService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for CRUD operations on StatementPeriod.
 * Centralized logging and error handling, propagates X-Transaction-ID header.
 */
@CrossOrigin(origins = "http://localhost:3000", exposedHeaders = "X-Transaction-ID")
@RestController
@RequestMapping("/api/statements")
public class StatementPeriodController {

    private static final Logger logger = LoggerFactory.getLogger(StatementPeriodController.class);

    private final StatementPeriodService statementPeriodService;

    public StatementPeriodController(StatementPeriodService statementPeriodService) {
        this.statementPeriodService = statementPeriodService;
    }

    /**
     * Create a statement period.
     *
     * @param statementPeriod payload
     * @param transactionId   propagated X-Transaction-ID header
     * @return created StatementPeriod or error response
     */
    @PostMapping
    public ResponseEntity<?> createStatementPeriod(
            @Valid @RequestBody StatementPeriod statementPeriod,
            @RequestHeader(value = "X-Transaction-ID", required = false) String transactionId) {

        logger.info("createStatementPeriod entered. transactionId={}, payload={}", transactionId, statementPeriod);

        try {
            StatementPeriod created = statementPeriodService.createStatementPeriod(statementPeriod, transactionId);
            logger.info("StatementPeriod created successfully. transactionId={}, id={}", transactionId, created.getId());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .header("X-Transaction-ID", transactionId)
                    .body(created);
        } catch (StatementPeriodService.DuplicateStatementPeriodException e) {
            logger.warn("Duplicate statement period detected. transactionId={}, error={}", transactionId, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .header("X-Transaction-ID", transactionId)
                    .body(new ErrorResponse(HttpStatus.CONFLICT.value(), "DUPLICATE_STATEMENT_PERIOD", e.getMessage(), transactionId));
        } catch (IllegalArgumentException e) {
            logger.warn("Validation error creating statement period. transactionId={}, error={}", transactionId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .header("X-Transaction-ID", transactionId)
                    .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "VALIDATION_ERROR", e.getMessage(), transactionId));
        } catch (Exception e) {
            logger.error("Error creating statement period. transactionId={}, error={}", transactionId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("X-Transaction-ID", transactionId)
                    .body(new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "CREATE_ERROR", "Unexpected error", transactionId));
        }
    }

    /**
     * Get all statement periods.
     *
     * @param transactionId propagated X-Transaction-ID header
     * @return list of StatementPeriod
     */
    @GetMapping
    public ResponseEntity<?> getAllStatementPeriods(
            @RequestHeader(value = "X-Transaction-ID", required = false) String transactionId) {

        logger.info("getAllStatementPeriods entered. transactionId={}", transactionId);

        try {
            List<StatementPeriod> result = statementPeriodService.getAllStatementPeriods(transactionId);
            logger.info("getAllStatementPeriods successful. transactionId={}, resultCount={}", transactionId, result.size());
            return ResponseEntity.ok()
                    .header("X-Transaction-ID", transactionId)
                    .body(result);
        } catch (Exception e) {
            logger.error("Error fetching statement periods. transactionId={}, error={}", transactionId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("X-Transaction-ID", transactionId)
                    .body(new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "LIST_ERROR", "Unexpected error", transactionId));
        }
    }

    /**
     * Get a specific statement period by id.
     *
     * @param id            identifier
     * @param transactionId propagated X-Transaction-ID header
     * @return StatementPeriod or error
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getStatementPeriod(
            @PathVariable Long id,
            @RequestHeader(value = "X-Transaction-ID", required = false) String transactionId) {

        logger.info("getStatementPeriod entered. transactionId={}, id={}", transactionId, id);

        try {
            StatementPeriod sp = statementPeriodService.getStatementPeriod(id, transactionId);
            logger.info("getStatementPeriod successful. transactionId={}, id={}", transactionId, id);
            return ResponseEntity.ok()
                    .header("X-Transaction-ID", transactionId)
                    .body(sp);
        } catch (StatementPeriodService.StatementPeriodNotFoundException e) {
            logger.warn("StatementPeriod not found. transactionId={}, id={}", transactionId, id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .header("X-Transaction-ID", transactionId)
                    .body(new ErrorResponse(HttpStatus.NOT_FOUND.value(), "NOT_FOUND", e.getMessage(), transactionId));
        } catch (Exception e) {
            logger.error("Error getting statement period. transactionId={}, error={}", transactionId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("X-Transaction-ID", transactionId)
                    .body(new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "GET_ERROR", "Unexpected error", transactionId));
        }
    }

    /**
     * Update a statement period.
     *
     * @param id              identifier
     * @param statementPeriod updated payload
     * @param transactionId   propagated X-Transaction-ID header
     * @return updated StatementPeriod or error
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateStatementPeriod(
            @PathVariable Long id,
            @Valid @RequestBody StatementPeriod statementPeriod,
            @RequestHeader(value = "X-Transaction-ID", required = false) String transactionId) {

        logger.info("updateStatementPeriod entered. transactionId={}, id={}, payload={}", transactionId, id, statementPeriod);

        try {
            StatementPeriod updated = statementPeriodService.updateStatementPeriod(id, statementPeriod, transactionId);
            logger.info("updateStatementPeriod successful. transactionId={}, id={}", transactionId, id);
            return ResponseEntity.ok()
                    .header("X-Transaction-ID", transactionId)
                    .body(updated);
        } catch (StatementPeriodService.StatementPeriodNotFoundException e) {
            logger.warn("StatementPeriod not found for update. transactionId={}, id={}", transactionId, id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .header("X-Transaction-ID", transactionId)
                    .body(new ErrorResponse(HttpStatus.NOT_FOUND.value(), "NOT_FOUND", e.getMessage(), transactionId));
        } catch (StatementPeriodService.DuplicateStatementPeriodException e) {
            logger.warn("Duplicate statement period on update. transactionId={}, error={}", transactionId, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .header("X-Transaction-ID", transactionId)
                    .body(new ErrorResponse(HttpStatus.CONFLICT.value(), "DUPLICATE_STATEMENT_PERIOD", e.getMessage(), transactionId));
        } catch (IllegalArgumentException e) {
            logger.warn("Validation error updating statement period. transactionId={}, error={}", transactionId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .header("X-Transaction-ID", transactionId)
                    .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "VALIDATION_ERROR", e.getMessage(), transactionId));
        } catch (Exception e) {
            logger.error("Error updating statement period. transactionId={}, error={}", transactionId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("X-Transaction-ID", transactionId)
                    .body(new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "UPDATE_ERROR", "Unexpected error", transactionId));
        }
    }

    /**
     * Delete a statement period by id.
     *
     * @param id            identifier
     * @param transactionId propagated X-Transaction-ID header
     * @return 204 No Content on success or error response
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteStatementPeriod(
            @PathVariable Long id,
            @RequestHeader(value = "X-Transaction-ID", required = false) String transactionId) {

        logger.info("deleteStatementPeriod entered. transactionId={}, id={}", transactionId, id);

        try {
            boolean deleted = statementPeriodService.deleteStatementPeriod(id, transactionId);
            if (!deleted) {
                logger.warn("StatementPeriod not found for delete. transactionId={}, id={}", transactionId, id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .header("X-Transaction-ID", transactionId)
                        .body(new ErrorResponse(HttpStatus.NOT_FOUND.value(), "NOT_FOUND", "Statement period not found for delete", transactionId));
            }
            logger.info("deleteStatementPeriod successful. transactionId={}, id={}", transactionId, id);
            return ResponseEntity.noContent()
                    .header("X-Transaction-ID", transactionId)
                    .build();
        } catch (Exception e) {
            logger.error("Error deleting statement period. transactionId={}, error={}", transactionId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("X-Transaction-ID", transactionId)
                    .body(new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "DELETE_ERROR", "Unexpected error", transactionId));
        }
    }

    /**
     * Delete all statement periods.
     *
     * @param transactionId propagated X-Transaction-ID header
     * @return JSON with deletedCount or error
     */
    @DeleteMapping
    public ResponseEntity<?> deleteAllStatementPeriods(
            @RequestHeader(value = "X-Transaction-ID", required = false) String transactionId) {

        logger.info("deleteAllStatementPeriods entered. transactionId={}", transactionId);

        try {
            long deletedCount = statementPeriodService.deleteAllStatementPeriods(transactionId);
            logger.info("deleteAllStatementPeriods successful. transactionId={}, deletedCount={}", transactionId, deletedCount);

            Map<String, Object> body = new HashMap<>();
            body.put("deletedCount", deletedCount);
            return ResponseEntity.ok()
                    .header("X-Transaction-ID", transactionId)
                    .body(body);
        } catch (Exception e) {
            logger.error("Error deleting all statement periods. transactionId={}, error={}", transactionId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("X-Transaction-ID", transactionId)
                    .body(new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "DELETE_ALL_ERROR", "Unexpected error", transactionId));
        }
    }

    /**
     * Error response format for all error cases in this controller.
     */
    private static class ErrorResponse {
        public final int status;
        public final String code;
        public final String message;
        public final String transactionId;

        public ErrorResponse(int status, String code, String message, String transactionId) {
            this.status = status;
            this.code = code;
            this.message = message;
            this.transactionId = transactionId;
        }
    }
}