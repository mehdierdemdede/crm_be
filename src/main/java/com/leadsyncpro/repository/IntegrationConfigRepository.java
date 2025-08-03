package com.leadsyncpro.repository;

import com.leadsyncpro.model.IntegrationConfig;
import com.leadsyncpro.model.IntegrationPlatform;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface IntegrationConfigRepository extends JpaRepository<IntegrationConfig, UUID> {
    Optional<IntegrationConfig> findByOrganizationIdAndPlatform(UUID organizationId, IntegrationPlatform platform);
}

