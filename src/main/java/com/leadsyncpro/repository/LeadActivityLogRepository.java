package com.leadsyncpro.repository;

import com.leadsyncpro.model.LeadActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface LeadActivityLogRepository extends JpaRepository<LeadActivityLog, UUID> {
    List<LeadActivityLog> findByLead_IdOrderByCreatedAtDesc(UUID leadId);
}
