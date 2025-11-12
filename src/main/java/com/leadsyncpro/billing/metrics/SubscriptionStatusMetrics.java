package com.leadsyncpro.billing.metrics;

import com.leadsyncpro.model.billing.Subscription;
import com.leadsyncpro.model.billing.SubscriptionStatus;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionStatusMetrics {

    private final MeterRegistry meterRegistry;

    public SubscriptionStatusMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void updateStatus(Subscription subscription, SubscriptionStatus newStatus) {
        SubscriptionStatus current = subscription.getStatus();
        if (Objects.equals(current, newStatus)) {
            return;
        }
        meterRegistry
                .counter(
                        "subscription_state_change_total",
                        "from", current != null ? current.name() : "UNSET",
                        "to", newStatus != null ? newStatus.name() : "UNKNOWN")
                .increment();
        subscription.setStatus(newStatus);
    }
}
