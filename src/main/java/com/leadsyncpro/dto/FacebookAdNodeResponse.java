package com.leadsyncpro.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class FacebookAdNodeResponse {
    String adId;
    String adName;
    LeadDistributionRuleResponse rule;
}

