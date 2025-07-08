package com.leadsyncpro.controller;

import com.leadsyncpro.dto.LoginRequest;
import com.leadsyncpro.dto.JwtAuthenticationResponse;
import com.leadsyncpro.model.User;
import com.leadsyncpro.model.Role;
import com.leadsyncpro.security.JwtTokenProvider;
import com.leadsyncpro.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserService userService;

    public AuthController(AuthenticationManager authenticationManager,
                          JwtTokenProvider tokenProvider,
                          UserService userService) {
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
        this.userService = userService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {

        // For SuperAdmin, organizationId will be "super" or a specific ID for global access
        // For regular users, organizationId is crucial for multi-tenancy
        UUID organizationId = null;
        if (!"super".equalsIgnoreCase(loginRequest.getOrganizationId())) {
            try {
                organizationId = UUID.fromString(loginRequest.getOrganizationId());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body("Invalid Organization ID format.");
            }
        }

        // Find the user by email and organizationId
        User user = null;
        if (organizationId != null) {
            user = userService.findByEmailAndOrganizationId(loginRequest.getEmail(), organizationId)
                    .orElse(null);
        } else { // Handle SuperAdmin login where orgId might be a special string like "super"
            user = userService.findByEmail(loginRequest.getEmail())
                    .filter(u -> u.getRole() == Role.SUPER_ADMIN) // Ensure it's a SuperAdmin
                    .orElse(null);
            // If it's a SuperAdmin, set their organizationId to null or a specific value
            if (user != null && user.getRole() == Role.SUPER_ADMIN) {
                organizationId = null; // Or a specific UUID for global SuperAdmin context
            }
        }

        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials or organization ID.");
        }

        // Authenticate using Spring Security's AuthenticationManager
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getEmail(), // Spring Security uses this as 'username'
                        loginRequest.getPassword()
                )
        );

        // Set authentication in SecurityContext
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Generate JWT token
        String jwt = tokenProvider.generateToken(authentication);
        return ResponseEntity.ok(new JwtAuthenticationResponse(jwt, "Bearer"));

    }
}