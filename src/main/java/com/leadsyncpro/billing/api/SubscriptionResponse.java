package com.leadsyncpro.billing.api;

import com.leadsyncpro.model.billing.BillingPeriod;
import com.leadsyncpro.model.billing.SubscriptionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(name = "SubscriptionResponse")
public record SubscriptionResponse(
        @Schema(example = "5bb1d1dc-7f8a-4f1d-9c4e-0f5e3b2a1c4d") UUID id,
        @Schema(example = "8c55f5a2-3c80-4aa5-8d3b-1c8b5d9f2c0a") UUID customerId,
        @Schema(example = "PRO") String planCode,
        @Schema(example = "MONTH") BillingPeriod billingPeriod,
        @Schema(example = "ACTIVE") SubscriptionStatus status,
        @Schema(example = "2024-01-01T00:00:00Z") Instant startAt,
        @Schema(example = "2024-01-01T00:00:00Z") Instant currentPeriodStart,
        @Schema(example = "2024-01-31T23:59:59Z") Instant currentPeriodEnd,
        @Schema(example = "2024-01-14T00:00:00Z") Instant trialEndAt,
        @Schema(example = "false") boolean cancelAtPeriodEnd,
        @Schema(example = "10") int seatCount,
        @Schema(example = "USD") String currency,
        @Schema(example = "sub_37f184d2f1") String externalSubscriptionId) {}
