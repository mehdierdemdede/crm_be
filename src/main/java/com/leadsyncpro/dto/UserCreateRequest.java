package com.leadsyncpro.dto;

import com.leadsyncpro.model.Role;
import lombok.Data;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Data
public class UserCreateRequest {
    private UUID organizationId; // Sadece SUPER_ADMIN için
    @NotBlank
    @Email
    private String email;

    // Invite flow için password zorunlu olmamalı
    private String password;

    private String firstName;
    private String lastName;
    @NotNull
    private Role role;
}
