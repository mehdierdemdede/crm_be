package com.leadsyncpro.service;

import com.leadsyncpro.model.billing.Price;
import com.leadsyncpro.model.billing.PublicSignup;
import com.leadsyncpro.repository.billing.PublicSignupRepository;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationService {

    private final PublicSignupRepository publicSignupRepository;
    private final int defaultMemberLimit;

    public OrganizationService(
            PublicSignupRepository publicSignupRepository,
            @Value("${organization.default-member-limit:10}") int defaultMemberLimit) {
        this.publicSignupRepository =
                Objects.requireNonNull(publicSignupRepository, "publicSignupRepository must not be null");
        this.defaultMemberLimit = defaultMemberLimit;
    }

    @Transactional(readOnly = true)
    public int getMemberLimit(UUID organizationId) {
        if (organizationId == null) {
            return defaultMemberLimit;
        }
        Optional<PublicSignup> latestSignup =
                publicSignupRepository.findTopByOrganizationIdOrderByCreatedAtDesc(organizationId);
        return latestSignup
                .map(signup -> {
                    Price price = signup.getPrice();
                    Integer seatLimit = price != null ? price.getSeatLimit() : null;
                    if (seatLimit != null && seatLimit > 0) {
                        return seatLimit;
                    }
                    Integer seatCount = signup.getSeatCount();
                    if (seatCount != null && seatCount > 0) {
                        return seatCount;
                    }
                    return defaultMemberLimit;
                })
                .orElse(defaultMemberLimit);
    }

    @Transactional(readOnly = true)
    public void ensureWithinUserLimit(UUID organizationId, long desiredUserCount) {
        if (organizationId == null) {
            throw new IllegalArgumentException("organizationId is required");
        }
        int memberLimit = getMemberLimit(organizationId);
        if (memberLimit > 0 && desiredUserCount > memberLimit) {
            throw new IllegalArgumentException(
                    "Organization has reached the maximum number of users allowed by the selected plan ("
                            + memberLimit
                            + ")");
        }
    }
}
