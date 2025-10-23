package com.example.wmbservice.service;
import com.example.wmbservice.model.AccountBudgetTransactionList;
import com.example.wmbservice.model.BudgetTransaction;
import com.example.wmbservice.model.BudgetTransactionList;
import com.example.wmbservice.model.StatementPeriod;
import com.example.wmbservice.repository.BudgetTransactionRepository;
import com.example.wmbservice.repository.StatementPeriodRepository;
import com.example.wmbservice.util.BudgetTransactionCsvImporter;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Service layer for BudgetTransaction CRUD with deduplication and robust logging.
 * All methods propagate X-Transaction-ID for traceability and error handling.
 *
 * Additional behavior:
 * - Validates statement period format (ALLCAPSMONTHYYYY) for new/updated transactions.
 * - Ensures a corresponding StatementPeriod row exists in the statement_periods table, creating it when missing.
 */
@Service
public class BudgetTransactionService {

    private static final Logger logger = LoggerFactory.getLogger(BudgetTransactionService.class);
    private final BudgetTransactionRepository repository;
    private final BudgetTransactionCsvImporter csvImporter;
    private final StatementPeriodRepository statementPeriodRepository;

    // Regex enforces FULL MONTH NAME (uppercase or mixed-case normalized) followed by 4-digit year e.g. OCTOBER2025
    private static final Pattern PERIOD_NAME_PATTERN = Pattern.compile(
            "^(JANUARY|FEBRUARY|MARCH|APRIL|MAY|JUNE|JULY|AUGUST|SEPTEMBER|OCTOBER|NOVEMBER|DECEMBER)\\d{4}$",
            Pattern.CASE_INSENSITIVE
    );

