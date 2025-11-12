package com.leadsyncpro.billing.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "CancelSubscriptionRequest")
public record CancelSubscriptionRequest(@Schema(defaultValue = "false") boolean cancelAtPeriodEnd) {}
