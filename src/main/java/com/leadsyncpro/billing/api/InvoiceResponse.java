package com.leadsyncpro.billing.api;

import com.leadsyncpro.model.billing.InvoiceStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(name = "InvoiceResponse")
public record InvoiceResponse(
        @Schema(example = "aa1d6fb2-0c91-4a9e-8a87-d1c1e66d1e90") UUID id,
        @Schema(example = "5bb1d1dc-7f8a-4f1d-9c4e-0f5e3b2a1c4d") UUID subscriptionId,
        @Schema(example = "2024-01-01T00:00:00Z") Instant periodStart,
        @Schema(example = "2024-01-31T23:59:59Z") Instant periodEnd,
        @Schema(example = "129900") Long totalCents,
        @Schema(example = "USD") String currency,
        @Schema(example = "PAID") InvoiceStatus status) {}
