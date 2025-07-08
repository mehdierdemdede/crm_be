package com.leadsyncpro.repository;

import com.leadsyncpro.model.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {
    // Custom query methods can be added here if needed
}