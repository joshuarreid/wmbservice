package com.example.wmbservice.service;

import com.example.wmbservice.model.ProjectedTransaction;
import com.example.wmbservice.model.ProjectedTransactionList;
import com.example.wmbservice.repository.ProjectedTransactionRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Service layer for ProjectedTransaction CRUD with deduplication (by business-key columns),
 * validation and robust logging. Does not require a persisted row_hash column.
 */
@Service
public class ProjectedTransactionService {
    private static final Logger logger = LoggerFactory.getLogger(ProjectedTransactionService.class);
    private final ProjectedTransactionRepository repository;

    public ProjectedTransactionService(ProjectedTransactionRepository repository) {
        this.repository = repository;
    }

    /**
     * Exception thrown for duplicate projected transactions.
     */
    public static class DuplicateProjectedTransactionException extends RuntimeException {
        public DuplicateProjectedTransactionException(String message) { super(message); }
    }

    /**
     * Exception thrown when a projected transaction is not found.
     */
    public static class ProjectedTransactionNotFoundException extends RuntimeException {
        public ProjectedTransactionNotFoundException(String message) { super(message); }
    }

    /**
     * Create a new ProjectedTransaction, enforcing deduplication by matching business-key columns.
     * If transactionDate is null (hand-add), sets it to today's date.
     *
     * @param transaction   The projected transaction to create.
     * @param transactionId X-Transaction-ID for logging/traceability.
     * @return The created ProjectedTransaction.
     */
    @Transactional
    public ProjectedTransaction createTransaction(ProjectedTransaction transaction, String transactionId) {
        logger.info("createProjectedTransaction entered. transactionId={}, payload={}", transactionId, transaction);

        if (transaction == null) {
            logger.warn("createProjectedTransaction received null payload. transactionId={}", transactionId);
            throw new IllegalArgumentException("Transaction payload must not be null");
        }

        if (transaction.getTransactionDate() == null) {
            LocalDate now = LocalDate.now();
            transaction.setTransactionDate(now);
            logger.debug("transactionDate was null; set to current date {}. transactionId={}", now, transactionId);
        }

        transaction.setCreatedTime(LocalDateTime.now());

        // Generate transient row hash for logging and potential in-memory use
        String rowHash = generateRowHash(transaction);
        transaction.setRowHash(rowHash);
        logger.debug("Generated (transient) rowHash for projected tx. transactionId={}, rowHash={}", transactionId, rowHash);

        try {
            Optional<ProjectedTransaction> existing = repository.findByBusinessKey(
                    transaction.getName(),
                    transaction.getAccount(),
                    transaction.getAmount(),
                    transaction.getCategory(),
                    transaction.getCriticality(),
                    transaction.getTransactionDate(),
                    transaction.getPaymentMethod(),
                    transaction.getStatementPeriod()
            );

            if (existing.isPresent()) {
                logger.warn("Duplicate projected transaction detected by business-key. transactionId={}, statementPeriod={}, rowHash={}",
                        transactionId, transaction.getStatementPeriod(), rowHash);
                throw new DuplicateProjectedTransactionException("Projected transaction already exists for this statement period.");
            }

            ProjectedTransaction saved = repository.save(transaction);
            logger.info("Projected transaction created successfully. transactionId={}, id={}", transactionId, saved.getId());
            return saved;
        } catch (DataIntegrityViolationException e) {
            logger.error("Data integrity violation on createProjectedTransaction. transactionId={}, error={}", transactionId, e.getMessage(), e);
            throw new DuplicateProjectedTransactionException("Projected transaction already exists for this statement period.");
        } catch (Exception e) {
            logger.error("Unexpected error in createProjectedTransaction. transactionId={}, error={}", transactionId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Get a projected transaction by ID.
     *
     * @param id            Transaction ID.
     * @param transactionId X-Transaction-ID for logging.
     * @return ProjectedTransaction if found.
     */
    @Transactional
    public ProjectedTransaction getTransaction(Long id, String transactionId) {
        logger.info("getProjectedTransaction entered. transactionId={}, id={}", transactionId, id);
        Optional<ProjectedTransaction> found = repository.findById(id);
        if (found.isPresent()) {
            logger.info("getProjectedTransaction successful. transactionId={}, id={}", transactionId, id);
            return found.get();
        } else {
            logger.warn("Projected transaction not found. transactionId={}, id={}", transactionId, id);
            throw new ProjectedTransactionNotFoundException("Projected transaction not found.");
        }
    }

    /**
     * Get projected transactions, optionally filtered.
     *
     * @param statementPeriod Optional statement period filter.
     * @param account         Optional account filter.
     * @param category        Optional category filter.
     * @param criticality     Optional criticality filter.
     * @param paymentMethod   Optional payment method filter.
     * @param transactionId   X-Transaction-ID for logging.
     * @return ProjectedTransactionList containing the results.
     */
    @Transactional
    public ProjectedTransactionList getTransactions(String statementPeriod, String account, String category, String criticality, String paymentMethod, String transactionId) {
        logger.info("getProjectedTransactions entered. transactionId={}, filters: statementPeriod={}, account={}, category={}, criticality={}, paymentMethod={}",
                transactionId, statementPeriod, account, category, criticality, paymentMethod);

        List<ProjectedTransaction> results;
        if (statementPeriod != null || account != null || category != null || criticality != null || paymentMethod != null) {
            logger.debug("Fetching filtered projected transactions. transactionId={}", transactionId);
            results = repository.findByFilters(statementPeriod, account, category, criticality, paymentMethod);
            logger.debug("Filtered projected transactions fetched. transactionId={}, count={}", transactionId, results.size());
        } else {
            logger.debug("Fetching all projected transactions. transactionId={}", transactionId);
            results = new ArrayList<>();
            repository.findAll().forEach(results::add);
            logger.debug("All projected transactions fetched. transactionId={}, count={}", transactionId, results.size());
        }

        logger.info("getProjectedTransactions successful. transactionId={}, resultCount={}", transactionId, results.size());
        return new ProjectedTransactionList(results);
    }

    /**
     * Update an existing projected transaction.
     *
     * @param id            Transaction ID to update.
     * @param updated       Updated projected transaction fields.
     * @param transactionId X-Transaction-ID for logging.
     * @return Updated ProjectedTransaction.
     */
    @Transactional
    public ProjectedTransaction updateTransaction(Long id, ProjectedTransaction updated, String transactionId) {
        logger.info("updateProjectedTransaction entered. transactionId={}, id={}, payload={}", transactionId, id, updated);

        if (updated == null) {
            logger.warn("updateProjectedTransaction received null payload. transactionId={}, id={}", transactionId, id);
            throw new IllegalArgumentException("Updated transaction payload must not be null");
        }

        Optional<ProjectedTransaction> existingOpt = repository.findById(id);
        if (existingOpt.isEmpty()) {
            logger.warn("Projected transaction not found for update. transactionId={}, id={}", transactionId, id);
            throw new ProjectedTransactionNotFoundException("Projected transaction not found for update.");
        }

        ProjectedTransaction existing = existingOpt.get();

        // Copy updatable fields
        existing.setName(updated.getName());
        existing.setAmount(updated.getAmount());
        existing.setCategory(updated.getCategory());
        existing.setCriticality(updated.getCriticality());
        existing.setTransactionDate(updated.getTransactionDate());
        existing.setAccount(updated.getAccount());
        existing.setStatus(updated.getStatus());
        existing.setPaymentMethod(updated.getPaymentMethod());
        existing.setStatementPeriod(updated.getStatementPeriod());
        existing.setCreatedTime(updated.getCreatedTime());

        // Generate transient row hash for logging
        String newRowHash = generateRowHash(existing);
        existing.setRowHash(newRowHash);
        logger.debug("Generated (transient) new rowHash for update. transactionId={}, rowHash={}", transactionId, newRowHash);

        try {
            Optional<ProjectedTransaction> duplicate = repository.findByBusinessKey(
                    existing.getName(),
                    existing.getAccount(),
                    existing.getAmount(),
                    existing.getCategory(),
                    existing.getCriticality(),
                    existing.getTransactionDate(),
                    existing.getPaymentMethod(),
                    existing.getStatementPeriod()
            );

            if (duplicate.isPresent() && !duplicate.get().getId().equals(id)) {
                logger.warn("Duplicate projected transaction detected on update by business-key. transactionId={}, rowHash={}, statementPeriod={}",
                        transactionId, newRowHash, existing.getStatementPeriod());
                throw new DuplicateProjectedTransactionException("Projected transaction already exists for this statement period.");
            }

            ProjectedTransaction saved = repository.save(existing);
            logger.info("updateProjectedTransaction successful. transactionId={}, id={}", transactionId, id);
            return saved;
        } catch (DataIntegrityViolationException e) {
            logger.error("Data integrity violation on updateProjectedTransaction. transactionId={}, error={}", transactionId, e.getMessage(), e);
            throw new DuplicateProjectedTransactionException("Projected transaction already exists for this statement period.");
        } catch (Exception e) {
            logger.error("Unexpected error on updateProjectedTransaction. transactionId={}, error={}", transactionId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Delete a projected transaction by ID.
     *
     * @param id            Transaction ID to delete.
     * @param transactionId X-Transaction-ID for logging.
     * @return true if deleted, false if not found.
     */
    @Transactional
    public boolean deleteTransaction(Long id, String transactionId) {
        logger.info("deleteProjectedTransaction entered. transactionId={}, id={}", transactionId, id);
        Optional<ProjectedTransaction> existing = repository.findById(id);
        if (existing.isEmpty()) {
            logger.warn("Projected transaction not found for delete. transactionId={}, id={}", transactionId, id);
            return false;
        }
        try {
            repository.deleteById(id);
            logger.info("deleteProjectedTransaction successful. transactionId={}, id={}", transactionId, id);
            return true;
        } catch (Exception e) {
            logger.error("Error deleting projected transaction. transactionId={}, id={}, error={}", transactionId, id, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Delete all projected transactions.
     *
     * @param transactionId X-Transaction-ID for logging.
     * @return Number of transactions deleted.
     */
    @Transactional
    public long deleteAllTransactions(String transactionId) {
        logger.info("deleteAllProjectedTransactions entered. transactionId={}", transactionId);
        long count = repository.count();
        if (count == 0) {
            logger.info("No projected transactions to delete. transactionId={}", transactionId);
            return 0L;
        }
        try {
            repository.deleteAll();
            logger.info("All projected transactions deleted. transactionId={}, deletedCount={}", transactionId, count);
            return count;
        } catch (Exception e) {
            logger.error("Error deleting all projected transactions. transactionId={}, error={}", transactionId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Generate SHA-256 row hash for deduplication (transient).
     * Includes only business-key fields per design: name, account, amount, category, criticality, transactionDate, paymentMethod, statementPeriod.
     *
     * @param tx ProjectedTransaction to hash.
     * @return SHA-256 hash string (lower hex).
     */
    public String generateRowHash(ProjectedTransaction tx) {
        logger.debug("generateRowHash entered for projected transaction: name={}, account={}, amount={}, category={}, criticality={}, transactionDate={}, paymentMethod={}, statementPeriod={}",
                tx.getName(), tx.getAccount(), tx.getAmount(), tx.getCategory(), tx.getCriticality(), tx.getTransactionDate(), tx.getPaymentMethod(), tx.getStatementPeriod());
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String raw = String.join("|",
                    safe(tx.getName()),
                    safe(tx.getAccount()),
                    safeAmount(tx.getAmount()),
                    safe(tx.getCategory()),
                    safe(tx.getCriticality()),
                    safeDate(tx.getTransactionDate()),
                    safe(tx.getPaymentMethod()),
                    safe(tx.getStatementPeriod())
            );
            byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            try (Formatter formatter = new Formatter()) {
                for (byte b : hash) {
                    formatter.format("%02x", b);
                }
                String result = formatter.toString();
                logger.debug("generateRowHash successful. raw={}, hash={}", raw, result);
                return result;
            }
        } catch (Exception e) {
            logger.error("Error generating rowHash for projected transaction. error={}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate row hash", e);
        }
    }

    // Helper methods for safely formatting values
    private String safe(String val) { return val == null ? "" : val.trim().toLowerCase(); }
    private String safeAmount(BigDecimal val) { return val == null ? "" : val.setScale(2, RoundingMode.HALF_UP).toString(); }
    private String safeDate(java.time.LocalDate val) { return val == null ? "" : val.toString(); }
    private String safeDateTime(LocalDateTime val) { return val == null ? "" : val.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME); }
}