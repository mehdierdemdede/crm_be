package com.leadsyncpro.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeadActionRequest {
    private String actionType; // örn: "phone", "whatsapp", "note"
    private String message;
}
