package com.leadsyncpro.dto;

import com.leadsyncpro.model.billing.BillingPeriod;
import java.math.BigDecimal;
import java.util.UUID;

public record PlanSummaryResponse(
        UUID planId,
        String planCode,
        String name,
        String description,
        BillingPeriod billingPeriod,
        BigDecimal basePrice,
        BigDecimal pricePerSeat,
        Integer seatCount,
        BigDecimal totalPrice,
        Integer trialDays,
        Integer seatLimit,
        String currency) {}
