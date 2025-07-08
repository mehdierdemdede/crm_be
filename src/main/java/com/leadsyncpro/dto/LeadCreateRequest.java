package com.leadsyncpro.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Email;

import java.util.UUID;

@Data
public class LeadCreateRequest {
    @NotBlank
    private String name;
    private String phone;
    @Email
    private String email;
    private String language;
    private String notes;
    private UUID campaignId;
    @NotBlank
    private String status; // Will be converted to LeadStatus enum
    private UUID assignedToUserId;
}