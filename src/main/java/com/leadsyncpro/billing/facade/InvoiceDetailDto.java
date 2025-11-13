package com.leadsyncpro.billing.facade;

import com.leadsyncpro.model.billing.InvoiceStatus;
import java.time.Instant;
import java.util.UUID;

public record InvoiceDetailDto(
        UUID id,
        UUID subscriptionId,
        String externalInvoiceId,
        Instant periodStart,
        Instant periodEnd,
        Long subtotalCents,
        Long taxCents,
        Long totalCents,
        String currency,
        InvoiceStatus status,
        Instant createdAt,
        Instant updatedAt) {}
