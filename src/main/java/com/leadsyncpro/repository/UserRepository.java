package com.leadsyncpro.repository;

import com.leadsyncpro.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByOrganizationIdAndEmail(UUID organizationId, String email);
    Optional<User> findByEmail(String email);
    boolean existsByOrganizationIdAndEmail(UUID organizationId, String email);
    List<User> findByOrganizationId(UUID organizationId);
    List<User> findByOrganizationIdAndAutoAssignEnabledTrue(UUID organizationId);

}