package com.leadsyncpro.billing.config;

import java.math.RoundingMode;
import java.time.ZoneId;
import java.util.Currency;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "billing")
public class BillingProperties {

    private Currency currency = Currency.getInstance("TRY");
    private ZoneId timezone = ZoneId.of("Europe/Istanbul");
    private RoundingMode rounding = RoundingMode.HALF_UP;

    public void setCurrency(Currency currency) {
        this.currency = Objects.requireNonNull(currency, "currency must not be null");
    }

    public void setTimezone(ZoneId timezone) {
        this.timezone = Objects.requireNonNull(timezone, "timezone must not be null");
    }

    public void setRounding(RoundingMode rounding) {
        this.rounding = Objects.requireNonNull(rounding, "rounding must not be null");
    }
}
