// src/main/java/com/leadsyncpro/controller/UserController.java
package com.leadsyncpro.controller;

import com.leadsyncpro.dto.UserCreateRequest;
import com.leadsyncpro.dto.UserUpdateRequest;
import com.leadsyncpro.model.Role;
import com.leadsyncpro.model.User;
import com.leadsyncpro.service.UserService;
import com.leadsyncpro.security.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // Create User (Admin or SuperAdmin)
    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<User> createUser(@Valid @RequestBody UserCreateRequest request,
                                           @AuthenticationPrincipal UserPrincipal currentUser) {
        // SuperAdmin can create users for any organization, Admin only for their own
        UUID targetOrgId = currentUser.getRole() == Role.SUPER_ADMIN ? request.getOrganizationId() : currentUser.getOrganizationId();
        if (targetOrgId == null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST); // Org ID required for SuperAdmin creating new org user
        }

        User newUser = userService.createUser(
                targetOrgId,
                request.getEmail(),
                request.getPassword(),
                request.getFirstName(),
                request.getLastName(),
                request.getRole()
        );
        return new ResponseEntity<>(newUser, HttpStatus.CREATED);
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
    public ResponseEntity<User> updateUser(@PathVariable UUID id,
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
                request.getIsActive()
        );
        return ResponseEntity.ok(updatedUser);
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
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}