package com.leadsyncpro.billing.config;

import com.leadsyncpro.billing.api.idempotency.CachedBodyFilter;
import com.leadsyncpro.billing.api.idempotency.IdempotencyInterceptor;
import com.leadsyncpro.billing.api.idempotency.IdempotencyStorage;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class IdempotencyConfiguration implements WebMvcConfigurer {

    private final IdempotencyInterceptor interceptor;

    public IdempotencyConfiguration(IdempotencyStorage storage, Clock clock) {
        this.interceptor = new IdempotencyInterceptor(storage, clock, Duration.ofMinutes(5));
    }

    @Bean
    public IdempotencyInterceptor idempotencyInterceptor() {
        return interceptor;
    }

    @Bean
    public FilterRegistrationBean<CachedBodyFilter> cachedBodyFilterRegistration() {
        FilterRegistrationBean<CachedBodyFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new CachedBodyFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor);
    }
}
