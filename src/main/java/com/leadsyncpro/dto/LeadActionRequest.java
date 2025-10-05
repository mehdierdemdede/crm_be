package com.leadsyncpro.dto;

import com.leadsyncpro.model.ActionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LeadActionRequest {
    @NotNull
    private ActionType actionType;

    @NotBlank
    private String message;
}
