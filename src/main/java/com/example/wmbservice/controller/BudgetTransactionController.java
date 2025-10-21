package com.example.wmbservice.controller;

import com.example.wmbservice.model.BudgetTransaction;
import com.example.wmbservice.service.BudgetTransactionService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for CRUD operations on BudgetTransaction.
 * Centralized logging and error handling, propagates X-Transaction-ID.
 */
@CrossOrigin(origins = "http://localhost:3000", exposedHeaders = "X-Transaction-ID")
@RestController
@RequestMapping("/api/transactions")
public class BudgetTransactionController {

    private static final Logger logger = LoggerFactory.getLogger(BudgetTransactionController.class);

    private final BudgetTransactionService budgetTransactionService;

    public BudgetTransactionController(BudgetTransactionService budgetTransactionService) {
        this.budgetTransactionService = budgetTransactionService;
    }

    /**
     * Create a budget transaction.
     * @param transaction BudgetTransaction payload.
     * @param transactionId X-Transaction-ID header for traceability.
     * @return ResponseEntity with created transaction or error.
     */
    @PostMapping
    public ResponseEntity<?> createTransaction(
            @Valid @RequestBody BudgetTransaction transaction,
            @RequestHeader(value = "X-Transaction-ID", required = false) String transactionId) {

        logger.info("createTransaction entered. transactionId={}, payload={}", transactionId, transaction);

        try {
            BudgetTransaction created = budgetTransactionService.createTransaction(transaction, transactionId);
            logger.info("Transaction created successfully. transactionId={}, id={}", transactionId, created.getId());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .header("X-Transaction-ID", transactionId)
                    .body(created);
        } catch (BudgetTransactionService.DuplicateTransactionException e) {
            logger.warn("Duplicate transaction detected. transactionId={}, error={}", transactionId, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .header("X-Transaction-ID", transactionId)
                    .body(new ErrorResponse(HttpStatus.CONFLICT.value(), "DUPLICATE_TRANSACTION", e.getMessage(), transactionId));
        } catch (Exception e) {
            logger.error("Error creating transaction. transactionId={}, error={}", transactionId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("X-Transaction-ID", transactionId)
                    .body(new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "CREATE_ERROR", "Unexpected error", transactionId));
        }
    }

    /**
     * Get all transactions, with optional filters.
     * @param statementPeriod Optional filter.
     * @param account Optional filter.
     * @param category Optional filter.
     * @param paymentMethod Optional filter.
     * @param transactionId X-Transaction-ID header.
     * @return List of BudgetTransaction.
     */
    @GetMapping
    public ResponseEntity<?> getTransactions(
            @RequestParam(value = "statementPeriod", required = false) String statementPeriod,
            @RequestParam(value = "account", required = false) String account,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "criticality", required = false) String criticality,
            @RequestParam(value = "paymentMethod", required = false) String paymentMethod,
            @RequestHeader(value = "X-Transaction-ID", required = false) String transactionId) {

        logger.info("getTransactions entered. transactionId={}, filters: statementPeriod={}, account={}, category={}, paymentMethod={}",
                transactionId, statementPeriod, account, category, paymentMethod);

        try {
            List<BudgetTransaction> transactions = budgetTransactionService.getTransactions(
                    statementPeriod, account, category, criticality, paymentMethod, transactionId);
            logger.info("getTransactions successful. transactionId={}, resultCount={}", transactionId, transactions.size());
            return ResponseEntity.ok()
                    .header("X-Transaction-ID", transactionId)
                    .body(transactions);
        } catch (Exception e) {
            logger.error("Error fetching transactions. transactionId={}, error={}", transactionId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("X-Transaction-ID", transactionId)
                    .body(new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "LIST_ERROR", "Unexpected error", transactionId));
        }
    }

