package com.example.wmbservice.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class AccountBudgetTransactionList  {
    private BudgetTransactionList personalTransactions;
    private BudgetTransactionList jointTransactions;

    @DecimalMin(value = "0.00", inclusive = false)
    @Digits(integer = 12, fraction = 2)
    private BigDecimal personalTotal;

    @DecimalMin(value = "0.00", inclusive = false)
    @Digits(integer = 12, fraction = 2)
    private BigDecimal jointTotal;

    @DecimalMin(value = "0.00", inclusive = false)
    @Digits(integer = 12, fraction = 2)
    private BigDecimal total;

    public AccountBudgetTransactionList(BudgetTransactionList personalTransactions, BudgetTransactionList jointTransactions) {
        this.personalTransactions = personalTransactions == null ? new BudgetTransactionList() : personalTransactions;
        this.jointTransactions = jointTransactions == null ? new BudgetTransactionList() : jointTransactions;
        this.personalTotal = safeTotal(this.personalTransactions);
        this.jointTotal = safeTotal(this.jointTransactions);
        this.total = safeSum(this.personalTotal, this.jointTotal);
    }

    public AccountBudgetTransactionList() {
        this(new BudgetTransactionList(), new BudgetTransactionList());
    }

    private static BigDecimal safeTotal(BudgetTransactionList list) {
        if (list == null) return BigDecimal.ZERO;
        BigDecimal t = list.getTotal();
        return t == null ? BigDecimal.ZERO : t;
    }

    private static BigDecimal safeSum(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) return BigDecimal.ZERO;
        if (a == null) return b;
        if (b == null) return a;
        return a.add(b);
    }
}
