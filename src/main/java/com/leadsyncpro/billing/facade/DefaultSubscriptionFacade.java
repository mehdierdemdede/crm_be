package com.leadsyncpro.billing.facade;

import com.leadsyncpro.billing.integration.iyzico.IyzicoClient;
import com.leadsyncpro.billing.integration.iyzico.IyzicoSubscriptionResponse;
import com.leadsyncpro.billing.integration.iyzico.ProrationBehavior;
import com.leadsyncpro.billing.metrics.SubscriptionStatusMetrics;
import com.leadsyncpro.billing.service.SubscriptionService;
import com.leadsyncpro.model.billing.Customer;
import com.leadsyncpro.model.billing.Invoice;
import com.leadsyncpro.model.billing.PaymentMethod;
import com.leadsyncpro.model.billing.Plan;
import com.leadsyncpro.model.billing.Price;
import com.leadsyncpro.model.billing.SeatAllocation;
import com.leadsyncpro.model.billing.Subscription;
import com.leadsyncpro.model.billing.SubscriptionStatus;
import com.leadsyncpro.repository.billing.CustomerRepository;
import com.leadsyncpro.repository.billing.InvoiceRepository;
import com.leadsyncpro.repository.billing.PaymentMethodRepository;
import com.leadsyncpro.repository.billing.PlanRepository;
import com.leadsyncpro.repository.billing.PriceRepository;
import com.leadsyncpro.repository.billing.SeatAllocationRepository;
import com.leadsyncpro.repository.billing.SubscriptionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DefaultSubscriptionFacade implements SubscriptionFacade {

    private final SubscriptionRepository subscriptionRepository;
    private final CustomerRepository customerRepository;
    private final PlanRepository planRepository;
    private final PriceRepository priceRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final SeatAllocationRepository seatAllocationRepository;
    private final InvoiceRepository invoiceRepository;
    private final SubscriptionService subscriptionService;
    private final IyzicoClient iyzicoClient;
    private final SubscriptionStatusMetrics subscriptionStatusMetrics;

    public DefaultSubscriptionFacade(
            SubscriptionRepository subscriptionRepository,
            CustomerRepository customerRepository,
            PlanRepository planRepository,
            PriceRepository priceRepository,
            PaymentMethodRepository paymentMethodRepository,
            SeatAllocationRepository seatAllocationRepository,
            InvoiceRepository invoiceRepository,
            SubscriptionService subscriptionService,
            IyzicoClient iyzicoClient,
            SubscriptionStatusMetrics subscriptionStatusMetrics) {
        this.subscriptionRepository = Objects.requireNonNull(subscriptionRepository, "subscriptionRepository");
        this.customerRepository = Objects.requireNonNull(customerRepository, "customerRepository");
        this.planRepository = Objects.requireNonNull(planRepository, "planRepository");
        this.priceRepository = Objects.requireNonNull(priceRepository, "priceRepository");
        this.paymentMethodRepository =
                Objects.requireNonNull(paymentMethodRepository, "paymentMethodRepository");
        this.seatAllocationRepository =
                Objects.requireNonNull(seatAllocationRepository, "seatAllocationRepository");
        this.invoiceRepository = Objects.requireNonNull(invoiceRepository, "invoiceRepository");
        this.subscriptionService = Objects.requireNonNull(subscriptionService, "subscriptionService");
        this.iyzicoClient = Objects.requireNonNull(iyzicoClient, "iyzicoClient");
        this.subscriptionStatusMetrics =
                Objects.requireNonNull(subscriptionStatusMetrics, "subscriptionStatusMetrics");
    }

    @Override
    @Transactional
    public SubscriptionDto createSubscription(CreateSubscriptionCmd cmd) {
        try {
            Customer customer = loadCustomer(cmd.customerId());
            Plan plan = loadPlan(cmd.planCode());
            Price price = loadPrice(plan, cmd.billingPeriod());

            PaymentMethod paymentMethod = resolvePaymentMethod(customer, cmd.cardToken());

            Instant startAt = Instant.now();
            Instant trialEndAt = cmd.trialDays() != null && cmd.trialDays() > 0
                    ? startAt.plus(Duration.ofDays(cmd.trialDays()))
                    : null;

            Subscription subscription = subscriptionService.create(
                    customer, plan, price, cmd.seatCount(), startAt, trialEndAt);
            subscription.setPaymentMethod(paymentMethod);

            IyzicoSubscriptionResponse response =
                    iyzicoClient.createSubscription(customer, price, cmd.seatCount(), paymentMethod);
            applyGatewayState(subscription, response);

            Subscription saved = saveSubscriptionWithAllocations(subscription);
            return toDto(saved);
        } catch (IllegalArgumentException ex) {
            throw new SubscriptionOperationException(ex.getMessage(), ex);
        } catch (SubscriptionNotFoundException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            if (ex instanceof SubscriptionOperationException || ex instanceof SubscriptionNotFoundException) {
                throw ex;
            }
            throw new SubscriptionOperationException("Failed to create subscription", ex);
        }
    }

    @Override
    @Transactional
    public SubscriptionDto changePlan(UUID subscriptionId, ChangePlanCmd cmd) {
        Subscription subscription = loadSubscription(subscriptionId);
        ensureHasGatewayReference(subscription);
        try {
            Plan newPlan = loadPlan(cmd.planCode());
            Price newPrice = loadPrice(newPlan, cmd.billingPeriod());

            subscriptionService.changePlan(subscription, newPlan, newPrice, Instant.now());
            iyzicoClient.changeSubscriptionPlan(
                    subscription.getExternalSubscriptionId(), newPrice, mapProration(cmd.prorationOrDefault()));

            Subscription saved = saveSubscriptionWithAllocations(subscription);
            return toDto(saved);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new SubscriptionOperationException(ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            if (ex instanceof SubscriptionOperationException) {
                throw ex;
            }
            throw new SubscriptionOperationException("Failed to change subscription plan", ex);
        }
    }

    @Override
    @Transactional
    public SubscriptionDto updateSeats(UUID subscriptionId, int seatCount, Proration proration) {
        Subscription subscription = loadSubscription(subscriptionId);
        ensureHasGatewayReference(subscription);
        Proration resolved = proration != null ? proration : Proration.defaultValue();
        try {
            boolean paymentCollected = resolved == Proration.IMMEDIATE;
            subscriptionService.updateSeats(subscription, seatCount, Instant.now(), paymentCollected);
            iyzicoClient.updateSeatCount(
                    subscription.getExternalSubscriptionId(), seatCount, mapProration(resolved));

            Subscription saved = saveSubscriptionWithAllocations(subscription);
            return toDto(saved);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new SubscriptionOperationException(ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            if (ex instanceof SubscriptionOperationException) {
                throw ex;
            }
            throw new SubscriptionOperationException("Failed to update subscription seats", ex);
        }
    }

    @Override
    @Transactional
    public void cancel(UUID subscriptionId, boolean cancelAtPeriodEnd) {
        Subscription subscription = loadSubscription(subscriptionId);
        ensureHasGatewayReference(subscription);
        try {
            iyzicoClient.cancelSubscription(subscription.getExternalSubscriptionId(), cancelAtPeriodEnd);
            if (cancelAtPeriodEnd) {
                subscription.setCancelAtPeriodEnd(true);
                if (subscription.getStatus() == SubscriptionStatus.TRIAL) {
                    subscriptionStatusMetrics.updateStatus(subscription, SubscriptionStatus.ACTIVE);
                }
            } else {
                subscriptionService.cancel(subscription, Instant.now());
            }
            saveSubscriptionWithAllocations(subscription);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new SubscriptionOperationException(ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            if (ex instanceof SubscriptionOperationException) {
                throw ex;
            }
            throw new SubscriptionOperationException("Failed to cancel subscription", ex);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public SubscriptionDto getSubscription(UUID subscriptionId) {
        Subscription subscription = loadSubscription(subscriptionId);
        return toDto(subscription);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubscriptionDto> getSubscriptionsByCustomer(UUID customerId) {
        Customer customer = loadCustomer(customerId);
        return subscriptionRepository.findByCustomer(customer).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvoiceDto> getInvoicesByCustomer(UUID customerId) {
        Customer customer = loadCustomer(customerId);
        return subscriptionRepository.findByCustomer(customer).stream()
                .flatMap(subscription -> invoiceRepository
                        .findBySubscriptionOrderByPeriodStartDesc(subscription)
                        .stream())
                .map(this::toInvoiceDto)
                .collect(Collectors.toList());
    }

    private Subscription saveSubscriptionWithAllocations(Subscription subscription) {
        Subscription saved = subscriptionRepository.save(subscription);
        persistNewSeatAllocations(saved);
        return saved;
    }

    private void persistNewSeatAllocations(Subscription subscription) {
        List<SeatAllocation> allocations = subscription.getSeatAllocations();
        if (allocations == null || allocations.isEmpty()) {
            return;
        }
        for (SeatAllocation allocation : allocations) {
            if (allocation.getId() == null) {
                allocation.setSubscription(subscription);
                seatAllocationRepository.save(allocation);
            }
        }
    }

    private PaymentMethod resolvePaymentMethod(Customer customer, String cardToken) {
        if (StringUtils.hasText(cardToken)) {
            String tokenRef = iyzicoClient.createOrAttachPaymentMethod(customer, cardToken);
            PaymentMethod paymentMethod = PaymentMethod.builder()
                    .customer(customer)
                    .tokenRef(tokenRef)
                    .defaultMethod(true)
                    .build();
            PaymentMethod saved = paymentMethodRepository.save(paymentMethod);
            if (customer.getPaymentMethods() != null) {
                customer.getPaymentMethods().add(saved);
            }
            return saved;
        }
        return paymentMethodRepository
                .findByCustomerAndDefaultMethodIsTrue(customer)
                .orElseThrow(() -> new SubscriptionOperationException(
                        "Customer %s does not have a default payment method".formatted(customer.getId())));
    }

    private void applyGatewayState(Subscription subscription, IyzicoSubscriptionResponse response) {
        subscription.setExternalSubscriptionId(response.subscriptionId());
        subscription.setCurrentPeriodStart(response.currentPeriodStart());
        subscription.setCurrentPeriodEnd(response.currentPeriodEnd());
        subscription.setCancelAtPeriodEnd(response.cancelAtPeriodEnd());
        if (response.cancelAtPeriodEnd() && subscription.getStatus() == SubscriptionStatus.ACTIVE) {
            subscriptionStatusMetrics.updateStatus(subscription, SubscriptionStatus.CANCELED);
        }
    }

    private SubscriptionDto toDto(Subscription subscription) {
        int seatCount = resolveSeatCount(subscription);
        return new SubscriptionDto(
                subscription.getId(),
                subscription.getCustomer() != null ? subscription.getCustomer().getId() : null,
                subscription.getPlan() != null ? subscription.getPlan().getCode() : null,
                subscription.getPrice() != null ? subscription.getPrice().getBillingPeriod() : null,
                subscription.getStatus(),
                subscription.getStartAt(),
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd(),
                subscription.getTrialEndAt(),
                subscription.isCancelAtPeriodEnd(),
                seatCount,
                subscription.getPrice() != null ? subscription.getPrice().getCurrency() : null,
                subscription.getExternalSubscriptionId());
    }

    private int resolveSeatCount(Subscription subscription) {
        List<SeatAllocation> allocations = subscription.getSeatAllocations();
        SeatAllocation latest = null;
        if (allocations != null && !allocations.isEmpty()) {
            latest = allocations.stream()
                    .filter(Objects::nonNull)
                    .max(Comparator.comparing(SeatAllocation::getEffectiveFrom))
                    .orElse(null);
        }
        if (latest == null && subscription.getId() != null) {
            latest = seatAllocationRepository.findBySubscriptionOrderByEffectiveFromDesc(subscription).stream()
                    .findFirst()
                    .orElse(null);
        }
        return latest != null && latest.getSeatCount() != null ? latest.getSeatCount() : 0;
    }

    private InvoiceDto toInvoiceDto(Invoice invoice) {
        return new InvoiceDto(
                invoice.getId(),
                invoice.getSubscription() != null ? invoice.getSubscription().getId() : null,
                invoice.getPeriodStart(),
                invoice.getPeriodEnd(),
                invoice.getTotalCents(),
                invoice.getCurrency(),
                invoice.getStatus());
    }

    private Customer loadCustomer(UUID customerId) {
        return customerRepository
                .findById(customerId)
                .orElseThrow(() ->
                        new SubscriptionNotFoundException("Customer %s not found".formatted(customerId)));
    }

    private Plan loadPlan(String planCode) {
        return planRepository
                .findByCode(planCode)
                .orElseThrow(() -> new SubscriptionNotFoundException("Plan %s not found".formatted(planCode)));
    }

    private Price loadPrice(Plan plan, com.leadsyncpro.model.billing.BillingPeriod billingPeriod) {
        return priceRepository
                .findByPlanAndBillingPeriod(plan, billingPeriod)
                .orElseThrow(() -> new SubscriptionNotFoundException(
                        "Price not found for plan %s and billing period %s".formatted(
                                plan.getCode(), billingPeriod)));
    }

    private Subscription loadSubscription(UUID subscriptionId) {
        return subscriptionRepository
                .findById(subscriptionId)
                .orElseThrow(() ->
                        new SubscriptionNotFoundException("Subscription %s not found".formatted(subscriptionId)));
    }

    private void ensureHasGatewayReference(Subscription subscription) {
        if (!StringUtils.hasText(subscription.getExternalSubscriptionId())) {
            throw new SubscriptionOperationException("Subscription is not linked to the payment gateway");
        }
    }

    private ProrationBehavior mapProration(Proration proration) {
        return switch (proration) {
            case IMMEDIATE -> ProrationBehavior.IMMEDIATE;
            case DEFERRED -> ProrationBehavior.DEFERRED;
        };
    }

}
