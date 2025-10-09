package com.leadsyncpro.dto;

import com.leadsyncpro.model.Role;
import com.leadsyncpro.model.SupportedLanguages;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.util.Set;
import java.util.UUID;

@Data
public class UserUpdateRequest {
    private String firstName;
    private String lastName;
    private Role role;
    private Boolean isActive;
    private UUID organizationId; // For SuperAdmin to specify which org's user to update
    
    private Set<SupportedLanguages> supportedLanguages;
    @PositiveOrZero
    private Integer dailyCapacity;
    private Boolean autoAssignEnabled;
}
