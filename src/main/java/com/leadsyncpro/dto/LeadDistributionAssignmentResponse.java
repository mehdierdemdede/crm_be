package com.leadsyncpro.dto;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
public class LeadDistributionAssignmentResponse {
    UUID userId;
    String fullName;
    String email;
    boolean active;
    boolean autoAssignEnabled;
    Integer frequency;
    Integer position;
}

