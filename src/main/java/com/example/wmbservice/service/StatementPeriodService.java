package com.example.wmbservice.service;

import com.example.wmbservice.model.StatementPeriod;
import com.example.wmbservice.repository.StatementPeriodRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Service layer for StatementPeriod CRUD operations with deduplication and robust logging.
 * All methods accept a propagated X-Transaction-ID for traceability in logs.
 */
@Service
public class StatementPeriodService {

    private static final Logger logger = LoggerFactory.getLogger(StatementPeriodService.class);
    private final StatementPeriodRepository repository;

    // Regex enforces MONTHNAME in full (uppercase) followed by 4-digit year, e.g. OCTOBER2025
    private static final Pattern PERIOD_NAME_PATTERN = Pattern.compile(
            "^(JANUARY|FEBRUARY|MARCH|APRIL|MAY|JUNE|JULY|AUGUST|SEPTEMBER|OCTOBER|NOVEMBER|DECEMBER)\\d{4}$"
    );

    public StatementPeriodService(StatementPeriodRepository repository) {
        this.repository = repository;
    }

    /**
     * Exception thrown when attempting to create or update a period that conflicts with an existing period name.
     */
    public static class DuplicateStatementPeriodException extends RuntimeException {
        public DuplicateStatementPeriodException(String message) { super(message); }
    }

    /**
     * Exception thrown when a statement period cannot be found.
     */
    public static class StatementPeriodNotFoundException extends RuntimeException {
        public StatementPeriodNotFoundException(String message) { super(message); }
    }

