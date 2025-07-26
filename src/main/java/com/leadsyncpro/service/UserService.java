package com.leadsyncpro.service;

import com.leadsyncpro.exception.ResourceNotFoundException;
import com.leadsyncpro.model.Role;
import com.leadsyncpro.model.User;
import com.leadsyncpro.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User createUser(UUID organizationId, String email, String password, String firstName, String lastName, Role role) {
        if (userRepository.existsByOrganizationIdAndEmail(organizationId, email)) {
            throw new IllegalArgumentException("User with this email already exists in this organization.");
        }
        User user = new User();
        user.setOrganizationId(organizationId);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password)); // Hash password
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setRole(role);
        user.setActive(true);
        return userRepository.save(user);
    }

    @Transactional
    public User updateUser(UUID userId, UUID organizationId, String firstName, String lastName, Role role, Boolean isActive) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        // Ensure the user belongs to the correct organization (multi-tenancy check)
        if (!user.getOrganizationId().equals(organizationId)) {
            throw new SecurityException("Access denied: User does not belong to this organization.");
        }

        if (firstName != null) user.setFirstName(firstName);
        if (lastName != null) user.setLastName(lastName);
        if (role != null) user.setRole(role);
        if (isActive != null) user.setActive(isActive);

        return userRepository.save(user);
    }

    @Transactional
    public void deleteUser(UUID userId, UUID organizationId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        // Ensure the user belongs to the correct organization (multi-tenancy check)
        if (!user.getOrganizationId().equals(organizationId)) {
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

    public Optional<User> findByEmailAndOrganizationId(String email, UUID organizationId) {
        return userRepository.findByOrganizationIdAndEmail(organizationId, email);
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
}
