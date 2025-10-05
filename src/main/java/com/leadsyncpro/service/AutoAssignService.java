package com.leadsyncpro.service;

import com.leadsyncpro.model.Lead;
import com.leadsyncpro.model.User;
import com.leadsyncpro.repository.LeadRepository;
import com.leadsyncpro.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AutoAssignService {

    private static final Logger logger = LoggerFactory.getLogger(AutoAssignService.class);

    private final UserRepository userRepository;
    private final LeadRepository leadRepository;

    public AutoAssignService(UserRepository userRepository, LeadRepository leadRepository) {
        this.userRepository = userRepository;
        this.leadRepository = leadRepository;
    }

    /**
     * Yeni bir lead geldiğinde otomatik kullanıcı ataması yapar.
     */
    @Transactional
    public Optional<User> assignLeadIfPossible(Lead lead) {
        UUID orgId = lead.getOrganizationId();
        if (orgId == null || lead.getLanguage() == null) {
            logger.debug("AutoAssign atlandı: organization veya language eksik.");
            return Optional.empty();
        }

        // 🔹 1. Otomatik atama açık kullanıcıları çek
        List<User> candidates = userRepository.findByOrganizationIdAndAutoAssignEnabledTrue(orgId);
        if (candidates.isEmpty()) {
            logger.debug("AutoAssign: otomatik atama aktif kullanıcı yok.");
            return Optional.empty();
        }

        // 🔹 2. Günlük kapasite ve dil uyumu kontrolü
        Map<UUID, Long> assignedCounts = leadRepository.countTodayAssignmentsByUsers(orgId, Instant.now());
        List<User> available = candidates.stream()
                .filter(u -> u.isActive())
                .filter(u -> u.getSupportedLanguages() != null && !u.getSupportedLanguages().isEmpty())
                .filter(u -> u.getSupportedLanguages().stream()
                        .anyMatch(lang -> lang.name().equalsIgnoreCase(lead.getLanguage())))
                .filter(u -> {
                    int assigned = assignedCounts.getOrDefault(u.getId(), 0L).intValue();
                    return u.getDailyCapacity() == null || assigned < u.getDailyCapacity();
                })
                .collect(Collectors.toList());

        if (available.isEmpty()) {
            logger.info("AutoAssign: uygun kullanıcı bulunamadı ({}).", lead.getLanguage());
            return Optional.empty();
        }

        // 🔹 3. En az lead almış kullanıcıya ata
        available.sort(Comparator.comparingInt(u -> assignedCounts.getOrDefault(u.getId(), 0L).intValue()));
        User selected = available.get(0);

        lead.setAssignedToUser(selected);
        leadRepository.save(lead);

        logger.info("AutoAssign: lead {} -> {} kullanıcısına atandı (dil: {}).",
                lead.getId(), selected.getEmail(), lead.getLanguage());

        return Optional.of(selected);
    }
}
