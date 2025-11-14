package com.leadsyncpro.billing.facade;

import com.leadsyncpro.model.billing.PublicSignupStatus;
import java.util.UUID;

public record PublicSignupResult(
        UUID signupId,
        UUID organizationId,
        String adminEmail,
        String inviteToken,
        PublicSignupStatus status,
        String message) {}
