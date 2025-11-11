package com.leadsyncpro.billing.integration.iyzico;

import java.time.Instant;

public record IyzicoInvoiceResponse(
        String invoiceId, Instant periodStart, Instant periodEnd, long amountCents, String currency) {}
