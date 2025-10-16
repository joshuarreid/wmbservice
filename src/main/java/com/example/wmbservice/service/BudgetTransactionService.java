package com.example.wmbservice.service;

import com.example.wmbservice.model.BudgetTransaction;
import com.example.wmbservice.repository.BudgetTransactionRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Formatter;
import java.util.List;
import java.util.Optional;

/**
 * Service layer for BudgetTransaction CRUD with deduplication and robust logging.
 * All methods propagate X-Transaction-ID for traceability and error handling.
 */
@Service
public class BudgetTransactionService {

    private static final Logger logger = LoggerFactory.getLogger(BudgetTransactionService.class);
    private final BudgetTransactionRepository repository;

    public BudgetTransactionService(BudgetTransactionRepository repository) {
        this.repository = repository;
    }

    /**
     * Exception thrown for duplicate transactions.
     */
    public static class DuplicateTransactionException extends RuntimeException {
        public DuplicateTransactionException(String message) { super(message); }
    }

    /**
     * Exception thrown when a transaction is not found.
     */
    public static class TransactionNotFoundException extends RuntimeException {
        public TransactionNotFoundException(String message) { super(message); }
    }

    /**
     * Create a new BudgetTransaction, enforcing deduplication by row hash.
     * Sets transactionDate to current date if not provided (for UI hand-adds).
     * @param transaction The transaction to create.
     * @param transactionId The propagated X-Transaction-ID for logging.
     * @return The created transaction.
     */
    @Transactional
    public BudgetTransaction createTransaction(BudgetTransaction transaction, String transactionId) {
        logger.info("createTransaction entered. transactionId={}, payload={}", transactionId, transaction);

        // If transactionDate is null (hand-add), set to current date
        if (transaction.getTransactionDate() == null) {
            LocalDate now = LocalDate.now();
            logger.info("transactionDate not provided, setting to current date: {}. transactionId={}", now, transactionId);
            transaction.setTransactionDate(now);
        }

        transaction.setCreatedTime(LocalDateTime.now());
        transaction.setRowHash(generateRowHash(transaction));
        logger.debug("Generated rowHash for transaction. transactionId={}, rowHash={}", transactionId, transaction.getRowHash());

        // Check for duplicate by rowHash and statementPeriod
        Optional<BudgetTransaction> existing = repository.findByRowHashAndStatementPeriod(transaction.getRowHash(), transaction.getStatementPeriod());
        if (existing.isPresent()) {
            logger.warn("Duplicate transaction detected. transactionId={}, rowHash={}, statementPeriod={}", transactionId, transaction.getRowHash(), transaction.getStatementPeriod());
            throw new DuplicateTransactionException("Transaction already exists for this statement period.");
        }

        try {
            BudgetTransaction saved = repository.save(transaction);
            logger.info("Transaction created successfully. transactionId={}, id={}", transactionId, saved.getId());
            return saved;
        } catch (DataIntegrityViolationException e) {
            logger.error("Data integrity violation on create. transactionId={}, error={}", transactionId, e.getMessage(), e);
            throw new DuplicateTransactionException("Transaction already exists for this statement period.");
        } catch (Exception e) {
            logger.error("Unexpected error on createTransaction. transactionId={}, error={}", transactionId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Get all transactions, with optional filters.
     * @param statementPeriod Optional statement period filter.
     * @param account Optional account filter.
     * @param category Optional category filter.
     * @param paymentMethod Optional payment method filter.
     * @param transactionId X-Transaction-ID for logging.
     * @return List of BudgetTransaction matching filters.
     */
    @Transactional
    public List<BudgetTransaction> getTransactions(String statementPeriod, String account, String category, String paymentMethod, String transactionId) {
        logger.info("getTransactions entered. transactionId={}, filters: statementPeriod={}, account={}, category={}, paymentMethod={}",
                transactionId, statementPeriod, account, category, paymentMethod);

        List<BudgetTransaction> results;
        if (statementPeriod != null || account != null || category != null || paymentMethod != null) {
            results = repository.findByFilters(statementPeriod, account, category, paymentMethod);
            logger.debug("Filtered transactions fetched. transactionId={}, count={}", transactionId, results.size());
        } else {
            results = repository.findAll();
            logger.debug("All transactions fetched. transactionId={}, count={}", transactionId, results.size());
        }
        logger.info("getTransactions successful. transactionId={}, resultCount={}", transactionId, results.size());
        return results;
    }

    /**
     * Get a transaction by ID.
     * @param id Transaction ID.
     * @param transactionId X-Transaction-ID for logging.
     * @return BudgetTransaction if found.
     */
    @Transactional
    public BudgetTransaction getTransaction(Long id, String transactionId) {
        logger.info("getTransaction entered. transactionId={}, id={}", transactionId, id);
        Optional<BudgetTransaction> found = repository.findById(id);
        if (found.isPresent()) {
            logger.info("getTransaction successful. transactionId={}, id={}", transactionId, id);
            return found.get();
        } else {
            logger.warn("Transaction not found. transactionId={}, id={}", transactionId, id);
            throw new TransactionNotFoundException("Transaction not found.");
        }
    }

    /**
     * Update an existing transaction.
     * @param id Transaction ID.
     * @param updated Updated transaction fields.
     * @param transactionId X-Transaction-ID for logging.
     * @return Updated BudgetTransaction.
     */
    @Transactional
    public BudgetTransaction updateTransaction(Long id, BudgetTransaction updated, String transactionId) {
        logger.info("updateTransaction entered. transactionId={}, id={}, payload={}", transactionId, id, updated);

        Optional<BudgetTransaction> existingOpt = repository.findById(id);
        if (existingOpt.isEmpty()) {
            logger.warn("Transaction not found for update. transactionId={}, id={}", transactionId, id);
            throw new TransactionNotFoundException("Transaction not found for update.");
        }
        BudgetTransaction existing = existingOpt.get();

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

        String newRowHash = generateRowHash(existing);
        existing.setRowHash(newRowHash);
        logger.debug("Generated new rowHash for updated transaction. transactionId={}, rowHash={}", transactionId, newRowHash);

        // Check for duplicate if rowHash changes
        Optional<BudgetTransaction> duplicate = repository.findByRowHashAndStatementPeriod(newRowHash, existing.getStatementPeriod());
        if (duplicate.isPresent() && !duplicate.get().getId().equals(id)) {
            logger.warn("Duplicate transaction detected on update. transactionId={}, rowHash={}, statementPeriod={}", transactionId, newRowHash, existing.getStatementPeriod());
            throw new DuplicateTransactionException("Transaction already exists for this statement period.");
        }

        try {
            BudgetTransaction saved = repository.save(existing);
            logger.info("updateTransaction successful. transactionId={}, id={}", transactionId, id);
            return saved;
        } catch (DataIntegrityViolationException e) {
            logger.error("Data integrity violation on update. transactionId={}, error={}", transactionId, e.getMessage(), e);
            throw new DuplicateTransactionException("Transaction already exists for this statement period.");
        } catch (Exception e) {
            logger.error("Unexpected error on updateTransaction. transactionId={}, error={}", transactionId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Delete a transaction by ID.
     * @param id Transaction ID.
     * @param transactionId X-Transaction-ID for logging.
     * @return true if deleted, false if not found.
     */
    @Transactional
    public boolean deleteTransaction(Long id, String transactionId) {
        logger.info("deleteTransaction entered. transactionId={}, id={}", transactionId, id);
        Optional<BudgetTransaction> existing = repository.findById(id);
        if (existing.isEmpty()) {
            logger.warn("Transaction not found for delete. transactionId={}, id={}", transactionId, id);
            return false;
        }
        try {
            repository.deleteById(id);
            logger.info("deleteTransaction successful. transactionId={}, id={}", transactionId, id);
            return true;
        } catch (Exception e) {
            logger.error("Error deleting transaction. transactionId={}, id={}, error={}", transactionId, id, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Generate SHA-256 row hash for deduplication, now including createdTime.
     * @param tx BudgetTransaction to hash.
     * @return SHA-256 hash string.
     */
    public String generateRowHash(BudgetTransaction tx) {
        logger.debug("generateRowHash entered for transaction: name={}, account={}, amount={}, category={}, criticality={}, transactionDate={}, paymentMethod={}, statementPeriod={}, createdTime={}",
                tx.getName(), tx.getAccount(), tx.getAmount(), tx.getCategory(), tx.getCriticality(), tx.getTransactionDate(), tx.getPaymentMethod(), tx.getStatementPeriod(), tx.getCreatedTime());
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
                    safe(tx.getStatementPeriod()),
                    safeDateTime(tx.getCreatedTime())
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
            logger.error("Error generating rowHash. error={}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate row hash", e);
        }
    }

    // Helper methods for safely formatting values
    private String safe(String val) { return val == null ? "" : val.trim().toLowerCase(); }
    private String safeAmount(BigDecimal val) { return val == null ? "" : val.setScale(2, BigDecimal.ROUND_HALF_UP).toString(); }
    private String safeDate(LocalDate val) { return val == null ? "" : val.toString(); }
    private String safeDateTime(LocalDateTime val) { return val == null ? "" : val.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME); }
}