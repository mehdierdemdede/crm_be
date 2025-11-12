package com.leadsyncpro.billing.worker;

import com.leadsyncpro.model.billing.Subscription;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LoggingBillingNotificationAdapter implements BillingNotificationPort {

    @Override
    public void notifyPaymentRetrySuccess(Subscription subscription, int attemptNumber) {
        log.info(
                "Subscription {} payment retry attempt {} succeeded",
                subscription.getId(),
                attemptNumber);
    }

    @Override
    public void notifyPaymentRetryFailure(Subscription subscription, int attemptNumber) {
        log.warn(
                "Subscription {} payment retry attempt {} failed",
                subscription.getId(),
                attemptNumber);
    }

    @Override
    public void notifySubscriptionCanceled(Subscription subscription) {
        log.warn("Subscription {} canceled after dunning attempts", subscription.getId());
    }
}
