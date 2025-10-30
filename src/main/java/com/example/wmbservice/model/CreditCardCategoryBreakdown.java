package com.example.wmbservice.model;

import java.math.BigDecimal;
import java.util.Map;


public class CreditCardCategoryBreakdown {
    private String card;
    private Map<String, BigDecimal> categoryTotals;

    public CreditCardCategoryBreakdown(String card, Map<String, BigDecimal> categoryTotals) {
        this.card = card;
        this.categoryTotals = categoryTotals;
    }

    public String getCard() {
        return card;
    }

    public Map<String, BigDecimal> getCategoryTotals() {
        return categoryTotals;
    }
}

