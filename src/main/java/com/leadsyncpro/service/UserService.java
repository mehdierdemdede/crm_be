package com.leadsyncpro.service;

import com.leadsyncpro.exception.ResourceNotFoundException;
import com.leadsyncpro.model.Role;
import com.leadsyncpro.model.SupportedLanguages;
import com.leadsyncpro.model.User;
import com.leadsyncpro.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Optional;
import java.util.EnumSet;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final InviteService inviteService;


    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, InviteService inviteService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.inviteService = inviteService;
    }

    @Transactional
    public User createUser(UUID organizationId,
                           String email,
                           String password,
                           String firstName,
                           String lastName,
                           Role role,
                           Set<SupportedLanguages> supportedLanguages,
                           Integer dailyCapacity,
                           boolean isActive,
                           boolean autoAssignEnabled) {
        String normalizedEmail = email.toLowerCase();
        if (userRepository.existsByOrganizationIdAndEmail(organizationId, normalizedEmail)) {
            throw new IllegalArgumentException("User with this email already exists in this organization.");
        }
        User user = new User();
        user.setOrganizationId(organizationId);
        user.setEmail(normalizedEmail);
        if (StringUtils.hasText(password)) {
            user.setPasswordHash(passwordEncoder.encode(password));
        } else {
            user.setPasswordHash(null);
        }
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setRole(role);
        user.setActive(isActive);
        user.setAutoAssignEnabled(autoAssignEnabled);
        user.setDailyCapacity(dailyCapacity);
        user.setSupportedLanguages(normalizeSupportedLanguages(supportedLanguages));
        return userRepository.save(user);
    }

    @Transactional
    public User updateUser(UUID userId,
                           UUID organizationId,
                           String firstName,
                           String lastName,
                           Role role,
                           Boolean isActive,
                           Set<SupportedLanguages> supportedLanguages,
                           Integer dailyCapacity,
                           Boolean autoAssignEnabled) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        // Ensure the user belongs to the correct organization (multi-tenancy check)
        if (organizationId != null && !user.getOrganizationId().equals(organizationId)) {
            throw new SecurityException("Access denied: User does not belong to this organization.");
        }

        if (firstName != null) user.setFirstName(firstName);
        if (lastName != null) user.setLastName(lastName);
        if (role != null) user.setRole(role);
        if (isActive != null) user.setActive(isActive);
        if (supportedLanguages != null) {
            user.setSupportedLanguages(normalizeSupportedLanguages(supportedLanguages));
        }
        if (dailyCapacity != null) {
            user.setDailyCapacity(dailyCapacity);
        }
        if (autoAssignEnabled != null) {
            user.setAutoAssignEnabled(autoAssignEnabled);
        }

        return userRepository.save(user);
    }

    @Transactional
    public void deleteUser(UUID userId, UUID organizationId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        // Ensure the user belongs to the correct organization (multi-tenancy check)
        if (organizationId != null && !user.getOrganizationId().equals(organizationId)) {
            throw new SecurityException("Access denied: User does not belong to this organization.");
        }

        userRepository.delete(user);
    }

    public List<User> getUsersByOrganization(UUID organizationId) {
        return userRepository.findByOrganizationId(organizationId);
    }

    public User getUserById(UUID userId, UUID organizationId) {
        return userRepository.findById(userId)
                .filter(user -> user.getOrganizationId().equals(organizationId)) // Multi-tenancy check
                .orElseThrow(() -> new ResourceNotFoundException("User not found or access denied."));
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email); // For SuperAdmin login
    }

    // This method is typically only called by SuperAdmin
    public List<User> findAll() {
        return userRepository.findAll();
    }

    // This method is typically only called by SuperAdmin
    public User findById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + id));
    }


    @Transactional
    public User createAndInviteUser(UUID orgId,
                                    String email,
                                    String firstName,
                                    String lastName,
                                    Role role,
                                    Set<SupportedLanguages> supportedLanguages,
                                    Integer dailyCapacity,
                                    boolean isActive,
                                    boolean autoAssignEnabled) {
        String normalizedEmail = email.toLowerCase();
        if (userRepository.existsByOrganizationIdAndEmail(orgId, normalizedEmail)) {
            throw new IllegalArgumentException("User with this email already exists in this organization.");
        }

        User user = new User();
        user.setOrganizationId(orgId);
        user.setEmail(normalizedEmail);
        user.setPasswordHash(null);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setRole(role);
        user.setActive(isActive);
        user.setAutoAssignEnabled(autoAssignEnabled);
        user.setDailyCapacity(dailyCapacity);
        user.setSupportedLanguages(normalizeSupportedLanguages(supportedLanguages));

        userRepository.save(user);

        // Invite + mail aynı transaction içinde
        inviteService.createInvite(user);

        return user;
    }

    private Set<SupportedLanguages> normalizeSupportedLanguages(Set<SupportedLanguages> languages) {
        if (languages == null || languages.isEmpty()) {
            return EnumSet.noneOf(SupportedLanguages.class);
        }
        return EnumSet.copyOf(languages);
    }

}
