package com.leadsyncpro.billing.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadsyncpro.billing.integration.iyzico.DefaultIyzicoClient;
import com.leadsyncpro.billing.integration.iyzico.IyzicoClient;
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
}
