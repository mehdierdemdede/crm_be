package com.leadsyncpro.dto;

import com.leadsyncpro.model.LeadStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeadResponse {
    private UUID id;
    private String name;
    private LeadStatus status;
    private String phone;
    private String email;
    private String language;
    private String notes;
    private String messengerPageId; // mapped from pageId
    private UUID lastSaleId;
    private SaleResponse lastSale;

    // Campaign & User
    private CampaignResponse campaign;
    private UserResponse assignedToUser;

    // Dates
    private java.time.Instant createdAt;
    private java.time.Instant updatedAt;
    private java.time.Instant firstActionAt;

    // Platform / Facebook Info
    private com.leadsyncpro.model.IntegrationPlatform platform;
    private String sourceLeadId;
    private java.time.Instant platformCreatedAt;
    private String pageId;
    private String formId;
    private String formName;
    private Boolean organic;

    // Ad Details
    private String adId;
    private String adName;
    private String adsetId;
    private String adsetName;
    private String fbCampaignId;
    private String fbCampaignName;

    // JSON
    private String extraFieldsJson;
    private String disclaimerResponsesJson;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CampaignResponse {
        private UUID id;
        private String name;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class UserResponse {
        private UUID id;
        private String firstName;
        private String lastName;
        private String email;
    }
}
