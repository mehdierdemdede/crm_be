package com.leadsyncpro.billing.api;

import com.leadsyncpro.model.billing.BillingPeriod;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(name = "PlanPriceResponse")
public record PlanPriceResponse(
        @Schema(description = "Unique identifier of the price option", example = "8c1d2e3f-4a5b-4c6d-8e9f-0a1b2c3d4e50") UUID id,
        @Schema(description = "Amount billed per seat for the period", example = "120") BigDecimal amount,
        @Schema(description = "Currency code for the price", example = "TRY") String currency,
        @Schema(description = "Billing period for the price", example = "MONTH") BillingPeriod billingPeriod,
        @Schema(description = "Maximum number of seats supported", example = "50") Integer seatLimit,
        @Schema(description = "Trial duration in days", example = "14") Integer trialDays) {}
