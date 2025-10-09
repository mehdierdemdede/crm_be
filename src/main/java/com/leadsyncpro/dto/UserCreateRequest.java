package com.leadsyncpro.dto;

import com.leadsyncpro.model.Role;
import com.leadsyncpro.model.SupportedLanguages;
import lombok.Data;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.Set;
import java.util.UUID;

@Data
public class UserCreateRequest {
    private UUID organizationId; // Sadece SUPER_ADMIN için
    @NotBlank
    @Email
    private String email;

    // Invite flow için password zorunlu olmamalı
    private String password;

    @NotBlank
    private String firstName;

    @NotBlank
    private String lastName;
    @NotNull
    private Role role;
    
    private Set<SupportedLanguages> supportedLanguages;
    @PositiveOrZero
    private Integer dailyCapacity;
    private boolean active = true;
    private boolean autoAssignEnabled = false;
}
