package com.leadsyncpro.repository;

import com.leadsyncpro.model.Sale;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SalesRepository extends JpaRepository<Sale, UUID> {
    Optional<Sale> findTopByLead_IdAndOrganizationIdOrderByCreatedAtDesc(UUID leadId, UUID organizationId);
    Optional<Sale> findByIdAndOrganizationId(UUID saleId, UUID organizationId);
}