    public BudgetTransactionService(BudgetTransactionRepository repository,
                                    BudgetTransactionCsvImporter csvImporter,
                                    StatementPeriodRepository statementPeriodRepository) {
        this.repository = repository;
        this.csvImporter = csvImporter;
        this.statementPeriodRepository = statementPeriodRepository;
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
     * Also validates the statementPeriod format and ensures a StatementPeriod row exists (creates it if missing).
     * Sets transactionDate to current date if not provided (for UI hand-adds).
     *
     * @param transaction   The transaction to create.
     * @param transactionId The propagated X-Transaction-ID for logging.
     * @return The created transaction.
     */
    @Transactional
    public BudgetTransaction createTransaction(BudgetTransaction transaction, String transactionId) {
        logger.info("createTransaction entered. transactionId={}, payload={}", transactionId, transaction);

        if (transaction == null) {
            logger.warn("createTransaction received null payload. transactionId={}", transactionId);
            throw new IllegalArgumentException("Transaction payload is required");
        }

        // Ensure statementPeriod is present and valid, and ensure it exists in DB
        String rawPeriod = transaction.getStatementPeriod();
        String normalizedPeriod = normalizeAndEnsureStatementPeriod(rawPeriod, transactionId);
        transaction.setStatementPeriod(normalizedPeriod);

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
     * @return BudgetTransactionList containing transactions, count and total.
     */
    @Transactional
    public BudgetTransactionList getTransactions(String statementPeriod, String account, String category, String criticality, String paymentMethod, String transactionId) {
        logger.info("getTransactions entered. transactionId={}, filters: statementPeriod={}, account={}, category={}, criticality= {}, paymentMethod={}",
                transactionId, statementPeriod, account, category, criticality, paymentMethod);

        List<BudgetTransaction> results;
        if (statementPeriod == null || statementPeriod.isBlank()) {
            results = repository.findAll();
            logger.debug("All transactions fetched. transactionId={}, count={}", transactionId, results.size());
        } else {
            results = repository.findByFilters(statementPeriod, account, category, criticality, paymentMethod);
            logger.debug("Filtered transactions fetched. transactionId={}, count={}", transactionId, results.size());
        }
        logger.info("getTransactions successful. transactionId={}, resultCount={}", transactionId, results.size());
        return new BudgetTransactionList(results);
    }


    /**
     * Get transactions for a specific account, separated into personal and joint lists for AccountBudgetTransactionList.
     */
    public AccountBudgetTransactionList getAccountBudgetTransactionList(
            String account,
            String statementPeriod,
            String category,
            String criticality,
            String paymentMethod,
            String transactionId
    ) {
        logger.info("getAccountBudgetTransactionList entered. transactionId={}, account={}", transactionId, account);

        List<BudgetTransaction> personalTxs = new ArrayList<>();
        List<BudgetTransaction> jointTxs = new ArrayList<>();

        if ("joint".equalsIgnoreCase(account)) {
            // Only joint transactions
            jointTxs = repository.findByFilters(statementPeriod, "joint", category, criticality, paymentMethod);
        } else {
            // Personal transactions
            personalTxs = repository.findByFilters(statementPeriod, account, category, criticality, paymentMethod);

            // Joint transactions, split for this account
            List<BudgetTransaction> jointRaw = repository.findByFilters(statementPeriod, "joint", category, criticality, paymentMethod);
            for (BudgetTransaction jt : jointRaw) {
                BudgetTransaction split = new BudgetTransaction();
                split.setName("[Split] " + jt.getName());
                split.setAccount(account);
                split.setAmount(jt.getAmount() != null ? jt.getAmount().divide(new java.math.BigDecimal("2.00"), 2, java.math.RoundingMode.HALF_UP) : null);
                split.setCategory(jt.getCategory());
                split.setCriticality(jt.getCriticality());
                split.setTransactionDate(jt.getTransactionDate());
                split.setStatus(jt.getStatus());
                split.setPaymentMethod(jt.getPaymentMethod());
                split.setStatementPeriod(jt.getStatementPeriod());
                split.setCreatedTime(jt.getCreatedTime());
                split.setRowHash(null);
                jointTxs.add(split);
            }
        }

        BudgetTransactionList personalList = new BudgetTransactionList(personalTxs);
        BudgetTransactionList jointList = new BudgetTransactionList(jointTxs);
        return new AccountBudgetTransactionList(personalList, jointList);
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
     * Validates and ensures updated statementPeriod exists when provided.
     *
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

        // If a new statementPeriod is provided, validate and ensure it exists
        if (updated.getStatementPeriod() != null && !updated.getStatementPeriod().isBlank()) {
            String normalized = normalizeAndEnsureStatementPeriod(updated.getStatementPeriod(), transactionId);
            existing.setStatementPeriod(normalized);
        }

        // Copy updatable fields
        existing.setName(updated.getName());
        existing.setAmount(updated.getAmount());
        existing.setCategory(updated.getCategory());
        existing.setCriticality(updated.getCriticality());
        existing.setTransactionDate(updated.getTransactionDate());
        existing.setAccount(updated.getAccount());
        existing.setStatus(updated.getStatus());
        existing.setPaymentMethod(updated.getPaymentMethod());
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
     * Delete all transactions.
     * @param transactionId X-Transaction-ID for logging.
     * @return Number of transactions deleted.
     */
    public long deleteAllTransactions(String transactionId) {
        logger.info("deleteAllTransactions entered. transactionId={}", transactionId);
        long count = repository.count();
        if (count == 0) {
            logger.info("No transactions to delete. transactionId={}", transactionId);
            return 0L;
        }
        try {
            repository.deleteAll();
            logger.info("All transactions deleted. transactionId={}, deletedCount={}", transactionId, count);
            return count;
        } catch (Exception e) {
            logger.error("Error deleting all transactions. transactionId={}, error={}", transactionId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Generate SHA-256 row hash for deduplication.
     * Includes only business-key fields per design: name, account, amount, category, criticality, transactionDate, paymentMethod, statementPeriod.
     * Excludes createdTime and other non-deterministic fields to ensure deduplication works as intended.
     * @param tx BudgetTransaction to hash.
     * @return SHA-256 hash string.
     */
    public String generateRowHash(BudgetTransaction tx) {
        logger.debug("generateRowHash entered for transaction: name={}, account={}, amount={}, category={}, criticality={}, transactionDate={}, paymentMethod={}, statementPeriod={}",
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
            logger.error("Error generating rowHash. error={}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate row hash", e);
        }
    }

    /**
     * Bulk imports transactions from a CSV file with deduplication and error reporting.
     * Each row is mapped, validated, hashed, and checked for duplicates before insert.
     * Ensures the provided statementPeriod is valid and present in the statement_periods table.
     *
     * @param file multipart CSV file
     * @param statementPeriod statement period to attribute to rows
     * @param transactionId propagated transaction id
     * @return summary result
     */
    @Transactional
    public BulkImportResult bulkImportTransactions(MultipartFile file, String statementPeriod, String transactionId) {
        logger.info("bulkImportTransactions entered. transactionId={}, statementPeriod={}", transactionId, statementPeriod);

        if (statementPeriod == null || statementPeriod.isBlank()) {
            logger.warn("bulkImportTransactions missing statementPeriod. transactionId={}", transactionId);
            throw new IllegalArgumentException("statementPeriod is required for bulk import");
        }

        // Normalize and ensure statementPeriod exists
        String normalizedPeriod = normalizeAndEnsureStatementPeriod(statementPeriod, transactionId);

        int insertedCount = 0;
        int duplicateCount = 0;
        List<Map<String, Object>> errors = new ArrayList<>();

        // Delegate robust CSV parsing/normalization to importer
        List<BudgetTransaction> transactions = csvImporter.parseCsvToTransactions(file, transactionId, errors);

        for (int i = 0; i < transactions.size(); i++) {
            BudgetTransaction transaction = transactions.get(i);
            if (transaction == null) continue;

            transaction.setStatementPeriod(normalizedPeriod);
            transaction.setRowHash(generateRowHash(transaction));
            try {
                Optional<BudgetTransaction> existing = repository.findByRowHashAndStatementPeriod(
                        transaction.getRowHash(), normalizedPeriod);
                if (existing.isPresent()) {
                    duplicateCount++;
                    logger.warn("Duplicate detected during bulk import. transactionId={}, rowHash={}, row={}",
                            transactionId, transaction.getRowHash(), i + 2);
                    continue;
                }
                repository.save(transaction);
                insertedCount++;
                logger.debug("Inserted transaction in bulk import. transactionId={}, rowHash={}, row={}",
                        transactionId, transaction.getRowHash(), i + 2);
            } catch (Exception e) {
                logger.error("Error inserting transaction in bulk import. transactionId={}, row={}, error={}",
                        transactionId, i + 2, e.getMessage(), e);
                Map<String, Object> errorDetail = new HashMap<>();
                errorDetail.put("row", i + 2);
                errorDetail.put("message", e.getMessage());
                errors.add(errorDetail);
            }
        }

        logger.info("bulkImportTransactions completed. transactionId={}, insertedCount={}, duplicateCount={}, errorCount={}",
                transactionId, insertedCount, duplicateCount, errors.size());
        return new BulkImportResult(insertedCount, duplicateCount, errors);
    }

    /**
     * Normalize the incoming periodName (trim + uppercase), validate it against the required pattern
     * and ensure a StatementPeriod row exists. If not present, creates a minimal StatementPeriod row.
     *
     * @param periodName raw incoming period name
     * @param transactionId propagated transaction id for logging
     * @return normalized period name (uppercase)
     */
    private String normalizeAndEnsureStatementPeriod(String periodName, String transactionId) {
        logger.debug("normalizeAndEnsureStatementPeriod entered. transactionId={}, rawPeriodName={}", transactionId, periodName);

        if (periodName == null) {
            logger.warn("periodName is null. transactionId={}", transactionId);
            throw new IllegalArgumentException("statementPeriod is required and must be in the form MONTHYYYY (e.g. OCTOBER2025)");
        }

        String normalized = periodName.trim().toUpperCase(Locale.ENGLISH);
        logger.debug("periodName trimmed and upper-cased. transactionId={}, normalized={}", transactionId, normalized);

        if (!PERIOD_NAME_PATTERN.matcher(normalized).matches()) {
            logger.warn("periodName failed format validation. transactionId={}, normalized={}", transactionId, normalized);
            throw new IllegalArgumentException("statementPeriod must be in the form MONTHYYYY (full month name + 4-digit year). Example: OCTOBER2025");
        }

        // Check repository for existence
        Optional<StatementPeriod> existing = statementPeriodRepository.findByPeriodName(normalized);
        if (existing.isPresent()) {
            logger.debug("StatementPeriod exists. transactionId={}, periodName={}", transactionId, normalized);
            return normalized;
        }

        // Create minimal StatementPeriod row
        try {
            StatementPeriod sp = new StatementPeriod();
            sp.setPeriodName(normalized);
            sp.setCreatedAt(LocalDateTime.now());
            // startDate/endDate left null - business rules may compute these later
            StatementPeriod saved = statementPeriodRepository.save(sp);
            logger.info("Created missing StatementPeriod. transactionId={}, id={}, periodName={}", transactionId, saved.getId(), normalized);
            return normalized;
        } catch (Exception e) {
            logger.error("Error creating StatementPeriod. transactionId={}, periodName={}, error={}", transactionId, normalized, e.getMessage(), e);
            throw new RuntimeException("Failed to create or verify statement period", e);
        }
    }

    /**
     * Result object for bulk import summary.
     */
    public static class BulkImportResult {
        private final int insertedCount;
        private final int duplicateCount;
        private final List<Map<String, Object>> errors;

        public BulkImportResult(int insertedCount, int duplicateCount, List<Map<String, Object>> errors) {
            this.insertedCount = insertedCount;
            this.duplicateCount = duplicateCount;
            this.errors = errors;
        }

        public int getInsertedCount() { return insertedCount; }
        public int getDuplicateCount() { return duplicateCount; }
        public List<Map<String, Object>> getErrors() { return errors; }
    }

    // Helper methods for safely formatting values
    private String safe(String val) { return val == null ? "" : val.trim().toLowerCase(); }
    private String safeAmount(BigDecimal val) { return val == null ? "" : val.setScale(2, RoundingMode.HALF_UP).toString(); }
    private String safeDate(LocalDate val) { return val == null ? "" : val.toString(); }
    private String safeDateTime(LocalDateTime val) { return val == null ? "" : val.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME); }
}