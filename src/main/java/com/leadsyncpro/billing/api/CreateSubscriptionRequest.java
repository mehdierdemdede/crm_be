package com.leadsyncpro.billing.api;

import com.leadsyncpro.model.billing.BillingPeriod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;

@Schema(name = "CreateSubscriptionRequest")
public record CreateSubscriptionRequest(
        @NotNull @Schema(example = "8c55f5a2-3c80-4aa5-8d3b-1c8b5d9f2c0a") UUID customerId,
        @NotBlank @Schema(example = "PRO") String planCode,
        @NotNull @Schema(example = "MONTH") BillingPeriod billingPeriod,
        @NotNull @Min(1) @Schema(example = "10") Integer seatCount,
        @PositiveOrZero @Schema(example = "14") Integer trialDays,
        @Schema(example = "tok_1Nv0a8YgL9s") String cardToken) {}
