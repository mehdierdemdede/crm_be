package com.leadsyncpro.repository;

import com.leadsyncpro.model.LeadDistributionAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface LeadDistributionAssignmentRepository extends JpaRepository<LeadDistributionAssignment, UUID> {

    List<LeadDistributionAssignment> findByRuleIdOrderByPositionAsc(UUID ruleId);

    List<LeadDistributionAssignment> findByRuleIdInOrderByPositionAsc(Collection<UUID> ruleIds);

    void deleteByRuleId(UUID ruleId);
}

