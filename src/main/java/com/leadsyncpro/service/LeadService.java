package com.leadsyncpro.service;

import com.leadsyncpro.dto.LeadCreateRequest;
import com.leadsyncpro.dto.LeadUpdateRequest;
import com.leadsyncpro.exception.ResourceNotFoundException;
import com.leadsyncpro.model.*;
import com.leadsyncpro.repository.CampaignRepository;
import com.leadsyncpro.repository.LeadRepository;
import com.leadsyncpro.repository.LeadStatusLogRepository;
import com.leadsyncpro.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;


@Service
public class LeadService {

    private static final Logger logger = LoggerFactory.getLogger(LeadService.class);

    private final LeadRepository leadRepository;
    private final CampaignRepository campaignRepository;
    private final UserRepository userRepository;
    private final LeadStatusLogRepository leadStatusLogRepository;

    public LeadService(LeadRepository leadRepository, CampaignRepository campaignRepository, UserRepository userRepository, LeadStatusLogRepository leadStatusLogRepository) {
        this.leadRepository = leadRepository;
        this.campaignRepository = campaignRepository;
        this.userRepository = userRepository;
        this.leadStatusLogRepository = leadStatusLogRepository;
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

    @Transactional
    public Lead updateLeadStatus(UUID leadId, LeadStatus newStatus, UUID userId) {
        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new ResourceNotFoundException("Lead not found with ID: " + leadId));

        LeadStatus oldStatus = lead.getStatus();
        if (oldStatus == newStatus) return lead; // değişim yoksa loglama gerekmez

        lead.setStatus(newStatus);
        lead.setUpdatedAt(Instant.now());
        leadRepository.save(lead);

        // Log kaydı
        LeadStatusLog log = LeadStatusLog.builder()
                .leadId(leadId)
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .changedBy(userId)
                .build();

        leadStatusLogRepository.save(log);

        logger.info("Lead {} status changed: {} → {}", leadId, oldStatus, newStatus);

        return lead;
    }

    @Scheduled(cron = "0 0 2 * * *") // her gece 02:00'de
    @Transactional
    public void autoCloseLeads() {
        Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        List<Lead> oldProposals = leadRepository.findAllByStatusAndUpdatedAtBefore(LeadStatus.PROPOSAL_SENT, sevenDaysAgo);

        for (Lead lead : oldProposals) {
            updateLeadStatus(lead.getId(), LeadStatus.CLOSED_LOST, UUID.randomUUID()); // system user gibi davran
        }
    }
}
