package com.leadsyncpro.service;

import com.leadsyncpro.dto.LeadLogRequest;
import com.leadsyncpro.model.LeadActivityLog;
import com.leadsyncpro.repository.LeadActivityLogRepository;
import com.leadsyncpro.repository.LeadRepository;
import com.leadsyncpro.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class LeadActivityLogService {

    private final LeadRepository leadRepository;
    private final LeadActivityLogRepository leadActivityLogRepository;

    public LeadActivityLogService(LeadRepository leadRepository, LeadActivityLogRepository leadActivityLogRepository) {
        this.leadRepository = leadRepository;
        this.leadActivityLogRepository = leadActivityLogRepository;
    }

    @Transactional
    public LeadActivityLog addLog(UUID leadId, UUID userId, LeadLogRequest request) {
        var lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new ResourceNotFoundException("Lead not found"));

        LeadActivityLog log = new LeadActivityLog();
        log.setLead(lead);
        log.setUserId(userId);
        log.setAction(request.getAction());
        log.setDetails(request.getDetails());
        log.setCreatedAt(Instant.now());

        return leadActivityLogRepository.save(log);
    }

    public List<LeadActivityLog> getLogs(UUID leadId) {
        return leadActivityLogRepository.findByLead_IdOrderByCreatedAtDesc(leadId);
    }
}
