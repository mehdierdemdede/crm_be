package com.leadsyncpro.service;

import com.leadsyncpro.dto.LeadActionRequest;
import com.leadsyncpro.dto.LeadActionResponse;
import com.leadsyncpro.exception.ResourceNotFoundException;
import com.leadsyncpro.model.Lead;
import com.leadsyncpro.model.LeadAction;
import com.leadsyncpro.repository.LeadActionRepository;
import com.leadsyncpro.repository.LeadRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class LeadActionService {

    private final LeadRepository leadRepository;
    private final LeadActionRepository leadActionRepository;

    public LeadActionService(LeadRepository leadRepository, LeadActionRepository leadActionRepository) {
        this.leadRepository = leadRepository;
        this.leadActionRepository = leadActionRepository;
    }

    @Transactional
    public LeadActionResponse createActionForLead(UUID leadId, UUID organizationId, UUID userId, LeadActionRequest request) {
        // Lead doğrulama
        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new ResourceNotFoundException("Lead not found."));

        if (!lead.getOrganizationId().equals(organizationId)) {
            throw new AccessDeniedException("Lead not accessible for this organization");
        }

        LeadAction action = new LeadAction();
        action.setLead(lead);
        action.setUserId(userId);
        action.setActionType(request.getActionType());
        action.setMessage(request.getMessage());
        action.setCreatedAt(Instant.now());

        LeadAction saved = leadActionRepository.save(action);
        return LeadActionResponse.fromEntity(saved);
    }


    public List<LeadActionResponse> getActionsForLead(UUID leadId, UUID organizationId) {
        List<LeadAction> actions = leadActionRepository.findByLead_IdOrderByCreatedAtDesc(leadId);

        return actions.stream()
                .filter(a -> a.getLead().getOrganizationId().equals(organizationId))
                .map(LeadActionResponse::new) // ✅ artık constructor var
                .collect(Collectors.toList());
    }
}
