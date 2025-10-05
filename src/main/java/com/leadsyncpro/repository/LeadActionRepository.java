package com.leadsyncpro.repository;

import com.leadsyncpro.model.LeadAction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface LeadActionRepository extends JpaRepository<LeadAction, UUID> {
    List<LeadAction> findByLead_IdOrderByCreatedAtDesc(UUID leadId);
}
