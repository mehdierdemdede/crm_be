package com.leadsyncpro.service;

import com.leadsyncpro.dto.LeadActionRequest;
import com.leadsyncpro.dto.LeadActionResponse;
import com.leadsyncpro.exception.ResourceNotFoundException;
import com.leadsyncpro.model.Lead;
import com.leadsyncpro.model.LeadAction;
import com.leadsyncpro.repository.LeadActionRepository;
import com.leadsyncpro.repository.LeadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class LeadActionService {

    private final LeadActionRepository leadActionRepository;
    private final LeadRepository leadRepository;

    public LeadActionService(LeadActionRepository leadActionRepository,
                             LeadRepository leadRepository) {
        this.leadActionRepository = leadActionRepository;
        this.leadRepository = leadRepository;
    }

    public List<LeadActionResponse> getActionsForLead(UUID leadId, UUID orgId) {
        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new ResourceNotFoundException("Lead not found: " + leadId));

        if (!lead.getOrganizationId().equals(orgId)) {
            throw new SecurityException("Access denied: lead not in your organization");
        }

        return leadActionRepository.findByLeadIdOrderByCreatedAtDesc(leadId).stream()
                .map(a -> new LeadActionResponse(a.getId(), a.getLeadId(), a.getUserId(), a.getActionType(), a.getMessage(), a.getCreatedAt()))
                .collect(Collectors.toList());
    }

    @Transactional
    public LeadActionResponse createActionForLead(UUID leadId, UUID orgId, UUID userId, LeadActionRequest req) {
        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new ResourceNotFoundException("Lead not found: " + leadId));

        if (!lead.getOrganizationId().equals(orgId)) {
            throw new SecurityException("Access denied: lead not in your organization");
        }

        LeadAction action = LeadAction.builder()
                .leadId(leadId)
                .userId(userId)
                .actionType(req.getActionType())
                .message(req.getMessage())
                .build();

        LeadAction saved = leadActionRepository.save(action);

        return new LeadActionResponse(saved.getId(), saved.getLeadId(), saved.getUserId(), saved.getActionType(), saved.getMessage(), saved.getCreatedAt());
    }

}
