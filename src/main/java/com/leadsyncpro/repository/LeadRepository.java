package com.leadsyncpro.repository;

import com.leadsyncpro.model.IntegrationPlatform;
import com.leadsyncpro.model.Lead;
import com.leadsyncpro.model.LeadStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeadRepository extends JpaRepository<Lead, UUID> {
    List<Lead> findByOrganizationId(UUID organizationId);

    List<Lead> findByOrganizationIdAndStatus(UUID organizationId, LeadStatus status);

    List<Lead> findByOrganizationIdAndAssignedToUserId(UUID organizationId, UUID assignedToUserId);

    List<Lead> findByOrganizationIdAndCampaignId(UUID organizationId, UUID campaignId);

    @Query("SELECT l FROM Lead l WHERE l.organizationId = :organizationId " +
            "AND (:campaignId IS NULL OR l.campaign.id = :campaignId) " +
            "AND (:status IS NULL OR l.status = :status) " +
            "AND (:assignedToUserId IS NULL OR l.assignedToUser.id = :assignedToUserId)")
    List<Lead> findByOrganizationIdAndFilters(
            @Param("organizationId") UUID organizationId,
            @Param("campaignId") UUID campaignId,
            @Param("status") LeadStatus status,
            @Param("assignedToUserId") UUID assignedToUserId);

    boolean existsByOrganizationIdAndPlatformAndSourceLeadId(
            UUID organizationId, IntegrationPlatform platform, String sourceLeadId);

    Optional<Lead> findByOrganizationIdAndPlatformAndSourceLeadId(
            UUID organizationId,
            IntegrationPlatform platform,
            String sourceLeadId
    );

    List<Lead> findAllByStatusAndUpdatedAtBefore(LeadStatus status, Instant updatedAt);


}
