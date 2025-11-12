package com.leadsyncpro.billing.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadsyncpro.billing.facade.DefaultSubscriptionFacade;
import com.leadsyncpro.billing.facade.SubscriptionFacade;
import com.leadsyncpro.billing.integration.iyzico.DefaultIyzicoClient;
import com.leadsyncpro.billing.integration.iyzico.IyzicoClient;
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
import java.time.Duration;
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

    public BillingConfig(IyzicoProperties iyzicoProperties) {
        this.iyzicoProperties = iyzicoProperties;
    }

    @Bean
    public RestTemplate iyzicoRestTemplate(RestTemplateBuilder restTemplateBuilder) {
        return restTemplateBuilder
                .setConnectTimeout(CONNECT_TIMEOUT)
                .setReadTimeout(READ_TIMEOUT)
                .build();
    }

    @Bean
    public IyzicoClient iyzicoClient(RestTemplate iyzicoRestTemplate, ObjectMapper objectMapper) {
        return new DefaultIyzicoClient(iyzicoRestTemplate, iyzicoProperties, objectMapper);
    }

    @Bean
    public SubscriptionService subscriptionService() {
        return new SubscriptionService();
    }

    @Bean
    public PricingService pricingService() {
        return new PricingService();
    }

    @Bean
    public InvoicingService invoicingService(PricingService pricingService) {
        return new InvoicingService(pricingService);
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
            IyzicoClient iyzicoClient) {
        return new DefaultSubscriptionFacade(
                subscriptionRepository,
                customerRepository,
                planRepository,
                priceRepository,
                paymentMethodRepository,
                seatAllocationRepository,
                invoiceRepository,
                subscriptionService,
                iyzicoClient);
    }
}
