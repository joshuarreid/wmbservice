package com.example.wmbservice.util;

import com.example.wmbservice.model.BudgetTransaction;
import com.opencsv.CSVReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Component dedicated to robustly importing BudgetTransactions from Notion/working-file style CSVs.
 * Handles header mapping, normalization, per-row error collection, and logging.
 */
@Component
public class BudgetTransactionCsvImporter {

    private static final Logger logger = LoggerFactory.getLogger(BudgetTransactionCsvImporter.class);

    // Logical-to-header mapping (Notion and working-file variants)
    private static final Map<String, String[]> LOGICAL_HEADERS;
    static {
        LOGICAL_HEADERS = new LinkedHashMap<>();
        LOGICAL_HEADERS.put("name", new String[]{"name", "Name"});
        LOGICAL_HEADERS.put("amount", new String[]{"amount", "Amount"});
        LOGICAL_HEADERS.put("category", new String[]{"category", "Category"});
        LOGICAL_HEADERS.put("criticality", new String[]{"criticality", "Criticality"});
        LOGICAL_HEADERS.put("transaction date", new String[]{"transaction date", "Transaction Date"});
        LOGICAL_HEADERS.put("account", new String[]{"account", "Account"});
        LOGICAL_HEADERS.put("status", new String[]{"status", "Status"});
        LOGICAL_HEADERS.put("created time", new String[]{"created time", "Created time"});
        LOGICAL_HEADERS.put("payment method", new String[]{"payment method", "Payment Method"});
    }

    /**
     * Parses a multipart CSV file into a list of BudgetTransactions.
     * Each row is mapped, normalized, and errors are added to the errors list.
     * @param file The uploaded CSV file.
     * @param transactionId The X-Transaction-ID for trace logging.
     * @param errors List to append detailed error maps (row, message).
     * @return List of BudgetTransaction (null at indices for error rows).
     */
    public List<BudgetTransaction> parseCsvToTransactions(MultipartFile file, String transactionId, List<Map<String, Object>> errors) {
        logger.info("parseCsvToTransactions entered. transactionId={}", transactionId);
        List<BudgetTransaction> transactions = new ArrayList<>();
        Map<String, Integer> headerMap = new HashMap<>();

        try (CSVReader csvReader = new CSVReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String[] headers = csvReader.readNext();
            if (headers == null) {
                logger.error("CSV file is empty. transactionId={}", transactionId);
                addError(errors, 1, "CSV file is empty.");
                return transactions;
            }
            // Handle BOM (Byte Order Mark)
            if (headers.length > 0 && headers[0] != null && headers[0].length() > 0 && headers[0].charAt(0) == '\uFEFF') {
                logger.info("Detected UTF-8 BOM in CSV header, removing... transactionId={}", transactionId);
                headers[0] = headers[0].substring(1);
            }
            // Map header indices
            for (int i = 0; i < headers.length; i++) {
                headerMap.put(headers[i].trim().toLowerCase(), i);
            }
            logger.debug("Mapped CSV columns: {}. transactionId={}", headerMap, transactionId);

            // Validate that all required logical fields are present
            for (String logical : LOGICAL_HEADERS.keySet()) {
                Integer idx = getHeaderIndex(headerMap, LOGICAL_HEADERS.get(logical));
                if (idx == null) {
                    logger.error("CSV missing required column for '{}'. transactionId={}, tried={}", logical, transactionId, Arrays.toString(LOGICAL_HEADERS.get(logical)));
                    addError(errors, 1, "Missing required column for: " + logical);
                    return transactions;
                }
            }

            String[] fields;
            int lineNum = 2;
            while ((fields = csvReader.readNext()) != null) {
                try {
                    BudgetTransaction tx = new BudgetTransaction();
                    tx.setName(getField(fields, headerMap, LOGICAL_HEADERS.get("name")));
                    tx.setAmount(parseAmount(getField(fields, headerMap, LOGICAL_HEADERS.get("amount")), lineNum, errors));
                    tx.setCategory(getField(fields, headerMap, LOGICAL_HEADERS.get("category")));
                    tx.setCriticality(getField(fields, headerMap, LOGICAL_HEADERS.get("criticality")));
                    tx.setTransactionDate(parseDate(getField(fields, headerMap, LOGICAL_HEADERS.get("transaction date")), lineNum, errors));
                    tx.setAccount(getField(fields, headerMap, LOGICAL_HEADERS.get("account")));
                    tx.setStatus(getField(fields, headerMap, LOGICAL_HEADERS.get("status")));
                    tx.setCreatedTime(parseDateTime(getField(fields, headerMap, LOGICAL_HEADERS.get("created time")), lineNum, errors));
                    tx.setPaymentMethod(getField(fields, headerMap, LOGICAL_HEADERS.get("payment method")));
                    // statementPeriod is set by endpoint param after parsing
                    transactions.add(tx);
                    logger.debug("Parsed transaction at line {}: {}. transactionId={}", lineNum, tx, transactionId);
                } catch (Exception ex) {
                    logger.error("Failed to parse line {}. Error: {}. transactionId={}", lineNum, ex.getMessage(), transactionId, ex);
                    addError(errors, lineNum, ex.getMessage());
                    transactions.add(null);
                }
                lineNum++;
            }
            logger.info("parseCsvToTransactions completed. transactionId={}, parsedCount={}, errorCount={}",
                    transactionId, transactions.size(), errors.size());
        } catch (Exception ex) {
            logger.error("Error reading/parsing CSV file. transactionId={}, error={}", transactionId, ex.getMessage(), ex);
            addError(errors, 1, "Failed to parse CSV: " + ex.getMessage());
        }
        return transactions;
    }

