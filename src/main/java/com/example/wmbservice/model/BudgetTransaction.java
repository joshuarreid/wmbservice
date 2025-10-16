package com.example.wmbservice.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Entity representing an actual budget transaction, mapped to the 'budget_transactions' table.
 * Includes rowHash for deduplication enforcement.
 */
@Entity
@Table(
        name = "budget_transactions",
        indexes = {
                @Index(name = "idx_statement_period", columnList = "statement_period"),
                @Index(name = "idx_account", columnList = "account"),
                @Index(name = "idx_payment_method", columnList = "payment_method"),
                @Index(name = "idx_category", columnList = "category"),
                @Index(name = "uniq_transaction_hash", columnList = "row_hash, statement_period", unique = true)
        }
)
public class BudgetTransaction {

    private static final Logger logger = LoggerFactory.getLogger(BudgetTransaction.class);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false)
    private String name;

    @NotNull
    @DecimalMin(value = "0.00", inclusive = false)
    @Digits(integer = 12, fraction = 2)
    @Column(nullable = false)
    private BigDecimal amount;

    @NotBlank
    @Size(max = 128)
    @Column(nullable = false)
    private String category;

    @NotBlank
    @Size(max = 32)
    @Column(nullable = false)
    private String criticality;

    @NotNull
    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @NotBlank
    @Size(max = 32)
    @Column(nullable = false)
    private String account;

    @Size(max = 64)
    private String status;

    private LocalDateTime createdTime;

    @NotBlank
    @Size(max = 64)
    @Column(name = "payment_method", nullable = false)
    private String paymentMethod;

    @NotBlank
    @Size(max = 32)
    @Column(name = "statement_period", nullable = false)
    private String statementPeriod;

    @Size(max = 64)
    @Column(name = "row_hash", length = 64, unique = true)
    private String rowHash;

    /**
     * Default constructor.
     * Logs entity creation.
     */
    public BudgetTransaction() {
        logger.info("BudgetTransaction instantiated via default constructor");
    }

    /**
     * All-args constructor for entity instantiation.
     */
    public BudgetTransaction(String name, BigDecimal amount, String category, String criticality,
                             LocalDate transactionDate, String account, String status, LocalDateTime createdTime,
                             String paymentMethod, String statementPeriod, String rowHash) {
        logger.info("BudgetTransaction instantiated with name={}, amount={}, statementPeriod={}", name, amount, statementPeriod);
        this.name = name;
        this.amount = amount;
        this.category = category;
        this.criticality = criticality;
        this.transactionDate = transactionDate;
        this.account = account;
        this.status = status;
        this.createdTime = createdTime;
        this.paymentMethod = paymentMethod;
        this.statementPeriod = statementPeriod;
        this.rowHash = rowHash;
    }

    // Getters and setters with logging for all field changes

    public Long getId() {
        logger.debug("getId called, returning {}", id);
        return id;
    }

    public void setId(Long id) {
        logger.debug("setId called with {}", id);
        this.id = id;
    }

    public String getName() {
        logger.debug("getName called, returning {}", name);
        return name;
    }

    public void setName(String name) {
        logger.debug("setName called with {}", name);
        this.name = name;
    }

    public BigDecimal getAmount() {
        logger.debug("getAmount called, returning {}", amount);
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        logger.debug("setAmount called with {}", amount);
        this.amount = amount;
    }

    public String getCategory() {
        logger.debug("getCategory called, returning {}", category);
        return category;
    }

    public void setCategory(String category) {
        logger.debug("setCategory called with {}", category);
        this.category = category;
    }

    public String getCriticality() {
        logger.debug("getCriticality called, returning {}", criticality);
        return criticality;
    }

    public void setCriticality(String criticality) {
        logger.debug("setCriticality called with {}", criticality);
        this.criticality = criticality;
    }

    public LocalDate getTransactionDate() {
        logger.debug("getTransactionDate called, returning {}", transactionDate);
        return transactionDate;
    }

    public void setTransactionDate(LocalDate transactionDate) {
        logger.debug("setTransactionDate called with {}", transactionDate);
        this.transactionDate = transactionDate;
    }

    public String getAccount() {
        logger.debug("getAccount called, returning {}", account);
        return account;
    }

    public void setAccount(String account) {
        logger.debug("setAccount called with {}", account);
        this.account = account;
    }

    public String getStatus() {
        logger.debug("getStatus called, returning {}", status);
        return status;
    }

    public void setStatus(String status) {
        logger.debug("setStatus called with {}", status);
        this.status = status;
    }

    public LocalDateTime getCreatedTime() {
        logger.debug("getCreatedTime called, returning {}", createdTime);
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        logger.debug("setCreatedTime called with {}", createdTime);
        this.createdTime = createdTime;
    }

    public String getPaymentMethod() {
        logger.debug("getPaymentMethod called, returning {}", paymentMethod);
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        logger.debug("setPaymentMethod called with {}", paymentMethod);
        this.paymentMethod = paymentMethod;
    }

    public String getStatementPeriod() {
        logger.debug("getStatementPeriod called, returning {}", statementPeriod);
        return statementPeriod;
    }

    public void setStatementPeriod(String statementPeriod) {
        logger.debug("setStatementPeriod called with {}", statementPeriod);
        this.statementPeriod = statementPeriod;
    }

    public String getRowHash() {
        logger.debug("getRowHash called, returning {}", rowHash);
        return rowHash;
    }

    public void setRowHash(String rowHash) {
        logger.debug("setRowHash called with {}", rowHash);
        this.rowHash = rowHash;
    }

    @Override
    public boolean equals(Object o) {
        logger.debug("equals called");
        if (this == o) return true;
        if (!(o instanceof BudgetTransaction that)) return false;
        return Objects.equals(rowHash, that.rowHash) &&
                Objects.equals(statementPeriod, that.statementPeriod);
    }

    @Override
    public int hashCode() {
        logger.debug("hashCode called");
        return Objects.hash(rowHash, statementPeriod);
    }

    @Override
    public String toString() {
        logger.debug("toString called");
        return "BudgetTransaction{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", amount=" + amount +
                ", category='" + category + '\'' +
                ", criticality='" + criticality + '\'' +
                ", transactionDate=" + transactionDate +
                ", account='" + account + '\'' +
                ", status='" + status + '\'' +
                ", createdTime=" + createdTime +
                ", paymentMethod='" + paymentMethod + '\'' +
                ", statementPeriod='" + statementPeriod + '\'' +
                ", rowHash='" + rowHash + '\'' +
                '}';
    }
}