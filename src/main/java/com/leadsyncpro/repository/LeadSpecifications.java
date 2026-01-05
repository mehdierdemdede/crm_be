package com.leadsyncpro.repository;

import com.leadsyncpro.model.Campaign;
import com.leadsyncpro.model.Lead;
import com.leadsyncpro.model.LeadStatus;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.UUID;

public final class LeadSpecifications {

    private LeadSpecifications() {
    }

    public static Specification<Lead> belongsToOrganization(UUID organizationId) {
        return (root, query, cb) -> cb.equal(root.get("organizationId"), organizationId);
    }

    public static Specification<Lead> matchesSearch(String search) {
        if (!StringUtils.hasText(search)) {
            return null;
        }

        String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";

        return (root, query, cb) -> {
            query.distinct(true);
            Join<Lead, Campaign> campaignJoin = root.join("campaign", JoinType.LEFT);
            return cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("email")), pattern),
                    cb.like(cb.lower(campaignJoin.get("name")), pattern));
        };
    }

    public static Specification<Lead> hasStatus(LeadStatus status) {
        if (status == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Lead> hasLanguage(String language) {
        if (!StringUtils.hasText(language)) {
            return null;
        }

        String languageLower = language.trim().toLowerCase(Locale.ROOT);
        return (root, query, cb) -> cb.equal(cb.lower(root.get("language")), languageLower);
    }

    public static Specification<Lead> hasCampaignId(UUID campaignId) {
        if (campaignId == null) {
            return null;
        }

        return (root, query, cb) -> {
            query.distinct(true);
            return cb.equal(root.join("campaign", JoinType.LEFT).get("id"), campaignId);
        };
    }

    public static Specification<Lead> hasAssignedUser(UUID assignedUserId) {
        if (assignedUserId == null) {
            return null;
        }

        return (root, query, cb) -> {
            query.distinct(true);
            return cb.equal(root.join("assignedToUser", JoinType.LEFT).get("id"), assignedUserId);
        };
    }

    public static Specification<Lead> isUnassigned() {
        return (root, query, cb) -> cb.isNull(root.get("assignedToUser"));
    }

    public static Specification<Lead> createdBetween(java.time.Instant start, java.time.Instant end) {
        return (root, query, cb) -> {
            if (start != null && end != null) {
                return cb.between(root.get("createdAt"), start, end);
            } else if (start != null) {
                return cb.greaterThanOrEqualTo(root.get("createdAt"), start);
            } else if (end != null) {
                return cb.lessThanOrEqualTo(root.get("createdAt"), end);
            }
            return null;
        };
    }
}
