package com.leadsyncpro.service;

import com.leadsyncpro.dto.LeadLogRequest;
import com.leadsyncpro.exception.ResourceNotFoundException;
import com.leadsyncpro.model.Lead;
import com.leadsyncpro.model.LeadActivityLog;
import com.leadsyncpro.repository.LeadActivityLogRepository;
import com.leadsyncpro.repository.LeadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class LeadActivityLogService {

    private final LeadActivityLogRepository logRepository;
    private final LeadRepository leadRepository;

    public LeadActivityLogService(LeadActivityLogRepository logRepository, LeadRepository leadRepository) {
        this.logRepository = logRepository;
        this.leadRepository = leadRepository;
    }

    @Transactional
    public LeadActivityLog addLog(UUID leadId, UUID userId, LeadLogRequest req) {
        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new ResourceNotFoundException("Lead not found: " + leadId));

        LeadActivityLog log = LeadActivityLog.builder()
                .leadId(lead.getId())
                .userId(userId)
                .actionType(req.getActionType())
                .message(req.getMessage())
                .build();

        return logRepository.save(log);
    }

    @Transactional(readOnly = true)
    public List<LeadActivityLog> getLogs(UUID leadId) {
        return logRepository.findByLeadIdOrderByCreatedAtDesc(leadId);
    }
}
