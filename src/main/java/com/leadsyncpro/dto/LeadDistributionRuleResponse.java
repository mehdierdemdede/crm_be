package com.leadsyncpro.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.UUID;

@Value
@Builder
public class LeadDistributionRuleResponse {
    UUID id;
    String pageId;
    String pageName;
    String campaignId;
    String campaignName;
    String adsetId;
    String adsetName;
    String adId;
    String adName;
    List<LeadDistributionAssignmentResponse> assignments;
}

