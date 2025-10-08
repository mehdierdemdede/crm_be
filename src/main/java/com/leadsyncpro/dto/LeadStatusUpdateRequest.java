package com.leadsyncpro.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LeadStatusUpdateRequest {
    @NotBlank
    private String status;
}
