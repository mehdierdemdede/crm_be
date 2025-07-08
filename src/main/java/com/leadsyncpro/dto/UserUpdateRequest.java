package com.leadsyncpro.dto;

import com.leadsyncpro.model.Role;
import lombok.Data;

import java.util.UUID;

@Data
public class UserUpdateRequest {
    private String firstName;
    private String lastName;
    private Role role;
    private Boolean isActive;
    private UUID organizationId; // For SuperAdmin to specify which org's user to update
}
