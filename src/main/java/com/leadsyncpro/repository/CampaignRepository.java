package com.leadsyncpro.repository;

import com.leadsyncpro.model.Campaign;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CampaignRepository extends JpaRepository<Campaign, UUID> {
    List<Campaign> findByOrganizationId(UUID organizationId);
    Optional<Campaign> findByOrganizationIdAndName(UUID organizationId, String name);
    boolean existsByOrganizationIdAndName(UUID organizationId, String name);
}