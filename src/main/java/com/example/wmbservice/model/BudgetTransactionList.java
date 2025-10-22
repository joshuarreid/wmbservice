package com.example.wmbservice.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Getter
@Setter
public class BudgetTransactionList {
   private List<BudgetTransaction> transactions;
   private int count;

   @DecimalMin(value = "0.00", inclusive = false)
   @Digits(integer = 12, fraction = 2)
   private BigDecimal total;

   public BudgetTransactionList(List<BudgetTransaction> transactions) {
       if (transactions == null) {
           this.transactions = Collections.emptyList();
           this.count = 0;
           this.total = BigDecimal.ZERO;
       } else {
           this.transactions = List.copyOf(transactions);
           this.count = transactions.size();
           this.total = transactions.stream()
                   .filter(Objects::nonNull)
                   .map(t -> t.getAmount() != null ? t.getAmount() : BigDecimal.ZERO)
                   .reduce(BigDecimal.ZERO, BigDecimal::add);
       }
   }

   public BudgetTransactionList() {
       this(Collections.emptyList());
   }
}