package com.leadsyncpro.dto;

import lombok.Data;

import java.util.List;

@Data
public class LeadDistributionRuleRequest {
    private String pageId;
    private String pageName;
    private String campaignId;
    private String campaignName;
    private String adsetId;
    private String adsetName;
    private String adId;
    private String adName;
    private List<LeadDistributionAssignmentRequest> assignments;
}

