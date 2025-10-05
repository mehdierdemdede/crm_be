package com.leadsyncpro.repository;

import com.leadsyncpro.model.LeadActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LeadActivityLogRepository extends JpaRepository<LeadActivityLog, UUID> {

    List<LeadActivityLog> findByLeadIdOrderByCreatedAtDesc(UUID leadId);
}
