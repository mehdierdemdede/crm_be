package com.leadsyncpro.repository;

import com.leadsyncpro.model.LeadStatusLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LeadStatusLogRepository extends JpaRepository<LeadStatusLog, UUID> {
    List<LeadStatusLog> findByLeadIdOrderByCreatedAtDesc(UUID leadId);
}