    /**
     * Get transaction by ID.
     * @param id Transaction ID.
     * @param transactionId X-Transaction-ID header.
     * @return BudgetTransaction or error.
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getTransaction(
            @PathVariable Long id,
            @RequestHeader(value = "X-Transaction-ID", required = false) String transactionId) {

        logger.info("getTransaction entered. transactionId={}, id={}", transactionId, id);

        try {
            BudgetTransaction transaction = budgetTransactionService.getTransaction(id, transactionId);
            if (transaction == null) {
                logger.warn("Transaction not found. transactionId={}, id={}", transactionId, id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .header("X-Transaction-ID", transactionId)
                        .body(new ErrorResponse(HttpStatus.NOT_FOUND.value(), "NOT_FOUND", "Transaction not found", transactionId));
            }
            logger.info("getTransaction successful. transactionId={}, id={}", transactionId, id);
            return ResponseEntity.ok()
                    .header("X-Transaction-ID", transactionId)
                    .body(transaction);
        } catch (Exception e) {
            logger.error("Error getting transaction. transactionId={}, error={}", transactionId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("X-Transaction-ID", transactionId)
                    .body(new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "GET_ERROR", "Unexpected error", transactionId));
        }
    }

    /**
     * Update a transaction.
     * @param id Transaction ID.
     * @param transaction Updated fields.
     * @param transactionId X-Transaction-ID header.
     * @return Updated transaction or error.
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateTransaction(
            @PathVariable Long id,
            @Valid @RequestBody BudgetTransaction transaction,
            @RequestHeader(value = "X-Transaction-ID", required = false) String transactionId) {

        logger.info("updateTransaction entered. transactionId={}, id={}, payload={}", transactionId, id, transaction);

        try {
            BudgetTransaction updated = budgetTransactionService.updateTransaction(id, transaction, transactionId);
            if (updated == null) {
                logger.warn("Transaction not found for update. transactionId={}, id={}", transactionId, id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .header("X-Transaction-ID", transactionId)
                        .body(new ErrorResponse(HttpStatus.NOT_FOUND.value(), "NOT_FOUND", "Transaction not found for update", transactionId));
            }
            logger.info("updateTransaction successful. transactionId={}, id={}", transactionId, id);
            return ResponseEntity.ok()
                    .header("X-Transaction-ID", transactionId)
                    .body(updated);
        } catch (BudgetTransactionService.DuplicateTransactionException e) {
            logger.warn("Duplicate transaction on update. transactionId={}, error={}", transactionId, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .header("X-Transaction-ID", transactionId)
                    .body(new ErrorResponse(HttpStatus.CONFLICT.value(), "DUPLICATE_TRANSACTION", e.getMessage(), transactionId));
        } catch (Exception e) {
            logger.error("Error updating transaction. transactionId={}, error={}", transactionId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("X-Transaction-ID", transactionId)
                    .body(new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "UPDATE_ERROR", "Unexpected error", transactionId));
        }
    }

    /**
     * Delete a transaction.
     * @param id Transaction ID.
     * @param transactionId X-Transaction-ID header.
     * @return Success or error response.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteTransaction(
            @PathVariable Long id,
            @RequestHeader(value = "X-Transaction-ID", required = false) String transactionId) {

        logger.info("deleteTransaction entered. transactionId={}, id={}", transactionId, id);

        try {
            boolean deleted = budgetTransactionService.deleteTransaction(id, transactionId);
            if (!deleted) {
                logger.warn("Transaction not found for delete. transactionId={}, id={}", transactionId, id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .header("X-Transaction-ID", transactionId)
                        .body(new ErrorResponse(HttpStatus.NOT_FOUND.value(), "NOT_FOUND", "Transaction not found for delete", transactionId));
            }
            logger.info("deleteTransaction successful. transactionId={}, id={}", transactionId, id);
            return ResponseEntity.noContent()
                    .header("X-Transaction-ID", transactionId)
                    .build();
        } catch (Exception e) {
            logger.error("Error deleting transaction. transactionId={}, error={}", transactionId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("X-Transaction-ID", transactionId)
                    .body(new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "DELETE_ERROR", "Unexpected error", transactionId));
        }
    }

    /**
     * Deletes all transactions.
     * @param transactionId X-Transaction-ID header.
     * @return Count of deleted transactions.
     */
    @DeleteMapping()
    public ResponseEntity<?> deleteAllTransactions(
            @RequestHeader(value = "X-Transaction-ID", required = false) String transactionId) {

        logger.info("deleteAllTransactions entered. transactionId={}", transactionId);

        try {
            long deletedCount = budgetTransactionService.deleteAllTransactions(transactionId);
            logger.info("deleteAllTransactions successful. transactionId={}, deletedCount={}", transactionId, deletedCount);

            Map<String, Object> body = Map.of("deletedCount", deletedCount);
            return ResponseEntity.ok()
                    .header("X-Transaction-ID", transactionId)
                    .body(body);
        } catch (Exception e) {
            logger.error("Error deleting all transactions. transactionId={}, error={}", transactionId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("X-Transaction-ID", transactionId)
                    .body(new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "DELETE_ALL_ERROR", "Unexpected error", transactionId));
        }
    }


    /**
     * Uploads a CSV of transactions for bulk import with deduplication and validation.
     * Accepts multipart form-data: file (CSV), statementPeriod (required), and X-Transaction-ID (header).
     * Returns inserted count, duplicate count, and error details.
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadTransactions(
            @RequestParam("file") MultipartFile file,
            @RequestParam("statementPeriod") String statementPeriod,
            @RequestHeader(value = "X-Transaction-ID", required = false) String transactionId) {

        logger.info("uploadTransactions entered. transactionId={}, statementPeriod={}", transactionId, statementPeriod);

        try {
            BudgetTransactionService.BulkImportResult result = budgetTransactionService.bulkImportTransactions(
                    file, statementPeriod, transactionId);

            logger.info("uploadTransactions completed. transactionId={}, insertedCount={}, duplicateCount={}, errorCount={}",
                    transactionId, result.getInsertedCount(), result.getDuplicateCount(), result.getErrors().size());

            return ResponseEntity.ok()
                    .header("X-Transaction-ID", transactionId)
                    .body(result);
        } catch (Exception e) {
            logger.error("Error during uploadTransactions. transactionId={}, error={}", transactionId, e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", HttpStatus.BAD_REQUEST.value());
            errorResponse.put("error", "CSV_IMPORT_ERROR");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("transactionId", transactionId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .header("X-Transaction-ID", transactionId)
                    .body(errorResponse);
        }
    }

    /**
     * Error response format for all error cases.
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