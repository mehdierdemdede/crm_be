package com.leadsyncpro.billing.facade;

import com.leadsyncpro.audit.PiiAccess;
import java.util.List;
import java.util.UUID;

public interface SubscriptionFacade {

    SubscriptionDto createSubscription(CreateSubscriptionCmd cmd);

    SubscriptionDto changePlan(UUID subscriptionId, ChangePlanCmd cmd);

    SubscriptionDto updateSeats(UUID subscriptionId, int seatCount, Proration proration);

    void cancel(UUID subscriptionId, boolean cancelAtPeriodEnd);

    @PiiAccess("subscription:read")
    SubscriptionDto getSubscription(UUID subscriptionId);

    @PiiAccess("customer:subscriptions:list")
    List<SubscriptionDto> getSubscriptionsByCustomer(UUID customerId);

    @PiiAccess("customer:invoices:list")
    List<InvoiceDto> getInvoicesByCustomer(UUID customerId);
}
