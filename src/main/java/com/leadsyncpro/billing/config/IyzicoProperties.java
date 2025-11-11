package com.leadsyncpro.billing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "iyzico")
public class IyzicoProperties {

    private String apiKey;
    private String secretKey;
    private String baseUrl;
    private String webhookSigningSecret;
}
