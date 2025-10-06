package com.leadsyncpro.dto;

import com.leadsyncpro.model.LeadAction;
import com.leadsyncpro.model.LeadActivityLog;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeadActionResponse {

    private UUID id;
    private String actionType;
    private String message;
    private Instant createdAt;
    private UUID userId;

    // ✅ Constructor for LeadAction
    public LeadActionResponse(LeadAction entity) {
        this.id = entity.getId();
        this.actionType = entity.getActionType();
        this.message = entity.getMessage();
        this.createdAt = entity.getCreatedAt();
        this.userId = entity.getUserId();
    }

    // ✅ fromEntity() for LeadAction
    public static LeadActionResponse fromEntity(LeadAction entity) {
        return LeadActionResponse.builder()
                .id(entity.getId())
                .actionType(entity.getActionType())
                .message(entity.getMessage())
                .createdAt(entity.getCreatedAt())
                .userId(entity.getUserId())
                .build();
    }

    // ✅ fromEntity() for LeadActivityLog
    public static LeadActionResponse fromEntity(LeadActivityLog entity) {
        return LeadActionResponse.builder()
                .id(entity.getId())
                .actionType(entity.getAction())
                .message(entity.getDetails())
                .createdAt(entity.getCreatedAt())
                .userId(entity.getUserId())
                .build();
    }
}
