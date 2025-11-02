// src/main/java/com/leadsyncpro/controller/UserController.java
package com.leadsyncpro.controller;

import com.leadsyncpro.dto.InviteAcceptRequest;
import com.leadsyncpro.dto.UserCreateRequest;
import com.leadsyncpro.dto.UserResponse;
import com.leadsyncpro.dto.UserUpdateRequest;
import com.leadsyncpro.model.Role;
import com.leadsyncpro.model.User;
import com.leadsyncpro.repository.InviteTokenRepository;
import com.leadsyncpro.repository.UserRepository;
import com.leadsyncpro.service.InviteService;
import com.leadsyncpro.service.OrganizationService;
import com.leadsyncpro.service.UserService;
import com.leadsyncpro.security.UserPrincipal;
import jakarta.annotation.security.PermitAll;
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
    private final InviteTokenRepository inviteTokenRepository;
    private final UserRepository userRepository;
    private final OrganizationService organizationService; // plan limit bilgisi
    private final PasswordEncoder passwordEncoder;

    public UserController(UserService userService, InviteService inviteService,
                          InviteTokenRepository inviteTokenRepository, UserRepository userRepository,
                          OrganizationService organizationService, PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.inviteService = inviteService;
        this.inviteTokenRepository = inviteTokenRepository;
        this.userRepository = userRepository;
        this.organizationService = organizationService;
        this.passwordEncoder = passwordEncoder;
    }


    @PostMapping("/invite")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> inviteUser(
            @RequestBody @Valid UserCreateRequest req,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
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
                req.isAutoAssignEnabled()
        );
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

    // Create User (Admin or SuperAdmin)
    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody UserCreateRequest request,
                                           @AuthenticationPrincipal UserPrincipal currentUser) {
        UUID targetOrgId = currentUser.getRole() == Role.SUPER_ADMIN
                ? request.getOrganizationId()
                : currentUser.getOrganizationId();
        if (targetOrgId == null) {
            return ResponseEntity.badRequest().build();
        }

        User newUser = userService.createUser(
                targetOrgId,
                request.getEmail(),
                request.getPassword(),
                request.getFirstName(),
                request.getLastName(),
                request.getRole(),
                request.getSupportedLanguages(),
                request.getDailyCapacity(),
                request.isActive(),
                request.isAutoAssignEnabled()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(newUser));
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

    // Get user by ID (scoped to organization for Admin/User)
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'USER', 'SUPER_ADMIN')")
    public ResponseEntity<User> getUserById(@PathVariable UUID id,
                                            @AuthenticationPrincipal UserPrincipal currentUser) {
        if (currentUser.getRole() == Role.SUPER_ADMIN) {
            return ResponseEntity.ok(userService.findById(id));
        } else {
            return ResponseEntity.ok(userService.getUserById(id, currentUser.getOrganizationId()));
        }
    }

    // Update User (Admin or SuperAdmin)
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<UserResponse> updateUser(@PathVariable UUID id,
                                           @Valid @RequestBody UserUpdateRequest request,
                                           @AuthenticationPrincipal UserPrincipal currentUser) {
        // SuperAdmin can update any user, Admin only users within their organization
        UUID targetOrgId = currentUser.getRole() == Role.SUPER_ADMIN ? request.getOrganizationId() : currentUser.getOrganizationId();
        if (targetOrgId == null && currentUser.getRole() == Role.SUPER_ADMIN) {
            // For SuperAdmin, if no orgId is provided in request, it implies updating a user in the same org as the super admin
            targetOrgId = currentUser.getOrganizationId();
        } else if (targetOrgId == null) {
            // For Admin, orgId is always current user's orgId
            targetOrgId = currentUser.getOrganizationId();
        }


        User updatedUser = userService.updateUser(
                id,
                targetOrgId, // Ensure multi-tenancy is respected
                request.getFirstName(),
                request.getLastName(),
                request.getRole(),
                request.getIsActive(),
                request.getSupportedLanguages(),
                request.getDailyCapacity(),
                request.getAutoAssignEnabled()
        );
        return ResponseEntity.ok(UserResponse.from(updatedUser));
    }

    // Delete User (Admin or SuperAdmin)
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id,
                                           @AuthenticationPrincipal UserPrincipal currentUser) {
        // SuperAdmin can delete any user, Admin only users within their organization
        if (currentUser.getRole() == Role.SUPER_ADMIN) {
            userService.deleteUser(id, null); // SuperAdmin can delete without orgId check in service
        } else {
            userService.deleteUser(id, currentUser.getOrganizationId());
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<User> getCurrentUser(@AuthenticationPrincipal UserPrincipal currentUser) {
        User user = userService.findById(currentUser.getId());
        return ResponseEntity.ok(user);
    }

    @GetMapping("/getMyHashedPass")
    @PermitAll
    public String getMyHashedPass() {
        return userService.getMyHashedPass(passwordEncoder);
    }


}