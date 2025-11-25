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
import java.time.Duration;
import java.time.ZoneId;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Tracer;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableRetry
public class BillingConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final IyzicoProperties iyzicoProperties;
    private final BillingProperties billingProperties;

    public BillingConfig(IyzicoProperties iyzicoProperties, BillingProperties billingProperties) {
        this.iyzicoProperties = iyzicoProperties;
        this.billingProperties = billingProperties;
    }

    @Bean
    public RestTemplate iyzicoRestTemplate(RestTemplateBuilder restTemplateBuilder) {
        ClientHttpRequestFactorySettings requestFactorySettings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(CONNECT_TIMEOUT)
                .withReadTimeout(READ_TIMEOUT);

        return restTemplateBuilder
                .requestFactorySettings(requestFactorySettings)
                .build();
    }

    @Bean
    public IyzicoClient iyzicoClient(
            RestTemplate iyzicoRestTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            Tracer tracer) {
        return new DefaultIyzicoClient(
                iyzicoRestTemplate, iyzicoProperties, objectMapper, meterRegistry, tracer);
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
