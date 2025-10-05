package com.leadsyncpro.dto;

import com.leadsyncpro.model.LeadAction;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeadActionResponse {
    private UUID id;
    private String actionType;
    private String message;
    private UUID userId;
    private Instant createdAt;

    public LeadActionResponse(LeadAction entity) {
        this.id = entity.getId();
        this.actionType = entity.getActionType();
        this.message = entity.getMessage();
        this.userId = entity.getUserId();
        this.createdAt = entity.getCreatedAt();
    }
}
