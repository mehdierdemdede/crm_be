package com.leadsyncpro.dto;

import com.leadsyncpro.model.ActionType;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@AllArgsConstructor
public class LeadActionResponse {
    private UUID id;
    private UUID leadId;
    private UUID userId;
    private ActionType actionType;
    private String message;
    private Instant createdAt;
}
