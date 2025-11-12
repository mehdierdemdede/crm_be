package com.leadsyncpro.billing.support;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.leadsyncpro.billing.integration.iyzico.IyzicoClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

@TestConfiguration
public class WireMockIyzicoTestConfig {

    @Bean(initMethod = "start", destroyMethod = "stop")
    public WireMockServer wireMockServer() {
        return new WireMockServer(wireMockConfig().dynamicPort());
    }

    @Bean
    @Primary
    public IyzicoClient wireMockIyzicoClient(WireMockServer wireMockServer, RestTemplateBuilder builder) {
        RestTemplate restTemplate = builder.build();
        return new WireMockIyzicoClient(restTemplate, wireMockServer.baseUrl());
    }
}
