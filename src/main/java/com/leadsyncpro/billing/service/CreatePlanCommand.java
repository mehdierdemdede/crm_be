package com.leadsyncpro.billing.service;

import com.leadsyncpro.model.billing.BillingPeriod;
import java.math.BigDecimal;
import java.util.List;

public record CreatePlanCommand(
        String code,
        String name,
        String description,
        List<String> features,
        PlanMetadata metadata,
        List<PlanPriceDefinition> prices) {

    public record PlanMetadata(
            BigDecimal basePrice,
            BigDecimal perSeatPrice,
            BigDecimal basePriceMonth,
            BigDecimal perSeatPriceMonth,
            BigDecimal basePriceYear,
            BigDecimal perSeatPriceYear) {}

    public record PlanPriceDefinition(
            String clientPriceId,
            BigDecimal amount,
            String currency,
            BillingPeriod billingPeriod,
            Integer seatLimit,
            Integer trialDays) {}
}
