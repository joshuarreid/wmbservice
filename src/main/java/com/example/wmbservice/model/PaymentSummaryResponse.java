package com.example.wmbservice.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
public class PaymentSummaryResponse {
    private String account;
    private Map<String, BigDecimal> creditCardTotals; // paymentMethod -> total owed
    private Map<String, Map<String, BigDecimal>> creditCardCategoryBreakdowns; // paymentMethod -> category -> total

}
