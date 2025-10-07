package com.leadsyncpro.dto;

import com.leadsyncpro.model.ActionType;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeadActionRequest {

    @Enumerated(EnumType.STRING)
    private ActionType actionType; // örn: "phone", "whatsapp", "note"

    private String message;
}
