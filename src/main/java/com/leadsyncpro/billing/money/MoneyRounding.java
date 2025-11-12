package com.leadsyncpro.billing.money;

import com.leadsyncpro.billing.config.BillingProperties;
import java.math.BigDecimal;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class MoneyRounding {

    private final BillingProperties billingProperties;

    public MoneyRounding(BillingProperties billingProperties) {
        this.billingProperties = Objects.requireNonNull(billingProperties, "billingProperties must not be null");
    }

    public long roundToMinorUnit(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount must not be null");
        }
        return amount.setScale(0, billingProperties.getRounding()).longValueExact();
    }
}
