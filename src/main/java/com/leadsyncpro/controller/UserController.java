// src/main/java/com/leadsyncpro/controller/UserController.java
package com.leadsyncpro.controller;

import com.leadsyncpro.dto.InviteAcceptRequest;
import com.leadsyncpro.dto.UserCreateRequest;
import com.leadsyncpro.dto.UserResponse;
import com.leadsyncpro.model.Role;
import com.leadsyncpro.model.User;
import com.leadsyncpro.repository.UserRepository;
import com.leadsyncpro.service.InviteService;
import com.leadsyncpro.service.UserService;
import com.leadsyncpro.security.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final InviteService inviteService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserController(UserService userService, InviteService inviteService,
            UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.inviteService = inviteService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/invite")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> inviteUser(
            @RequestBody @Valid UserCreateRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID orgId = principal.getOrganizationId();
        if (principal.getRole() == Role.SUPER_ADMIN && req.getOrganizationId() != null) {
            orgId = req.getOrganizationId();
        }

        User u = userService.createAndInviteUser(
                orgId,
                req.getEmail(),
                req.getFirstName(),
                req.getLastName(),
                req.getRole(),
                req.getSupportedLanguages(),
                req.getDailyCapacity(),
                req.isActive(),
                req.isAutoAssignEnabled());
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(u));
    }

    @PostMapping("/invite/accept")
    public ResponseEntity<?> acceptInvite(@RequestBody InviteAcceptRequest req) {
        try {
            inviteService.acceptInvite(req.getToken(), req.getPassword(), passwordEncoder, userRepository);
            return ResponseEntity.ok(Map.of("message", "Password set, account activated"));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", ex.getMessage()));
        }
    }

    // Get all users for the current organization (Admin) or all users (SuperAdmin)
    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<User>> getAllUsers(@AuthenticationPrincipal UserPrincipal currentUser) {
        if (currentUser.getRole() == Role.SUPER_ADMIN) {
            return ResponseEntity.ok(userService.findAll());
        } else {
            return ResponseEntity.ok(userService.getUsersByOrganization(currentUser.getOrganizationId()));
        }
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<User> getCurrentUser(@AuthenticationPrincipal UserPrincipal currentUser) {
        User user = userService.findById(currentUser.getId());
        return ResponseEntity.ok(user);
    }

    @GetMapping("/me/stats")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> getMyStats(@AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(userService.getUserStats(currentUser.getId(), currentUser.getOrganizationId()));
    }
}