package com.leadsyncpro.billing.api;

import com.leadsyncpro.model.billing.BillingPeriod;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(name = "PlanPriceResponse")
public record PlanPriceResponse(
        @Schema(description = "Unique identifier of the price option", example = "8c1d2e3f-4a5b-4c6d-8e9f-0a1b2c3d4e50") UUID id,
        @Schema(description = "Billing period for the price", example = "MONTH") BillingPeriod billingPeriod,
        @Schema(description = "Base amount in cents billed per period", example = "9900") Long baseAmountCents,
        @Schema(description = "Per-seat amount in cents billed per period", example = "1500") Long perSeatAmountCents,
        @Schema(description = "Currency code for the price", example = "TRY") String currency) {}
