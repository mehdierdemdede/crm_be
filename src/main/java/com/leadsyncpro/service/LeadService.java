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
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${app.system-user-id:00000000-0000-0000-0000-000000000000}")
    private UUID SYSTEM_USER_ID;

    private final LeadRepository leadRepository;
    private final CampaignRepository campaignRepository;
    private final UserRepository userRepository;
    private final LeadStatusLogRepository leadStatusLogRepository;

    public LeadService(LeadRepository leadRepository,
                       CampaignRepository campaignRepository,
                       UserRepository userRepository,
                       LeadStatusLogRepository leadStatusLogRepository) {
        this.leadRepository = leadRepository;
        this.campaignRepository = campaignRepository;
        this.userRepository = userRepository;
        this.leadStatusLogRepository = leadStatusLogRepository;
    }

    // ───────────────────────────────
    // CREATE LEAD
    // ───────────────────────────────
    @Transactional
    public Lead createLead(UUID organizationId, LeadCreateRequest request) {
        Lead lead = new Lead();
        lead.setOrganizationId(organizationId);
        lead.setName(request.getName());
        lead.setPhone(request.getPhone());
        lead.setEmail(request.getEmail());
        lead.setLanguage(request.getLanguage());
        lead.setNotes(request.getNotes());
        lead.setStatus(LeadStatus.valueOf(request.getStatus().toUpperCase()));

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

        Lead saved = leadRepository.save(lead);
        logger.info("Yeni lead oluşturuldu: {} ({})", saved.getId(), saved.getName());
        return saved;
    }

    // ───────────────────────────────
    // UPDATE LEAD
    // ───────────────────────────────
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
        } else if (request.isClearCampaign()) {
            lead.setCampaign(null);
        }

        if (request.getAssignedToUserId() != null) {
            User assignedUser = userRepository.findById(request.getAssignedToUserId())
                    .filter(u -> u.getOrganizationId().equals(organizationId))
                    .orElseThrow(() -> new ResourceNotFoundException("Assigned user not found or access denied."));
            lead.setAssignedToUser(assignedUser);
        } else if (request.isClearAssignedUser()) {
            lead.setAssignedToUser(null);
        }

        return leadRepository.save(lead);
    }

    // ───────────────────────────────
    // DELETE LEAD
    // ───────────────────────────────
    @Transactional
    public void deleteLead(UUID leadId, UUID organizationId) {
        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new ResourceNotFoundException("Lead not found with ID: " + leadId));

        if (!lead.getOrganizationId().equals(organizationId)) {
            throw new SecurityException("Access denied: Lead does not belong to this organization.");
        }

        leadRepository.delete(lead);
        logger.info("Lead silindi: {} ({})", leadId, lead.getName());
    }

    // ───────────────────────────────
    // GET LEADS
    // ───────────────────────────────
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
                    .orElse(null);
        }

        LeadStatus leadStatus = null;
        if (status != null && !status.isEmpty()) {
            try {
                leadStatus = LeadStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }

        return leadRepository.findByOrganizationIdAndFilters(organizationId, campaignId, leadStatus, assignedToUserId);
    }

    // ───────────────────────────────
    // UPDATE LEAD STATUS
    // ───────────────────────────────
    @Transactional
    public Lead updateLeadStatus(UUID leadId, LeadStatus newStatus, UUID userId) {
        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new ResourceNotFoundException("Lead bulunamadı: " + leadId));

        LeadStatus oldStatus = lead.getStatus();
        if (oldStatus == newStatus) {
            logger.info("Lead {} zaten {} durumunda, güncellenmedi.", leadId, newStatus);
            return lead;
        }

        lead.setStatus(newStatus);
        lead.setUpdatedAt(Instant.now());
        leadRepository.save(lead);

        LeadStatusLog log = LeadStatusLog.builder()
                .leadId(leadId)
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .changedBy(userId)
                .build();

        leadStatusLogRepository.save(log);
        logger.info("Lead {} durumu değişti: {} → {}", leadId, oldStatus, newStatus);

        return lead;
    }

    // ───────────────────────────────
    // ASSIGN LEAD TO USER
    // ───────────────────────────────
    @Transactional
    public Lead assignLead(UUID leadId, UUID userId, UUID organizationId) {
        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new ResourceNotFoundException("Lead not found: " + leadId));

        if (!lead.getOrganizationId().equals(organizationId)) {
            throw new SecurityException("Access denied: Lead does not belong to this organization.");
        }

        User user = null;
        if (userId != null) {
            user = userRepository.findById(userId)
                    .filter(u -> u.getOrganizationId().equals(organizationId))
                    .orElseThrow(() -> new ResourceNotFoundException("User not found or access denied."));
        }

        lead.setAssignedToUser(user);
        lead.setUpdatedAt(Instant.now());
        Lead updated = leadRepository.save(lead);

        logger.info("Lead {} kullanıcıya atandı: {}", leadId, (user != null ? user.getEmail() : "unassigned"));
        return updated;
    }

    // ───────────────────────────────
    // BULK ASSIGN LEADS
    // ───────────────────────────────
    @Transactional
    public void bulkAssign(List<UUID> leadIds, UUID userId, UUID organizationId) {
        User user = userRepository.findById(userId)
                .filter(u -> u.getOrganizationId().equals(organizationId))
                .orElseThrow(() -> new ResourceNotFoundException("User not found or access denied."));

        List<Lead> leads = leadRepository.findAllById(leadIds);
        for (Lead lead : leads) {
            if (!lead.getOrganizationId().equals(organizationId)) continue;
            lead.setAssignedToUser(user);
            lead.setUpdatedAt(Instant.now());
        }

        leadRepository.saveAll(leads);
        logger.info("{} lead {} kullanıcısına atandı.", leads.size(), user.getEmail());
    }

    // ───────────────────────────────
    // AUTOMATION RULES (SCHEDULED)
    // ───────────────────────────────
    @Scheduled(cron = "0 0 3 * * *") // her gece 03:00
    @Transactional
    public void autoUpdateLeadStatuses() {
        Instant now = Instant.now();

        Instant sevenDaysAgo = now.minus(7, ChronoUnit.DAYS);
        List<Lead> oldProposals =
                leadRepository.findAllByStatusAndUpdatedAtBefore(LeadStatus.PROPOSAL_SENT, sevenDaysAgo);
        for (Lead lead : oldProposals) {
            updateLeadStatus(lead.getId(), LeadStatus.CLOSED_LOST, SYSTEM_USER_ID);
        }

        Instant threeDaysAgo = now.minus(3, ChronoUnit.DAYS);
        List<Lead> staleContacted =
                leadRepository.findAllByStatusAndUpdatedAtBefore(LeadStatus.CONTACTED, threeDaysAgo);
        for (Lead lead : staleContacted) {
            updateLeadStatus(lead.getId(), LeadStatus.NEW, SYSTEM_USER_ID);
        }

        Instant tenDaysAgo = now.minus(10, ChronoUnit.DAYS);
        List<Lead> inactiveQualified =
                leadRepository.findAllByStatusAndUpdatedAtBefore(LeadStatus.QUALIFIED, tenDaysAgo);
        for (Lead lead : inactiveQualified) {
            updateLeadStatus(lead.getId(), LeadStatus.CLOSED_LOST, SYSTEM_USER_ID);
        }

        logger.info("Otomatik statü güncelleme tamamlandı (SYSTEM_USER_ID={}): {} teklif, {} contacted, {} qualified güncellendi.",
                SYSTEM_USER_ID, oldProposals.size(), staleContacted.size(), inactiveQualified.size());
    }
}
