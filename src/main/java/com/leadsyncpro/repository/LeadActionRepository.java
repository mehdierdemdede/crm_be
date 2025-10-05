package com.leadsyncpro.repository;

import com.leadsyncpro.model.LeadAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LeadActionRepository extends JpaRepository<LeadAction, UUID> {
    List<LeadAction> findByLeadIdOrderByCreatedAtDesc(UUID leadId);
}