    /**
     * Create a new statement period. Ensures periodName uniqueness and correct format (ALLCAPSMONTHYYYY).
     *
     * @param statementPeriod payload to create.
     * @param transactionId   propagated X-Transaction-ID for traceability.
     * @return saved StatementPeriod with id and createdAt set.
     */
    @Transactional
    public StatementPeriod createStatementPeriod(StatementPeriod statementPeriod, String transactionId) {
        logger.info("createStatementPeriod entered. transactionId={}, payload={}", transactionId, statementPeriod);

        if (statementPeriod == null) {
            logger.warn("createStatementPeriod received null payload. transactionId={}", transactionId);
            throw new IllegalArgumentException("statementPeriod payload is required");
        }

        String incomingName = statementPeriod.getPeriodName();
        String normalizedName = normalizeAndValidatePeriodName(incomingName, transactionId);

        logger.debug("Normalized periodName for create. transactionId={}, periodName={}", transactionId, normalizedName);

        Optional<StatementPeriod> existing = repository.findByPeriodName(normalizedName);
        if (existing.isPresent()) {
            logger.warn("Duplicate statement period detected on create. transactionId={}, periodName={}", transactionId, normalizedName);
            throw new DuplicateStatementPeriodException("Statement period already exists with the given periodName.");
        }

        // Set createdAt to now to reflect creation time (DB has default, but set explicitly for consistency)
        statementPeriod.setPeriodName(normalizedName);
        statementPeriod.setCreatedAt(LocalDateTime.now());

        try {
            StatementPeriod saved = repository.save(statementPeriod);
            logger.info("createStatementPeriod successful. transactionId={}, id={}", transactionId, saved.getId());
            return saved;
        } catch (DataIntegrityViolationException e) {
            logger.error("Data integrity violation on createStatementPeriod. transactionId={}, error={}", transactionId, e.getMessage(), e);
            throw new DuplicateStatementPeriodException("Statement period already exists with the given periodName.");
        } catch (Exception e) {
            logger.error("Unexpected error on createStatementPeriod. transactionId={}, error={}", transactionId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Retrieve all statement periods, ordered by start date descending.
     *
     * @param transactionId propagated X-Transaction-ID for traceability.
     * @return list of StatementPeriod
     */
    public List<StatementPeriod> getAllStatementPeriods(String transactionId) {
        logger.info("getAllStatementPeriods entered. transactionId={}", transactionId);
        List<StatementPeriod> results = repository.findAllOrderByStartDateDesc();
        logger.info("getAllStatementPeriods successful. transactionId={}, resultCount={}", transactionId, results.size());
        return results;
    }

    /**
     * Retrieve a single statement period by id.
     *
     * @param id            identifier
     * @param transactionId propagated X-Transaction-ID for traceability.
     * @return found StatementPeriod
     */
    public StatementPeriod getStatementPeriod(Long id, String transactionId) {
        logger.info("getStatementPeriod entered. transactionId={}, id={}", transactionId, id);
        Optional<StatementPeriod> found = repository.findById(id);
        if (found.isPresent()) {
            logger.info("getStatementPeriod successful. transactionId={}, id={}", transactionId, id);
            return found.get();
        } else {
            logger.warn("StatementPeriod not found. transactionId={}, id={}", transactionId, id);
            throw new StatementPeriodNotFoundException("Statement period not found.");
        }
    }

    /**
     * Update an existing statement period.
     *
     * @param id              identifier of the period to update
     * @param updated         updated fields
     * @param transactionId   propagated X-Transaction-ID for traceability.
     * @return updated StatementPeriod
     */
    @Transactional
    public StatementPeriod updateStatementPeriod(Long id, StatementPeriod updated, String transactionId) {
        logger.info("updateStatementPeriod entered. transactionId={}, id={}, payload={}", transactionId, id, updated);

        if (updated == null) {
            logger.warn("updateStatementPeriod received null payload. transactionId={}, id={}", transactionId, id);
            throw new IllegalArgumentException("statementPeriod payload is required for update");
        }

        Optional<StatementPeriod> existingOpt = repository.findById(id);
        if (existingOpt.isEmpty()) {
            logger.warn("StatementPeriod not found for update. transactionId={}, id={}", transactionId, id);
            throw new StatementPeriodNotFoundException("Statement period not found for update.");
        }
        StatementPeriod existing = existingOpt.get();

        // Validate and normalize periodName
        String newName = updated.getPeriodName();
        String normalizedName = normalizeAndValidatePeriodName(newName, transactionId);
        logger.debug("Normalized periodName for update. transactionId={}, newPeriodName={}", transactionId, normalizedName);

        if (!normalizedName.equals(existing.getPeriodName())) {
            Optional<StatementPeriod> conflict = repository.findByPeriodName(normalizedName);
            if (conflict.isPresent() && !conflict.get().getId().equals(id)) {
                logger.warn("Duplicate statement period detected on update. transactionId={}, newPeriodName={}", transactionId, normalizedName);
                throw new DuplicateStatementPeriodException("Statement period already exists with the given periodName.");
            }
            existing.setPeriodName(normalizedName);
        }

        // Copy remaining updatable fields
        existing.setStartDate(updated.getStartDate());
        existing.setEndDate(updated.getEndDate());
        // Do not update createdAt (preserve original creation time)

        try {
            StatementPeriod saved = repository.save(existing);
            logger.info("updateStatementPeriod successful. transactionId={}, id={}", transactionId, id);
            return saved;
        } catch (DataIntegrityViolationException e) {
            logger.error("Data integrity violation on updateStatementPeriod. transactionId={}, error={}", transactionId, e.getMessage(), e);
            throw new DuplicateStatementPeriodException("Statement period already exists with the given periodName.");
        } catch (Exception e) {
            logger.error("Unexpected error on updateStatementPeriod. transactionId={}, error={}", transactionId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Delete a statement period by id.
     *
     * @param id            identifier
     * @param transactionId propagated X-Transaction-ID for traceability.
     * @return true if deleted, false if not found.
     */
    @Transactional
    public boolean deleteStatementPeriod(Long id, String transactionId) {
        logger.info("deleteStatementPeriod entered. transactionId={}, id={}", transactionId, id);
        Optional<StatementPeriod> existing = repository.findById(id);
        if (existing.isEmpty()) {
            logger.warn("StatementPeriod not found for delete. transactionId={}, id={}", transactionId, id);
            return false;
        }
        try {
            repository.deleteById(id);
            logger.info("deleteStatementPeriod successful. transactionId={}, id={}", transactionId, id);
            return true;
        } catch (Exception e) {
            logger.error("Error deleting statement period. transactionId={}, id={}, error={}", transactionId, id, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Delete all statement periods.
     *
     * @param transactionId propagated X-Transaction-ID for traceability.
     * @return number of deleted periods.
     */
    @Transactional
    public long deleteAllStatementPeriods(String transactionId) {
        logger.info("deleteAllStatementPeriods entered. transactionId={}", transactionId);
        long count = repository.count();
        if (count == 0) {
            logger.info("No statement periods to delete. transactionId={}", transactionId);
            return 0L;
        }
        try {
            repository.deleteAll();
            logger.info("deleteAllStatementPeriods successful. transactionId={}, deletedCount={}", transactionId, count);
            return count;
        } catch (Exception e) {
            logger.error("Error deleting all statement periods. transactionId={}, error={}", transactionId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Normalize and validate the incoming periodName to the required format:
     * ALLCAPSMONTHYYYY e.g. OCTOBER2025.
     *
     * This method trims, upper-cases, and validates the structure. Throws IllegalArgumentException on invalid input.
     *
     * @param periodName    incoming periodName (may be null or mixed-case)
     * @param transactionId propagated transaction id for logging
     * @return normalized periodName (uppercase)
     */
    private String normalizeAndValidatePeriodName(String periodName, String transactionId) {
        logger.debug("normalizeAndValidatePeriodName entered. transactionId={}, rawPeriodName={}", transactionId, periodName);

        if (periodName == null) {
            logger.warn("periodName is null. transactionId={}", transactionId);
            throw new IllegalArgumentException("periodName is required and must be in the form MONTHYYYY (e.g. OCTOBER2025)");
        }

        String normalized = periodName.trim().toUpperCase();
        logger.debug("periodName trimmed and upper-cased. transactionId={}, normalized={}", transactionId, normalized);

        if (!PERIOD_NAME_PATTERN.matcher(normalized).matches()) {
            logger.warn("periodName failed format validation. transactionId={}, normalized={}", transactionId, normalized);
            throw new IllegalArgumentException("periodName must be in the form MONTHYYYY (full month name, uppercase or mixed-case will be normalized). Example: OCTOBER2025");
        }

        logger.debug("periodName validated successfully. transactionId={}, normalized={}", transactionId, normalized);
        return normalized;
    }
}