package com.leadsyncpro.security;

import com.leadsyncpro.model.User;
import com.leadsyncpro.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // This method is primarily used by Spring Security for authentication.
        // For multi-tenancy, the organization ID is also needed.
        // We'll handle organization-specific user loading in the authentication process.
        // For now, this will find a user by email, which works for SuperAdmin.
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email : " + email));

        return UserPrincipal.create(user);
    }

    // Method to load user by ID, ensuring it belongs to the correct organization
    @Transactional
    public UserDetails loadUserByIdAndOrganization(UUID id, UUID organizationId) {
        User user = userRepository.findById(id).orElseThrow(
                () -> new UsernameNotFoundException("User not found with id : " + id)
        );

        // Crucial multi-tenancy check
        if (!user.getOrganizationId().equals(organizationId)) {
            throw new SecurityException("Access denied: User does not belong to this organization.");
        }

        return UserPrincipal.create(user);
    }
}
