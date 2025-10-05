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

    long countByOrganizationIdAndCreatedAtBetween(UUID organizationId, Instant start, Instant end);

    @Query("""
        SELECT l.status, COUNT(l)
        FROM Lead l
        WHERE l.organizationId = :orgId
          AND l.createdAt BETWEEN :start AND :end
        GROUP BY l.status
    """)
    List<Object[]> countByStatusBetween(@Param("orgId") UUID orgId,
                                        @Param("start") Instant start,
                                        @Param("end") Instant end);

    @Query("""
        SELECT COALESCE(c.name, 'Unassigned'), COUNT(l)
        FROM Lead l
        LEFT JOIN l.campaign c
        WHERE l.organizationId = :orgId
          AND l.createdAt BETWEEN :start AND :end
        GROUP BY c.name
        ORDER BY COUNT(l) DESC
    """)
    List<Object[]> countByCampaignBetween(@Param("orgId") UUID orgId,
                                          @Param("start") Instant start,
                                          @Param("end") Instant end);

    List<Lead> findByOrganizationIdAndCreatedAtBetween(UUID organizationId, Instant start, Instant end);



}
