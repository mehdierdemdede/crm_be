package com.leadsyncpro.service;

import com.leadsyncpro.dto.*;
import com.leadsyncpro.exception.ResourceNotFoundException;
import com.leadsyncpro.model.*;
import com.leadsyncpro.repository.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LeadDistributionService {

    private static final Logger logger = LoggerFactory.getLogger(LeadDistributionService.class);

    private final LeadDistributionRuleRepository ruleRepository;
    private final LeadDistributionAssignmentRepository assignmentRepository;
    private final LeadRepository leadRepository;
    private final UserRepository userRepository;
    private final IntegrationConfigRepository integrationConfigRepository;
    private final EncryptionService encryptionService;

    private final RestTemplate restTemplate = new RestTemplate();

    @Transactional
    public LeadDistributionRuleResponse upsertFacebookRule(UUID organizationId, LeadDistributionRuleRequest request) {
        if (organizationId == null) {
            throw new IllegalArgumentException("Organization id is required");
        }
        if (request.getAdId() == null || request.getAdsetId() == null || request.getCampaignId() == null || request.getPageId() == null) {
            throw new IllegalArgumentException("Facebook rule requires page, campaign, ad set and ad identifiers");
        }

        LeadDistributionRule rule = ruleRepository
                .findByOrganizationIdAndPlatformAndPageIdAndCampaignIdAndAdsetIdAndAdId(
                        organizationId,
                        IntegrationPlatform.FACEBOOK,
                        request.getPageId(),
                        request.getCampaignId(),
                        request.getAdsetId(),
                        request.getAdId())
                .orElseGet(() -> LeadDistributionRule.builder()
                        .organizationId(organizationId)
                        .platform(IntegrationPlatform.FACEBOOK)
                        .pageId(request.getPageId())
                        .campaignId(request.getCampaignId())
                        .adsetId(request.getAdsetId())
                        .adId(request.getAdId())
                        .build());

        if (request.getPageName() != null && !request.getPageName().isBlank()) {
            rule.setPageName(request.getPageName());
        }
        if (request.getCampaignName() != null && !request.getCampaignName().isBlank()) {
            rule.setCampaignName(request.getCampaignName());
        }
        if (request.getAdsetName() != null && !request.getAdsetName().isBlank()) {
            rule.setAdsetName(request.getAdsetName());
        }
        if (request.getAdName() != null && !request.getAdName().isBlank()) {
            rule.setAdName(request.getAdName());
        }
        rule.setCurrentIndex(0);
        rule.setCurrentCount(0);
        rule = ruleRepository.save(rule);

        assignmentRepository.deleteByRuleId(rule.getId());

        if (!CollectionUtils.isEmpty(request.getAssignments())) {
            for (LeadDistributionAssignmentRequest assignmentRequest : request.getAssignments()) {
                if (assignmentRequest.getUserId() == null) {
                    continue;
                }
                User user = userRepository.findById(assignmentRequest.getUserId())
                        .orElseThrow(() -> new ResourceNotFoundException("User not found: " + assignmentRequest.getUserId()));
                LeadDistributionAssignment assignment = LeadDistributionAssignment.builder()
                        .rule(rule)
                        .user(user)
                        .frequency(Math.max(0, Optional.ofNullable(assignmentRequest.getFrequency()).orElse(0)))
                        .position(Optional.ofNullable(assignmentRequest.getPosition()).orElse(0))
                        .build();
                assignmentRepository.save(assignment);
            }
        }

        return toResponse(rule, assignmentRepository.findByRuleIdOrderByPositionAsc(rule.getId()));
    }

    @Transactional(readOnly = true)
    public List<LeadDistributionRuleResponse> listFacebookRules(UUID organizationId) {
        if (organizationId == null) {
            return List.of();
        }
        List<LeadDistributionRule> rules = ruleRepository.findByOrganizationIdAndPlatform(organizationId, IntegrationPlatform.FACEBOOK);
        List<UUID> ruleIds = rules.stream().map(LeadDistributionRule::getId).toList();
        Map<UUID, List<LeadDistributionAssignment>> assignments = ruleIds.isEmpty()
                ? Map.of()
                : assignmentRepository
                .findByRuleIdInOrderByPositionAsc(ruleIds)
                .stream()
                .collect(Collectors.groupingBy(a -> a.getRule().getId()));

        return rules.stream()
                .map(rule -> toResponse(rule, assignments.getOrDefault(rule.getId(), List.of())))
                .toList();
    }

    @Transactional
    public void deleteRule(UUID organizationId, UUID ruleId) {
        LeadDistributionRule rule = ruleRepository.findById(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException("Distribution rule not found"));
        if (!rule.getOrganizationId().equals(organizationId)) {
            throw new IllegalArgumentException("Rule does not belong to organization");
        }
        assignmentRepository.deleteByRuleId(ruleId);
        ruleRepository.delete(rule);
    }

    @Transactional
    public Optional<User> assignLeadByRule(Lead lead) {
        if (lead == null || lead.getOrganizationId() == null) {
            return Optional.empty();
        }
        if (lead.getPlatform() != IntegrationPlatform.FACEBOOK) {
            return Optional.empty();
        }
        if (lead.getPageId() == null || lead.getFbCampaignId() == null || lead.getAdsetId() == null || lead.getAdId() == null) {
            return Optional.empty();
        }

        Optional<LeadDistributionRule> ruleOpt = ruleRepository
                .findWithLockByOrganizationIdAndPlatformAndPageIdAndCampaignIdAndAdsetIdAndAdId(
                        lead.getOrganizationId(),
                        IntegrationPlatform.FACEBOOK,
                        lead.getPageId(),
                        lead.getFbCampaignId(),
                        lead.getAdsetId(),
                        lead.getAdId());

        if (ruleOpt.isEmpty()) {
            return Optional.empty();
        }

        LeadDistributionRule rule = ruleOpt.get();
        List<LeadDistributionAssignment> assignments = assignmentRepository.findByRuleIdOrderByPositionAsc(rule.getId());
        List<LeadDistributionAssignment> eligibleAssignments = assignments.stream()
                .filter(a -> a.getFrequency() != null && a.getFrequency() > 0)
                .filter(a -> a.getUser() != null && a.getUser().isActive() && a.getUser().isAutoAssignEnabled())
                .toList();

        if (eligibleAssignments.isEmpty()) {
            logger.warn("Lead distribution rule {} has no eligible users", rule.getId());
            rule.setCurrentIndex(0);
            rule.setCurrentCount(0);
            ruleRepository.save(rule);
            return Optional.empty();
        }

        int index = Optional.ofNullable(rule.getCurrentIndex()).orElse(0);
        int count = Optional.ofNullable(rule.getCurrentCount()).orElse(0);
        if (index >= eligibleAssignments.size()) {
            index = 0;
            count = 0;
        }

        LeadDistributionAssignment currentAssignment = eligibleAssignments.get(index);
        if (count >= currentAssignment.getFrequency()) {
            count = 0;
            index = (index + 1) % eligibleAssignments.size();
            currentAssignment = eligibleAssignments.get(index);
        }

        User selectedUser = currentAssignment.getUser();
        lead.setAssignedToUser(selectedUser);

        count++;
        if (count >= currentAssignment.getFrequency()) {
            count = 0;
            index = (index + 1) % eligibleAssignments.size();
        }

        rule.setCurrentIndex(index);
        rule.setCurrentCount(count);
        ruleRepository.save(rule);

        return Optional.of(selectedUser);
    }

    @Transactional(readOnly = true)
    public FacebookAdHierarchyResponse getFacebookHierarchy(UUID organizationId) {
        if (organizationId == null) {
            return FacebookAdHierarchyResponse.builder().build();
        }

        List<LeadDistributionRule> rules = ruleRepository.findByOrganizationIdAndPlatform(organizationId, IntegrationPlatform.FACEBOOK);
        List<UUID> ruleIds = rules.stream().map(LeadDistributionRule::getId).toList();
        Map<UUID, List<LeadDistributionAssignment>> assignments = ruleIds.isEmpty()
                ? Map.of()
                : assignmentRepository
                .findByRuleIdInOrderByPositionAsc(ruleIds)
                .stream()
                .collect(Collectors.groupingBy(a -> a.getRule().getId()));

        Map<String, LeadDistributionRule> ruleKeyMap = rules.stream()
                .collect(Collectors.toMap(this::ruleKey, r -> r));

        Map<String, LeadDistributionRuleResponse> ruleResponses = new HashMap<>();
        for (LeadDistributionRule rule : rules) {
            ruleResponses.put(ruleKey(rule), toResponse(rule, assignments.getOrDefault(rule.getId(), List.of())));
        }

        List<Object[]> leadHierarchyRows = leadRepository.findDistinctFacebookHierarchy(organizationId, IntegrationPlatform.FACEBOOK);

        Set<String> pageIds = new HashSet<>();
        for (Object[] row : leadHierarchyRows) {
            if (row[0] != null) {
                pageIds.add((String) row[0]);
            }
        }
        for (LeadDistributionRule rule : rules) {
            if (rule.getPageId() != null) {
                pageIds.add(rule.getPageId());
            }
        }

        Map<String, String> pageNames = fetchPageNames(organizationId, pageIds);

        Map<String, PageAggregate> aggregates = new LinkedHashMap<>();

        for (Object[] row : leadHierarchyRows) {
            String pageId = (String) row[0];
            String campaignId = (String) row[1];
            String campaignName = (String) row[2];
            String adsetId = (String) row[3];
            String adsetName = (String) row[4];
            String adId = (String) row[5];
            String adName = (String) row[6];

            if (pageId == null || campaignId == null || adsetId == null || adId == null) {
                continue;
            }

            PageAggregate pageAggregate = aggregates.computeIfAbsent(pageId, PageAggregate::new);
            pageAggregate.pageName = firstNonNull(pageAggregate.pageName, resolveName(pageNames, ruleKeyMap, pageId));

            CampaignAggregate campaignAggregate = pageAggregate.campaigns
                    .computeIfAbsent(campaignId, CampaignAggregate::new);
            campaignAggregate.campaignName = firstNonNull(campaignAggregate.campaignName,
                    resolveCampaignName(ruleKeyMap, pageId, campaignId, campaignName));

            AdsetAggregate adsetAggregate = campaignAggregate.adsets
                    .computeIfAbsent(adsetId, AdsetAggregate::new);
            adsetAggregate.adsetName = firstNonNull(adsetAggregate.adsetName,
                    resolveAdsetName(ruleKeyMap, pageId, campaignId, adsetId, adsetName));

            String key = String.join("|", pageId, campaignId, adsetId, adId);
            LeadDistributionRuleResponse ruleResponse = ruleResponses.get(key);
            if (ruleResponse == null && ruleKeyMap.containsKey(key)) {
                ruleResponse = toResponse(ruleKeyMap.get(key), assignments.getOrDefault(ruleKeyMap.get(key).getId(), List.of()));
                ruleResponses.put(key, ruleResponse);
            }

            AdAggregate adAggregate = adsetAggregate.ads.computeIfAbsent(adId, AdAggregate::new);
            adAggregate.adName = firstNonNull(adAggregate.adName,
                    resolveAdName(ruleKeyMap, pageId, campaignId, adsetId, adId, adName));
            adAggregate.rule = ruleResponse;
        }

        for (LeadDistributionRule rule : rules) {
            String key = ruleKey(rule);
            LeadDistributionRuleResponse ruleResponse = ruleResponses.computeIfAbsent(key,
                    k -> toResponse(rule, assignments.getOrDefault(rule.getId(), List.of())));

            PageAggregate pageAggregate = aggregates.computeIfAbsent(rule.getPageId(), PageAggregate::new);
            pageAggregate.pageName = firstNonNull(pageAggregate.pageName,
                    firstNonNull(rule.getPageName(), pageNames.get(rule.getPageId())));

            CampaignAggregate campaignAggregate = pageAggregate.campaigns
                    .computeIfAbsent(rule.getCampaignId(), CampaignAggregate::new);
            campaignAggregate.campaignName = firstNonNull(campaignAggregate.campaignName, rule.getCampaignName());

            AdsetAggregate adsetAggregate = campaignAggregate.adsets
                    .computeIfAbsent(rule.getAdsetId(), AdsetAggregate::new);
            adsetAggregate.adsetName = firstNonNull(adsetAggregate.adsetName, rule.getAdsetName());

            AdAggregate adAggregate = adsetAggregate.ads.computeIfAbsent(rule.getAdId(), AdAggregate::new);
            adAggregate.adName = firstNonNull(adAggregate.adName, rule.getAdName());
            adAggregate.rule = ruleResponse;
        }

        List<FacebookPageNodeResponse> pages = aggregates.values().stream()
                .map(pageAggregate -> FacebookPageNodeResponse.builder()
                        .pageId(pageAggregate.pageId)
                        .pageName(firstNonNull(pageAggregate.pageName, pageAggregate.pageId))
                        .campaigns(pageAggregate.campaigns.values().stream()
                                .map(campaignAggregate -> FacebookCampaignNodeResponse.builder()
                                        .campaignId(campaignAggregate.campaignId)
                                        .campaignName(firstNonNull(campaignAggregate.campaignName, campaignAggregate.campaignId))
                                        .adsets(campaignAggregate.adsets.values().stream()
                                                .map(adsetAggregate -> FacebookAdsetNodeResponse.builder()
                                                        .adsetId(adsetAggregate.adsetId)
                                                        .adsetName(firstNonNull(adsetAggregate.adsetName, adsetAggregate.adsetId))
                                                        .ads(adsetAggregate.ads.values().stream()
                                                                .map(adAggregate -> FacebookAdNodeResponse.builder()
                                                                        .adId(adAggregate.adId)
                                                                        .adName(firstNonNull(adAggregate.adName, adAggregate.adId))
                                                                        .rule(adAggregate.rule)
                                                                        .build())
                                                                .toList())
                                                        .build())
                                                .toList())
                                        .build())
                                .toList())
                        .build())
                .collect(Collectors.toList());

        return FacebookAdHierarchyResponse.builder().pages(pages).build();
    }

    private LeadDistributionRuleResponse toResponse(LeadDistributionRule rule, List<LeadDistributionAssignment> assignments) {
        List<LeadDistributionAssignmentResponse> assignmentResponses = assignments.stream()
                .map(assignment -> LeadDistributionAssignmentResponse.builder()
                        .userId(assignment.getUser().getId())
                        .fullName(assignment.getUser().getFirstName() +
                                (assignment.getUser().getLastName() != null ? " " + assignment.getUser().getLastName() : ""))
                        .email(assignment.getUser().getEmail())
                        .active(assignment.getUser().isActive())
                        .autoAssignEnabled(assignment.getUser().isAutoAssignEnabled())
                        .frequency(assignment.getFrequency())
                        .position(assignment.getPosition())
                        .build())
                .toList();

        return LeadDistributionRuleResponse.builder()
                .id(rule.getId())
                .pageId(rule.getPageId())
                .pageName(rule.getPageName())
                .campaignId(rule.getCampaignId())
                .campaignName(rule.getCampaignName())
                .adsetId(rule.getAdsetId())
                .adsetName(rule.getAdsetName())
                .adId(rule.getAdId())
                .adName(rule.getAdName())
                .assignments(assignmentResponses)
                .build();
    }

    private String ruleKey(LeadDistributionRule rule) {
        return String.join("|",
                Optional.ofNullable(rule.getPageId()).orElse(""),
                Optional.ofNullable(rule.getCampaignId()).orElse(""),
                Optional.ofNullable(rule.getAdsetId()).orElse(""),
                Optional.ofNullable(rule.getAdId()).orElse(""));
    }

    private Map<String, String> fetchPageNames(UUID organizationId, Collection<String> pageIds) {
        Map<String, String> result = new HashMap<>();
        if (pageIds == null || pageIds.isEmpty()) {
            return result;
        }
        Optional<IntegrationConfig> configOpt = integrationConfigRepository
                .findByOrganizationIdAndPlatform(organizationId, IntegrationPlatform.FACEBOOK);
        if (configOpt.isEmpty()) {
            return result;
        }

        IntegrationConfig config = configOpt.get();
        String accessToken = encryptionService.decrypt(config.getAccessToken());
        if (accessToken == null || accessToken.isBlank()) {
            return result;
        }

        for (String pageId : pageIds) {
            if (pageId == null || pageId.isBlank()) {
                continue;
            }
            try {
                Map<?, ?> resp = restTemplate.getForObject(
                        "https://graph.facebook.com/v18.0/{pageId}?fields=name&access_token={token}",
                        Map.class,
                        pageId,
                        accessToken
                );
                if (resp != null && resp.get("name") != null) {
                    result.put(pageId, resp.get("name").toString());
                }
            } catch (Exception e) {
                logger.warn("Unable to fetch Facebook page {} name: {}", pageId, e.getMessage());
            }
        }

        return result;
    }

    private String resolveName(Map<String, String> pageNames, Map<String, LeadDistributionRule> ruleMap, String pageId) {
        if (pageNames.containsKey(pageId)) {
            return pageNames.get(pageId);
        }
        return ruleMap.values().stream()
                .filter(r -> pageId.equals(r.getPageId()))
                .map(LeadDistributionRule::getPageName)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(pageId);
    }

    private String resolveCampaignName(Map<String, LeadDistributionRule> ruleMap, String pageId, String campaignId, String fallback) {
        return ruleMap.values().stream()
                .filter(r -> pageId.equals(r.getPageId()) && campaignId.equals(r.getCampaignId()))
                .map(LeadDistributionRule::getCampaignName)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(Optional.ofNullable(fallback).orElse(campaignId));
    }

    private String resolveAdsetName(Map<String, LeadDistributionRule> ruleMap, String pageId, String campaignId, String adsetId, String fallback) {
        return ruleMap.values().stream()
                .filter(r -> pageId.equals(r.getPageId())
                        && campaignId.equals(r.getCampaignId())
                        && adsetId.equals(r.getAdsetId()))
                .map(LeadDistributionRule::getAdsetName)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(Optional.ofNullable(fallback).orElse(adsetId));
    }

    private String resolveAdName(Map<String, LeadDistributionRule> ruleMap, String pageId, String campaignId, String adsetId, String adId, String fallback) {
        return ruleMap.values().stream()
                .filter(r -> pageId.equals(r.getPageId())
                        && campaignId.equals(r.getCampaignId())
                        && adsetId.equals(r.getAdsetId())
                        && adId.equals(r.getAdId()))
                .map(LeadDistributionRule::getAdName)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(Optional.ofNullable(fallback).orElse(adId));
    }

    private static <T> T firstNonNull(T first, T fallback) {
        return first != null ? first : fallback;
    }

    private static class PageAggregate {
        final String pageId;
        String pageName;
        final Map<String, CampaignAggregate> campaigns = new LinkedHashMap<>();

        PageAggregate(String pageId) {
            this.pageId = pageId;
        }
    }

    private static class CampaignAggregate {
        final String campaignId;
        String campaignName;
        final Map<String, AdsetAggregate> adsets = new LinkedHashMap<>();

        CampaignAggregate(String campaignId) {
            this.campaignId = campaignId;
        }
    }

    private static class AdsetAggregate {
        final String adsetId;
        String adsetName;
        final Map<String, AdAggregate> ads = new LinkedHashMap<>();

        AdsetAggregate(String adsetId) {
            this.adsetId = adsetId;
        }
    }

    private static class AdAggregate {
        final String adId;
        String adName;
        LeadDistributionRuleResponse rule;

        AdAggregate(String adId) {
            this.adId = adId;
        }
    }
}

