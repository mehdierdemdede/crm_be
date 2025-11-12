package com.leadsyncpro.controller;

import com.leadsyncpro.dto.JwtAuthenticationResponse;
import com.leadsyncpro.dto.LoginRequest;
import com.leadsyncpro.model.User;
import com.leadsyncpro.security.JwtTokenProvider;
import com.leadsyncpro.service.UserService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    public AuthController(AuthenticationManager authenticationManager,
                          JwtTokenProvider tokenProvider,
                          UserService userService,
                          PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        logger.info("Login attempt for email: {} in organization: {}", loginRequest.getEmail(), loginRequest.getOrganizationId());

        User user = userService.findByEmail(loginRequest.getEmail())
                .orElse(null);

        if (user == null) {
            logger.warn("User not found or role mismatch for email: {} in organization: {}", loginRequest.getEmail(), loginRequest.getOrganizationId());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials or organization ID.");
        }

        logger.debug("User found: {} (Role: {}) for authentication. Attempting authentication...", user.getEmail(), user.getRole());
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getEmail(),
                        loginRequest.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        logger.info("User {} successfully authenticated.", loginRequest.getEmail());

        String jwt = tokenProvider.generateToken(authentication);
        return ResponseEntity.ok(new JwtAuthenticationResponse(jwt, "Bearer"));
    }

    @PostMapping("/hash-password")
    public ResponseEntity<String> hashPassword(@RequestBody String plainPassword) {
        String hashedPassword = passwordEncoder.encode(plainPassword);
        logger.info("Hashed password generated for request.");
        return ResponseEntity.ok(hashedPassword);
    }
}
