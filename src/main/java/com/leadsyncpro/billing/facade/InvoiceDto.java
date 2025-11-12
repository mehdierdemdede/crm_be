package com.leadsyncpro.billing.facade;

import com.leadsyncpro.model.billing.InvoiceStatus;
import java.time.Instant;
import java.util.UUID;

public record InvoiceDto(
        UUID id,
        UUID subscriptionId,
        Instant periodStart,
        Instant periodEnd,
        Long totalCents,
        String currency,
        InvoiceStatus status) {}
