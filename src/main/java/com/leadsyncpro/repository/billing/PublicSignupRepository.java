package com.leadsyncpro.repository.billing;

import com.leadsyncpro.model.billing.PublicSignup;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublicSignupRepository extends JpaRepository<PublicSignup, UUID> {

    Optional<PublicSignup> findTopByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);
}
