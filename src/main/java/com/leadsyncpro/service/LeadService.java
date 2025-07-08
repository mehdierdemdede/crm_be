package com.leadsyncpro.service;

import com.leadsyncpro.dto.LeadCreateRequest;
import com.leadsyncpro.dto.LeadUpdateRequest;
import com.leadsyncpro.exception.ResourceNotFoundException;
import com.leadsyncpro.model.Campaign;
import com.leadsyncpro.model.Lead;
import com.leadsyncpro.model.LeadStatus;
import com.leadsyncpro.model.User;
import com.leadsyncpro.repository.CampaignRepository;
import com.leadsyncpro.repository.LeadRepository;
import com.leadsyncpro.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class LeadService {

    private final LeadRepository leadRepository;
    private final CampaignRepository campaignRepository;
    private final UserRepository userRepository;

    public LeadService(LeadRepository leadRepository, CampaignRepository campaignRepository, UserRepository userRepository) {
        this.leadRepository = leadRepository;
        this.campaignRepository = campaignRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Lead createLead(UUID organizationId, LeadCreateRequest request) {
        Lead lead = new Lead();
        lead.setOrganizationId(organizationId);
        lead.setName(request.getName());
        lead.setPhone(request.getPhone());
        lead.setEmail(request.getEmail());
        lead.setLanguage(request.getLanguage());
        lead.setNotes(request.getNotes());
        lead.setStatus(LeadStatus.valueOf(request.getStatus().toUpperCase())); // Convert string to enum

        if (request.getCampaignId() != null) {
            Campaign campaign = campaignRepository.findById(request.getCampaignId())
                    .filter(c -> c.getOrganizationId().equals(organizationId))
                    .orElseThrow(() -> new ResourceNotFoundException("Campaign not found or access denied."));
            lead.setCampaign(campaign);
        }

        if (request.getAssignedToUserId() != null) {
            User assignedUser = userRepository.findById(request.getAssignedToUserId())
                    .filter(u -> u.getOrganizationId().equals(organizationId))
                    .orElseThrow(() -> new ResourceNotFoundException("Assigned user not found or access denied."));
            lead.setAssignedToUser(assignedUser);
        }

        return leadRepository.save(lead);
    }

    @Transactional
    public Lead updateLead(UUID leadId, UUID organizationId, LeadUpdateRequest request) {
        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new ResourceNotFoundException("Lead not found with ID: " + leadId));

        if (!lead.getOrganizationId().equals(organizationId)) {
            throw new SecurityException("Access denied: Lead does not belong to this organization.");
        }

        if (request.getName() != null) lead.setName(request.getName());
        if (request.getPhone() != null) lead.setPhone(request.getPhone());
        if (request.getEmail() != null) lead.setEmail(request.getEmail());
        if (request.getLanguage() != null) lead.setLanguage(request.getLanguage());
        if (request.getNotes() != null) lead.setNotes(request.getNotes());

        if (request.getStatus() != null) {
            lead.setStatus(LeadStatus.valueOf(request.getStatus().toUpperCase()));
        }

        if (request.getCampaignId() != null) {
            Campaign campaign = campaignRepository.findById(request.getCampaignId())
                    .filter(c -> c.getOrganizationId().equals(organizationId))
                    .orElseThrow(() -> new ResourceNotFoundException("Campaign not found or access denied."));
            lead.setCampaign(campaign);
        } else if (request.isClearCampaign()) { // Allow clearing campaign
            lead.setCampaign(null);
        }

        if (request.getAssignedToUserId() != null) {
            User assignedUser = userRepository.findById(request.getAssignedToUserId())
                    .filter(u -> u.getOrganizationId().equals(organizationId))
                    .orElseThrow(() -> new ResourceNotFoundException("Assigned user not found or access denied."));
            lead.setAssignedToUser(assignedUser);
        } else if (request.isClearAssignedUser()) { // Allow clearing assigned user
            lead.setAssignedToUser(null);
        }

        return leadRepository.save(lead);
    }

    @Transactional
    public void deleteLead(UUID leadId, UUID organizationId) {
        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new ResourceNotFoundException("Lead not found with ID: " + leadId));

        if (!lead.getOrganizationId().equals(organizationId)) {
            throw new SecurityException("Access denied: Lead does not belong to this organization.");
        }

        leadRepository.delete(lead);
    }

    public Lead getLeadById(UUID leadId, UUID organizationId) {
        return leadRepository.findById(leadId)
                .filter(lead -> lead.getOrganizationId().equals(organizationId))
                .orElseThrow(() -> new ResourceNotFoundException("Lead not found or access denied."));
    }

    public List<Lead> getLeadsByOrganization(UUID organizationId, String campaignName, String status, UUID assignedToUserId) {
        UUID campaignId = null;
        if (campaignName != null && !campaignName.isEmpty()) {
            campaignId = campaignRepository.findByOrganizationIdAndName(organizationId, campaignName)
                    .map(Campaign::getId)
                    .orElse(null); // If campaign name doesn't exist, no leads for it
        }

        LeadStatus leadStatus = null;
        if (status != null && !status.isEmpty()) {
            try {
                leadStatus = LeadStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                // Handle invalid status string, perhaps log or throw a specific exception
                // For now, we'll just treat it as no status filter
            }
        }

        return leadRepository.findByOrganizationIdAndFilters(organizationId, campaignId, leadStatus, assignedToUserId);
    }
}
