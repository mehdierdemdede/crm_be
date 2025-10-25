package com.leadsyncpro.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class LeadDistributionAssignmentRequest {
    private UUID userId;
    private Integer frequency;
    private Integer position;
}

