package com.example.wmbservice.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Entity representing a statement period (e.g., credit card statement window).
 * Maps to the 'statement_periods' table.
 */
@Getter
@Setter
@Entity
@Table(
        name = "statement_periods",
        indexes = {
                @Index(name = "idx_period_name", columnList = "period_name")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uniq_period_name", columnNames = {"period_name"})
        }
)
public class StatementPeriod {

    private static final Logger logger = LoggerFactory.getLogger(StatementPeriod.class);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 32)
    @Column(name = "period_name", nullable = false, unique = true, length = 32)
    private String periodName;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /**
     * Default constructor.
     */
    public StatementPeriod() {
        logger.debug("StatementPeriod default constructor called");
    }

    /**
     * All-args constructor.
     */
    public StatementPeriod(String periodName, LocalDate startDate, LocalDate endDate, LocalDateTime createdAt) {
        logger.info("StatementPeriod instantiated with periodName={}, startDate={}, endDate={}", periodName, startDate, endDate);
        this.periodName = periodName;
        this.startDate = startDate;
        this.endDate = endDate;
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        logger.debug("equals called for StatementPeriod");
        if (this == o) return true;
        if (!(o instanceof StatementPeriod that)) return false;
        return Objects.equals(periodName, that.periodName);
    }

    @Override
    public int hashCode() {
        logger.debug("hashCode called for StatementPeriod");
        return Objects.hash(periodName);
    }

    @Override
    public String toString() {
        logger.debug("toString called for StatementPeriod");
        return "StatementPeriod{" +
                "id=" + id +
                ", periodName='" + periodName + '\'' +
                ", startDate=" + startDate +
                ", endDate=" + endDate +
                ", createdAt=" + createdAt +
                '}';
    }
}