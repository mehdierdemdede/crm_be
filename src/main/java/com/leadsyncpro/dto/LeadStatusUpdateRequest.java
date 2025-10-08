package com.leadsyncpro.dto;

import com.leadsyncpro.model.LeadStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LeadStatusUpdateRequest {
    @NotNull
    private LeadStatus status;
}