    /** Helper: Add an error to the error list. */
    private void addError(List<Map<String, Object>> errors, int row, String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("row", row);
        error.put("message", message);
        errors.add(error);
    }

    /** Maps logical header keys to CSV indices, case-insensitive. */
    private static Integer getHeaderIndex(Map<String, Integer> headerMap, String[] logicalNames) {
        for (String name : logicalNames) {
            Integer idx = headerMap.get(name.trim().toLowerCase());
            if (idx != null) return idx;
        }
        return null;
    }

    /** Gets a field by logical header key(s) from a CSV row. */
    private static String getField(String[] row, Map<String, Integer> headerMap, String[] logicalNames) {
        Integer idx = getHeaderIndex(headerMap, logicalNames);
        if (idx == null || idx >= row.length) return "";
        return row[idx] == null ? "" : row[idx].trim();
    }

    /** Robustly parse and validate amount. Adds error on failure. */
    private static BigDecimal parseAmount(String raw, int rowNum, List<Map<String, Object>> errors) {
        if (raw == null || raw.trim().isEmpty()) return BigDecimal.ZERO;
        String cleaned = raw.replace("$", "").replace(",", "").trim();
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("row", rowNum);
            error.put("field", "amount");
            error.put("value", raw);
            error.put("message", "Invalid amount format: '" + raw + "'");
            errors.add(error);
            return BigDecimal.ZERO;
        }
    }

    /** Robustly parse and validate LocalDate. Adds error on failure. */
    private static LocalDate parseDate(String raw, int rowNum, List<Map<String, Object>> errors) {
        if (raw == null || raw.trim().isEmpty()) return null;
        String s = raw.trim();
        List<String> patterns = Arrays.asList("yyyy-MM-dd", "M/d/yyyy", "MMM d, yyyy", "MMMM d, yyyy");
        for (String pattern : patterns) {
            try {
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern(pattern, Locale.US);
                LocalDate date = LocalDate.parse(s, fmt);
                logger.debug("Parsed LocalDate '{}' using pattern '{}'", s, pattern);
                return date;
            } catch (DateTimeParseException ignored) { }
        }
        // Try ISO as fallback
        try {
            LocalDate date = LocalDate.parse(s);
            logger.debug("Parsed LocalDate '{}' using ISO", s);
            return date;
        } catch (Exception ignored) {}
        Map<String, Object> error = new HashMap<>();
        error.put("row", rowNum);
        error.put("field", "transaction_date");
        error.put("value", raw);
        error.put("message", "Invalid date format: '" + raw + "'");
        errors.add(error);
        return null;
    }

    /** Robustly parse and validate LocalDateTime. Adds error on failure. Supports 'October 15, 2025 9:28 AM' style. */
    private static LocalDateTime parseDateTime(String raw, int rowNum, List<Map<String, Object>> errors) {
        if (raw == null || raw.trim().isEmpty()) return null;
        String s = raw.trim();
        List<String> patterns = Arrays.asList(
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm:ss.SSS",
                "yyyy-MM-dd HH:mm:ss.SSS",
                "M/d/yyyy H:mm",
                "MMM d, yyyy H:mm",
                "MMMM d, yyyy h:mm a" // Added: October 15, 2025 9:28 AM
        );
        for (String pattern : patterns) {
            try {
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern(pattern, Locale.US);
                LocalDateTime dateTime = LocalDateTime.parse(s, fmt);
                logger.debug("Parsed LocalDateTime '{}' using pattern '{}'", s, pattern);
                return dateTime;
            } catch (DateTimeParseException ignored) { }
        }
        // Try ISO as fallback
        try {
            LocalDateTime dateTime = LocalDateTime.parse(s);
            logger.debug("Parsed LocalDateTime '{}' using ISO", s);
            return dateTime;
        } catch (Exception ignored) {}
        Map<String, Object> error = new HashMap<>();
        error.put("row", rowNum);
        error.put("field", "created_time");
        error.put("value", raw);
        error.put("message", "Invalid datetime format: '" + raw + "'");
        errors.add(error);
        return null;
    }
}