package com.leadsyncpro.service;

import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationService {

    private final com.leadsyncpro.repository.OrganizationRepository organizationRepository;
    private final int defaultMemberLimit;

    public OrganizationService(
            com.leadsyncpro.repository.OrganizationRepository organizationRepository,
            @Value("${organization.default-member-limit:10}") int defaultMemberLimit) {
        this.organizationRepository = Objects.requireNonNull(organizationRepository,
                "organizationRepository must not be null");
        this.defaultMemberLimit = defaultMemberLimit;
    }

    @Transactional(readOnly = true)
    public java.util.List<com.leadsyncpro.model.Organization> getAllOrganizations() {
        return organizationRepository.findAll();
    }

    @Transactional(readOnly = true)
    public int getMemberLimit(UUID organizationId) {
        // Manual implementation: unlimited or default limit for now
        return defaultMemberLimit > 0 ? defaultMemberLimit : 100;
    }

    @Transactional(readOnly = true)
    public void ensureWithinUserLimit(UUID organizationId, long desiredUserCount) {
        if (organizationId == null) {
            throw new IllegalArgumentException("organizationId is required");
        }
        int memberLimit = getMemberLimit(organizationId);
        // If we want to enforce limit
        if (memberLimit > 0 && desiredUserCount > memberLimit) {
            throw new IllegalArgumentException(
                    "Organization has reached the maximum number of users allowed ("
                            + memberLimit
                            + ")");
        }
    }
}
