package com.leadsyncpro.security;

import com.leadsyncpro.model.User;
import com.leadsyncpro.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private static final Logger logger = LoggerFactory.getLogger(CustomUserDetailsService.class);

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        logger.debug("Attempting to load user by email: {}", email);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    logger.warn("User not found with email: {}", email);
                    return new UsernameNotFoundException("User not found with email : " + email);
                });
        logger.debug("User found: {} with role: {}", user.getEmail(), user.getRole());
        return UserPrincipal.create(user);
    }

    // Method to load user by ID, ensuring it belongs to the correct organization
    @Transactional
    public UserDetails loadUserByIdAndOrganization(UUID id, UUID organizationId) {
        logger.debug("Attempting to load user by ID: {} for organization: {}", id, organizationId);
        User user = userRepository.findById(id).orElseThrow(
                () -> {
                    logger.warn("User not found with ID: {}", id);
                    return new UsernameNotFoundException("User not found with id : " + id);
                }
        );

        // Crucial multi-tenancy check
        if (!user.getOrganizationId().equals(organizationId)) {
            logger.warn("Access denied: User {} (ID: {}) does not belong to organization {}", user.getEmail(), id, organizationId);
            throw new SecurityException("Access denied: User does not belong to this organization.");
        }
        logger.debug("User found by ID: {} for organization: {}", user.getEmail(), organizationId);
        return UserPrincipal.create(user);
    }
}