package com.leadsyncpro.billing.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(name = "PublicSignupResponse")
public record PublicSignupResponse(
        @Schema(description = "Identifier of the recorded signup", example = "91a0bf5c-4ee8-4d67-98f0-3a9bf139df6f")
                UUID signupId,
        @Schema(description = "Identifier of the newly created organization", example = "1af24367-0dcf-4a4f-8d65-662be1f4b191")
                UUID organizationId,
        @Schema(description = "Email address of the invited admin", example = "ada@acme.co") String adminEmail,
        @Schema(description = "Invite token generated for the admin", example = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9")
                String inviteToken,
        @Schema(description = "Current status of the signup", example = "INVITE_SENT") String status,
        @Schema(description = "Human readable message describing the outcome", example = "Invitation email sent to organization admin")
                String message) {}
