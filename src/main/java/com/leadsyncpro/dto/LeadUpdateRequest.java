package com.leadsyncpro.dto;

import lombok.Data;
import jakarta.validation.constraints.Email;

import java.util.UUID;

@Data
public class LeadUpdateRequest {
    private String name;
    private String phone;
    @Email
    private String email;
    private String language;
    private String notes;
    private UUID campaignId;
    private boolean clearCampaign; // To explicitly clear campaign association
    private String status; // Will be converted to LeadStatus enum
    private UUID assignedToUserId;
    private boolean clearAssignedUser; // To explicitly clear assigned user association
}