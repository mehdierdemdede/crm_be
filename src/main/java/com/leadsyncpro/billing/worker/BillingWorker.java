package com.leadsyncpro.billing.worker;

import com.leadsyncpro.billing.integration.iyzico.IyzicoClient;
import com.leadsyncpro.billing.integration.iyzico.IyzicoInvoiceResponse;
import com.leadsyncpro.billing.metrics.SubscriptionStatusMetrics;
import com.leadsyncpro.billing.service.InvoicingService;
import com.leadsyncpro.billing.service.SubscriptionService;
import com.leadsyncpro.model.billing.Price;
import com.leadsyncpro.model.billing.SeatAllocation;
import com.leadsyncpro.model.billing.Subscription;
import com.leadsyncpro.model.billing.SubscriptionStatus;
import com.leadsyncpro.repository.billing.SubscriptionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Component
public class BillingWorker {

    private static final List<Long> RETRY_SCHEDULE_DAYS = List.of(1L, 3L, 5L);

    private final SubscriptionRepository subscriptionRepository;
    private final InvoicingService invoicingService;
    private final IyzicoClient iyzicoClient;
    private final SubscriptionService subscriptionService;
    private final BillingNotificationPort notificationPort;
    private final Clock clock;
    private final SubscriptionStatusMetrics subscriptionStatusMetrics;

    public BillingWorker(
            SubscriptionRepository subscriptionRepository,
            InvoicingService invoicingService,
            IyzicoClient iyzicoClient,
            SubscriptionService subscriptionService,
            BillingNotificationPort notificationPort,
            Clock clock,
            SubscriptionStatusMetrics subscriptionStatusMetrics) {
        this.subscriptionRepository = Objects.requireNonNull(subscriptionRepository, "subscriptionRepository");
        this.invoicingService = Objects.requireNonNull(invoicingService, "invoicingService");
        this.iyzicoClient = Objects.requireNonNull(iyzicoClient, "iyzicoClient");
        this.subscriptionService = Objects.requireNonNull(subscriptionService, "subscriptionService");
        this.notificationPort = Objects.requireNonNull(notificationPort, "notificationPort");
        this.clock = clock == null ? Clock.system(ZoneOffset.UTC) : clock;
        this.subscriptionStatusMetrics =
                Objects.requireNonNull(subscriptionStatusMetrics, "subscriptionStatusMetrics");
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void runDunningCycle() {
        Instant now = Instant.now(clock);
        activateExpiredTrials(now);
        processPastDueSubscriptions(now);
    }

    private void activateExpiredTrials(Instant now) {
        List<Subscription> trialSubscriptions = subscriptionRepository.findByStatus(SubscriptionStatus.TRIAL);
        if (CollectionUtils.isEmpty(trialSubscriptions)) {
            return;
        }
        for (Subscription subscription : trialSubscriptions) {
            Instant trialEnd = subscription.getTrialEndAt();
            if (trialEnd != null && !trialEnd.isAfter(now)) {
                subscriptionService.activateTrial(subscription, trialEnd);
                subscriptionRepository.save(subscription);
            }
        }
    }

    private void processPastDueSubscriptions(Instant now) {
        List<Subscription> pastDueSubscriptions = subscriptionRepository.findByStatus(SubscriptionStatus.PAST_DUE);
        if (CollectionUtils.isEmpty(pastDueSubscriptions)) {
            return;
        }
        for (Subscription subscription : pastDueSubscriptions) {
            if (subscription.getUpdatedAt() == null) {
                continue;
            }
            long daysPastDue = ChronoUnit.DAYS.between(subscription.getUpdatedAt(), now);
            if (daysPastDue > RETRY_SCHEDULE_DAYS.get(RETRY_SCHEDULE_DAYS.size() - 1)) {
                cancelSubscription(subscription, now);
                continue;
            }
            int attemptIndex = findAttemptIndex(daysPastDue);
            if (attemptIndex < 0) {
                continue;
            }
            attemptRecovery(subscription, attemptIndex, now);
        }
    }

    private int findAttemptIndex(long daysPastDue) {
        for (int i = 0; i < RETRY_SCHEDULE_DAYS.size(); i++) {
            if (daysPastDue == RETRY_SCHEDULE_DAYS.get(i)) {
                return i;
            }
        }
        return -1;
    }

    private void attemptRecovery(Subscription subscription, int attemptIndex, Instant now) {
        int attemptNumber = attemptIndex + 1;
        try {
            InvoicingService.InvoiceContext context = buildInvoiceContext(subscription, now);
            if (context.amountCents() <= 0) {
                markSubscriptionActive(subscription, context);
                notificationPort.notifyPaymentRetrySuccess(subscription, attemptNumber);
                return;
            }
            String externalId = subscription.getExternalSubscriptionId();
            if (!StringUtils.hasText(externalId)) {
                throw new IllegalStateException("Subscription is missing an external reference");
            }
            IyzicoInvoiceResponse response = iyzicoClient.createInvoice(
                    externalId,
                    context.periodStart(),
                    context.periodEnd(),
                    context.amountCents(),
                    subscription.getPrice().getCurrency());
            if (response != null) {
                markSubscriptionActive(subscription, context);
                notificationPort.notifyPaymentRetrySuccess(subscription, attemptNumber);
            }
        } catch (Exception ex) {
            handleRetryFailure(subscription, attemptIndex, now);
        }
    }

    private void markSubscriptionActive(Subscription subscription, InvoicingService.InvoiceContext context) {
        subscriptionStatusMetrics.updateStatus(subscription, SubscriptionStatus.ACTIVE);
        subscription.setCancelAtPeriodEnd(false);
        subscription.setCurrentPeriodStart(context.periodStart());
        subscription.setCurrentPeriodEnd(context.periodEnd());
        subscriptionRepository.save(subscription);
    }

    private void handleRetryFailure(Subscription subscription, int attemptIndex, Instant now) {
        int attemptNumber = attemptIndex + 1;
        boolean finalAttempt = attemptIndex == RETRY_SCHEDULE_DAYS.size() - 1;
        if (finalAttempt) {
            cancelSubscription(subscription, now);
            notificationPort.notifySubscriptionCanceled(subscription);
        } else {
            notificationPort.notifyPaymentRetryFailure(subscription, attemptNumber);
        }
    }

    private void cancelSubscription(Subscription subscription, Instant cancellationTime) {
        subscriptionService.cancel(subscription, cancellationTime);
        subscriptionRepository.save(subscription);
    }

    private InvoicingService.InvoiceContext buildInvoiceContext(Subscription subscription, Instant now) {
        Price price = subscription.getPrice();
        int seatCount = determineSeatCount(subscription);
        Instant periodStart = subscription.getCurrentPeriodStart();
        if (periodStart == null) {
            periodStart = subscription.getStartAt() != null ? subscription.getStartAt() : now;
        }
        Instant periodEnd = subscription.getCurrentPeriodEnd();
        if (periodEnd == null || !periodEnd.isAfter(periodStart)) {
            periodEnd = periodStart.plus(1, ChronoUnit.DAYS);
        }
        return invoicingService.generateInvoiceContext(price, seatCount, periodStart, periodEnd);
    }

    private int determineSeatCount(Subscription subscription) {
        List<SeatAllocation> allocations = subscription.getSeatAllocations();
        if (allocations == null || allocations.isEmpty()) {
            return 0;
        }
        return allocations.stream()
                .max(Comparator.comparing(SeatAllocation::getEffectiveFrom))
                .map(SeatAllocation::getSeatCount)
                .orElse(0);
    }
}
