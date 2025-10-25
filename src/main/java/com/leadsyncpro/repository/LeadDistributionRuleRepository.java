package com.leadsyncpro.repository;

import com.leadsyncpro.model.IntegrationPlatform;
import com.leadsyncpro.model.LeadDistributionRule;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeadDistributionRuleRepository extends JpaRepository<LeadDistributionRule, UUID> {

    Optional<LeadDistributionRule> findByOrganizationIdAndPlatformAndPageIdAndCampaignIdAndAdsetIdAndAdId(
            UUID organizationId,
            IntegrationPlatform platform,
            String pageId,
            String campaignId,
            String adsetId,
            String adId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<LeadDistributionRule> findWithLockByOrganizationIdAndPlatformAndPageIdAndCampaignIdAndAdsetIdAndAdId(
            UUID organizationId,
            IntegrationPlatform platform,
            String pageId,
            String campaignId,
            String adsetId,
            String adId
    );

    List<LeadDistributionRule> findByOrganizationIdAndPlatform(UUID organizationId, IntegrationPlatform platform);
}

