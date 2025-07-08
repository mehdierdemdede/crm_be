package com.leadsyncpro.service;

import com.leadsyncpro.exception.ResourceNotFoundException;
import com.leadsyncpro.model.Campaign;
import com.leadsyncpro.repository.CampaignRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CampaignService {

    private final CampaignRepository campaignRepository;

    public CampaignService(CampaignRepository campaignRepository) {
        this.campaignRepository = campaignRepository;
    }

    @Transactional
    public Campaign createCampaign(UUID organizationId, String name, String description) {
        if (campaignRepository.existsByOrganizationIdAndName(organizationId, name)) {
            throw new IllegalArgumentException("Campaign with this name already exists in this organization.");
        }
        Campaign campaign = new Campaign();
        campaign.setOrganizationId(organizationId);
        campaign.setName(name);
        campaign.setDescription(description);
        return campaignRepository.save(campaign);
    }

    @Transactional
    public Campaign updateCampaign(UUID campaignId, UUID organizationId, String name, String description) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found with ID: " + campaignId));

        if (!campaign.getOrganizationId().equals(organizationId)) {
            throw new SecurityException("Access denied: Campaign does not belong to this organization.");
        }

        // Check for name uniqueness if name is changed
        if (name != null && !name.equals(campaign.getName())) {
            if (campaignRepository.findByOrganizationIdAndName(organizationId, name).isPresent()) {
                throw new IllegalArgumentException("Another campaign with this name already exists in this organization.");
            }
        }

        if (name != null) campaign.setName(name);
        if (description != null) campaign.setDescription(description);

        return campaignRepository.save(campaign);
    }

    @Transactional
    public void deleteCampaign(UUID campaignId, UUID organizationId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found with ID: " + campaignId));

        if (!campaign.getOrganizationId().equals(organizationId)) {
            throw new SecurityException("Access denied: Campaign does not belong to this organization.");
        }

        campaignRepository.delete(campaign);
    }

    public List<Campaign> getCampaignsByOrganization(UUID organizationId) {
        return campaignRepository.findByOrganizationId(organizationId);
    }

    public Campaign getCampaignById(UUID campaignId, UUID organizationId) {
        return campaignRepository.findById(campaignId)
                .filter(campaign -> campaign.getOrganizationId().equals(organizationId))
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found or access denied."));
    }
}
