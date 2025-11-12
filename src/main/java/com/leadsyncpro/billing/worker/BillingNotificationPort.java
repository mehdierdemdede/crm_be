package com.leadsyncpro.billing.worker;

import com.leadsyncpro.model.billing.Subscription;

public interface BillingNotificationPort {

    void notifyPaymentRetrySuccess(Subscription subscription, int attemptNumber);

    void notifyPaymentRetryFailure(Subscription subscription, int attemptNumber);

    void notifySubscriptionCanceled(Subscription subscription);
}
