package com.leadsyncpro.repository;

import com.leadsyncpro.model.Sale;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SalesRepository extends JpaRepository<Sale, UUID> {
        Optional<Sale> findTopByLead_IdAndOrganizationIdOrderByCreatedAtDesc(UUID leadId, UUID organizationId);

        Optional<Sale> findByIdAndOrganizationId(UUID saleId, UUID organizationId);

        org.springframework.data.domain.Page<Sale> findAllByOrganizationId(UUID organizationId,
                        org.springframework.data.domain.Pageable pageable);

        long countByUserIdAndCreatedAtAfter(UUID userId, java.time.Instant createdAt);

        java.util.List<Sale> findAllByLead_IdAndOrganizationIdOrderByOperationDateDesc(UUID leadId,
                        UUID organizationId);

        java.util.List<Sale> findAllByOrganizationIdAndOperationDateBetweenOrderByOperationDateAsc(UUID organizationId,
                        java.time.Instant startDate, java.time.Instant endDate);

        java.util.List<Sale> findAllByUserIdAndOrganizationIdAndOperationDateBetweenOrderByOperationDateAsc(UUID userId,
                        UUID organizationId, java.time.Instant startDate, java.time.Instant endDate);

        @org.springframework.data.jpa.repository.Query("SELECT s.currency, SUM(s.price) FROM Sale s WHERE s.organizationId = :orgId AND s.operationDate BETWEEN :start AND :end GROUP BY s.currency")
        java.util.List<Object[]> sumPriceByCurrencyBetween(
                        @org.springframework.data.repository.query.Param("orgId") UUID orgId,
                        @org.springframework.data.repository.query.Param("start") java.time.Instant start,
                        @org.springframework.data.repository.query.Param("end") java.time.Instant end);

        long countByOrganizationIdAndOperationDateBetween(UUID organizationId, java.time.Instant start,
                        java.time.Instant end);
}
