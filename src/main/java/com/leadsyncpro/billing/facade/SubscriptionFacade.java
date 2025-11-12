package com.leadsyncpro.billing.facade;

import java.util.List;
import java.util.UUID;

public interface SubscriptionFacade {

    SubscriptionDto createSubscription(CreateSubscriptionCmd cmd);

    SubscriptionDto changePlan(UUID subscriptionId, ChangePlanCmd cmd);

    SubscriptionDto updateSeats(UUID subscriptionId, int seatCount, Proration proration);

    void cancel(UUID subscriptionId, boolean cancelAtPeriodEnd);

    SubscriptionDto getSubscription(UUID subscriptionId);

    List<SubscriptionDto> getSubscriptionsByCustomer(UUID customerId);

    List<InvoiceDto> getInvoicesByCustomer(UUID customerId);
}
