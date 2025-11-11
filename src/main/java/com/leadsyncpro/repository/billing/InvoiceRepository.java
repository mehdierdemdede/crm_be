package com.leadsyncpro.repository.billing;

import com.leadsyncpro.model.billing.Invoice;
import com.leadsyncpro.model.billing.Subscription;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    List<Invoice> findBySubscriptionOrderByPeriodStartDesc(Subscription subscription);

    List<Invoice> findByPeriodStartGreaterThanEqualAndPeriodEndLessThanEqual(Instant periodStart, Instant periodEnd);

    Optional<Invoice> findByExternalInvoiceId(String externalInvoiceId);
}
