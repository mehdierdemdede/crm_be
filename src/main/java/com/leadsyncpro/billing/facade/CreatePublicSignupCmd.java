package com.leadsyncpro.billing.facade;

import com.leadsyncpro.model.billing.BillingPeriod;
import java.util.UUID;

public record CreatePublicSignupCmd(
        UUID planId,
        BillingPeriod billingPeriod,
        int seatCount,
        String organizationName,
        String adminFirstName,
        String adminLastName,
        String adminEmail,
        String adminPhone) {}
