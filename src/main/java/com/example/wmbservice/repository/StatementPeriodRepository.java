package com.example.wmbservice.repository;

import com.example.wmbservice.model.StatementPeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for StatementPeriod CRUD operations.
 */
@Repository
public interface StatementPeriodRepository extends JpaRepository<StatementPeriod, Long> {

    /**
     * Find by periodName for uniqueness checks.
     */
    Optional<StatementPeriod> findByPeriodName(String periodName);

    /**
     * Get all statement periods ordered by start_date desc (most recent first).
     */
    @Query("SELECT s FROM StatementPeriod s ORDER BY s.startDate DESC NULLS LAST, s.endDate DESC NULLS LAST")
    List<StatementPeriod> findAllOrderByStartDateDesc();
}