package com.leadsyncpro.billing.api;

import com.leadsyncpro.model.billing.InvoiceStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(name = "InvoiceDetailResponse")
public record InvoiceDetailResponse(
        @Schema(description = "Unique identifier of the invoice", example = "7b8c9d0e-1f2a-4b3c-5d6e-7f8091a2b3c4") UUID id,
        @Schema(description = "Identifier of the related subscription", example = "5f6a7b8c-9d0e-4f1a-2b3c-4d5e6f708192") UUID subscriptionId,
        @Schema(description = "Payment gateway invoice reference", example = "inv_demo_001") String externalInvoiceId,
        @Schema(description = "Start of the billing period", example = "2024-01-01T00:00:00Z") Instant periodStart,
        @Schema(description = "End of the billing period", example = "2024-01-31T23:59:59Z") Instant periodEnd,
        @Schema(description = "Subtotal amount in cents", example = "99000") Long subtotalCents,
        @Schema(description = "Tax amount in cents", example = "18810") Long taxCents,
        @Schema(description = "Total amount in cents", example = "117810") Long totalCents,
        @Schema(description = "Currency code", example = "TRY") String currency,
        @Schema(description = "Invoice status", example = "PAID") InvoiceStatus status,
        @Schema(description = "Creation timestamp", example = "2024-01-31T23:59:59Z") Instant createdAt,
        @Schema(description = "Last update timestamp", example = "2024-01-31T23:59:59Z") Instant updatedAt) {}
