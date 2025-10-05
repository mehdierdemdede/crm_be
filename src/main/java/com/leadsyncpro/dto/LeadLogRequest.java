package com.leadsyncpro.dto;

import com.leadsyncpro.model.LeadActionType;
import lombok.Data;

@Data
public class LeadLogRequest {
    private LeadActionType actionType;
    private String message;
}
