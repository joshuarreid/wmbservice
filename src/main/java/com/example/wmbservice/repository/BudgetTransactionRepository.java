package com.example.wmbservice.repository;

import com.example.wmbservice.model.BudgetTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for BudgetTransaction CRUD and queries.
 * All queries can be logged in the service layer.
 */
@Repository
public interface BudgetTransactionRepository extends JpaRepository<BudgetTransaction, Long> {

    /**
     * Find a transaction by row hash and statement period for deduplication.
     */
    @Query("SELECT t FROM BudgetTransaction t WHERE t.rowHash = :rowHash AND t.statementPeriod = :statementPeriod")
    Optional<BudgetTransaction> findByRowHashAndStatementPeriod(@Param("rowHash") String rowHash,
                                                                @Param("statementPeriod") String statementPeriod);

    /**
     * Filter transactions by optional statementPeriod and other optional fields. If a field is null, it is not used as a filter.
     * statementPeriod is now optional.
     */
    @Query("SELECT t FROM BudgetTransaction t WHERE (:statementPeriod IS NULL OR t.statementPeriod = :statementPeriod) " +
            "AND (:account IS NULL OR t.account = :account) " +
            "AND (:category IS NULL OR t.category = :category) " +
            "AND (:criticality IS NULL OR t.criticality = :criticality) " +
            "AND (:paymentMethod IS NULL OR t.paymentMethod = :paymentMethod) " +
            "ORDER BY t.transactionDate DESC")
    List<BudgetTransaction> findByFilters(@Param("statementPeriod") String statementPeriod,
                                          @Param("account") String account,
                                          @Param("category") String category,
                                          @Param("criticality") String criticality,
                                          @Param("paymentMethod") String paymentMethod);



}