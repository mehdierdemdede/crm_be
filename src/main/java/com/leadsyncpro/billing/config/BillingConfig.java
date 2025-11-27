package com.leadsyncpro.billing.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadsyncpro.billing.facade.DefaultSubscriptionFacade;
import com.leadsyncpro.billing.facade.SubscriptionFacade;
import com.leadsyncpro.billing.integration.iyzico.DefaultIyzicoClient;
import com.leadsyncpro.billing.integration.iyzico.IyzicoClient;
import com.leadsyncpro.billing.metrics.SubscriptionStatusMetrics;
import com.leadsyncpro.billing.money.MoneyRounding;
import com.leadsyncpro.billing.service.InvoicingService;
import com.leadsyncpro.billing.service.PricingService;
import com.leadsyncpro.billing.service.SubscriptionService;
import com.leadsyncpro.repository.billing.CustomerRepository;
import com.leadsyncpro.repository.billing.InvoiceRepository;
import com.leadsyncpro.repository.billing.PaymentMethodRepository;
import com.leadsyncpro.repository.billing.PlanRepository;
import com.leadsyncpro.repository.billing.PriceRepository;
import com.leadsyncpro.repository.billing.SeatAllocationRepository;
import com.leadsyncpro.repository.billing.SubscriptionRepository;
import java.time.Clock;
import java.time.ZoneId;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Tracer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

@Configuration
@EnableRetry
public class BillingConfig {

    private final IyzicoProperties iyzicoProperties;
    private final BillingProperties billingProperties;

    public BillingConfig(IyzicoProperties iyzicoProperties, BillingProperties billingProperties) {
        this.iyzicoProperties = iyzicoProperties;
        this.billingProperties = billingProperties;
    }

    @Bean
    public IyzicoClient iyzicoClient(
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            Tracer tracer) {
        return new DefaultIyzicoClient(iyzicoProperties, objectMapper, meterRegistry, tracer);
    }

    @Bean
    public SubscriptionService subscriptionService(SubscriptionStatusMetrics subscriptionStatusMetrics) {
        return new SubscriptionService(subscriptionStatusMetrics);
    }

    @Bean
    public PricingService pricingService() {
        return new PricingService();
    }

    @Bean
    public InvoicingService invoicingService(PricingService pricingService, MoneyRounding moneyRounding) {
        return new InvoicingService(pricingService, moneyRounding);
    }

    @Bean
    public Clock systemClock() {
        ZoneId zoneId = billingProperties.getTimezone();
        return Clock.system(zoneId != null ? zoneId : ZoneId.of("UTC"));
    }

    @Bean
    public SubscriptionFacade subscriptionFacade(
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
        return new DefaultSubscriptionFacade(
                subscriptionRepository,
                customerRepository,
                planRepository,
                priceRepository,
                paymentMethodRepository,
                seatAllocationRepository,
                invoiceRepository,
                subscriptionService,
                iyzicoClient,
                subscriptionStatusMetrics);
    }
}
