package com.leadsyncpro.service;

import com.leadsyncpro.model.*;
import com.leadsyncpro.repository.LeadRepository;
import com.leadsyncpro.repository.UserRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AutoAssignService {

    private static final Logger logger = LoggerFactory.getLogger(AutoAssignService.class);

    private final UserRepository userRepository;
    private final LeadRepository leadRepository;
    private final MailService mailService;

    // ✅ İstatistik DTO
    @Data
    @AllArgsConstructor
    public static class AgentStatsResponse {
        private UUID userId;
        private String fullName;
        private boolean active;
        private boolean autoAssignEnabled;
        private Set<SupportedLanguages> supportedLanguages;
        private Integer dailyCapacity;
        private long assignedToday;
        private long remainingCapacity;
        private Instant lastAssignedAt;
    }

    // ✅ Kullanıcı istatistiklerini döner (Admin Dashboard için)
    public List<AgentStatsResponse> getAgentStats(UUID orgId) {
        List<User> users = userRepository.findAll().stream()
                .filter(u -> u.getOrganizationId().equals(orgId))
                .collect(Collectors.toList());

        Instant startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant endOfDay = startOfDay.plusSeconds(86400);

        List<AgentStatsResponse> stats = new ArrayList<>();

        for (User u : users) {
            long assignedToday = leadRepository.countByAssignedToUser_IdAndCreatedAtBetween(
                    u.getId(), startOfDay, endOfDay);

            Instant lastAssigned = leadRepository.findTopByAssignedToUser_IdOrderByCreatedAtDesc(u.getId())
                    .map(Lead::getCreatedAt)
                    .orElse(null);

            Integer dailyCap = u.getDailyCapacity() != null ? u.getDailyCapacity() : 0;
            long remaining = Math.max(dailyCap - assignedToday, 0);

            stats.add(new AgentStatsResponse(
                    u.getId(),
                    u.getFirstName() + " " + (u.getLastName() != null ? u.getLastName() : ""),
                    u.isActive(),
                    u.isAutoAssignEnabled(),
                    u.getSupportedLanguages(),
                    dailyCap,
                    assignedToday,
                    remaining,
                    lastAssigned
            ));
        }

        return stats.stream()
                .sorted(Comparator.comparingLong(AgentStatsResponse::getAssignedToday).reversed())
                .collect(Collectors.toList());
    }

    // ✅ Asıl motor: lead’i uygun kullanıcıya otomatik ata
    @Transactional
    public Optional<User> assignLeadAutomatically(Lead lead) {
        UUID orgId = lead.getOrganizationId();
        String leadLang = lead.getLanguage();

        List<User> allUsers = userRepository.findAll().stream()
                .filter(u -> u.getOrganizationId().equals(orgId))
                .filter(User::isActive)
                .filter(User::isAutoAssignEnabled)
                .collect(Collectors.toList());

        if (allUsers.isEmpty()) {
            logger.warn("Auto-assign skipped: No eligible users in org {}", orgId);
            return Optional.empty();
        }

        // 🔹 Dil uyumlu olanları filtrele
        List<User> eligibleUsers = allUsers.stream()
                .filter(u -> u.getSupportedLanguages() != null &&
                        u.getSupportedLanguages().stream()
                                .anyMatch(lang -> lang.name().equalsIgnoreCase(leadLang)))
                .collect(Collectors.toList());

        if (eligibleUsers.isEmpty()) {
            logger.warn("Auto-assign skipped: No eligible users for language {}", leadLang);
            return Optional.empty();
        }

        // 🔹 Bugün başlangıç ve bitiş zamanı
        Instant startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant endOfDay = startOfDay.plusSeconds(86400);

        // 🔹 Günlük atama sayılarını hesapla
        Map<UUID, Long> todayAssignments = new HashMap<>();
        for (User u : eligibleUsers) {
            long count = leadRepository.countByAssignedToUser_IdAndCreatedAtBetween(
                    u.getId(), startOfDay, endOfDay);
            todayAssignments.put(u.getId(), count);
        }

        // 🔹 Kapasitesi dolmamış kullanıcıları filtrele
        List<User> availableUsers = eligibleUsers.stream()
                .filter(u -> {
                    long assignedToday = todayAssignments.getOrDefault(u.getId(), 0L);
                    return u.getDailyCapacity() == null || assignedToday < u.getDailyCapacity();
                })
                .collect(Collectors.toList());

        if (availableUsers.isEmpty()) {
            logger.warn("Auto-assign skipped: all users reached capacity for org {}", orgId);
            return Optional.empty();
        }

        // 🔹 En az atanmış kullanıcıyı bul
        User selected = availableUsers.stream()
                .min(Comparator.comparingLong(u -> todayAssignments.getOrDefault(u.getId(), 0L)))
                .orElse(null);

        if (selected == null) {
            logger.warn("Auto-assign failed: could not pick user for org {}", orgId);
            return Optional.empty();
        }

        // 🔹 Lead'i ata
        lead.setAssignedToUser(selected);
        leadRepository.save(lead);

        // 🔹 Mail bildirimi gönder
        try {
            mailService.sendLeadAssignedEmail(
                    selected.getEmail(),
                    selected.getFirstName(),
                    lead.getName(),
                    lead.getCampaign() != null ? lead.getCampaign().getName() : null,
                    lead.getLanguage(),
                    lead.getStatus() != null ? lead.getStatus().name() : "NEW",
                    lead.getId().toString()
            );
        } catch (Exception e) {
            logger.warn("Mail gönderilemedi: {}", e.getMessage());
        }

        logger.info("✅ Auto-assigned lead {} to user {}", lead.getId(), selected.getEmail());
        return Optional.of(selected);
    }
}
