package com.leadsyncpro.service;

import com.leadsyncpro.dto.*;
import com.leadsyncpro.exception.ResourceNotFoundException;
import com.leadsyncpro.model.*;
import com.leadsyncpro.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static java.time.temporal.ChronoUnit.DAYS;

@Service
public class LeadService {

    private static final Logger logger = LoggerFactory.getLogger(LeadService.class);

    @Value("${app.system-user-id:00000000-0000-0000-0000-000000000000}")
    private UUID SYSTEM_USER_ID;

    private final LeadRepository leadRepository;
    private final CampaignRepository campaignRepository;
    private final UserRepository userRepository;
    private final LeadStatusLogRepository leadStatusLogRepository;
    private final LeadActivityLogRepository leadActivityLogRepository;
    private final AutoAssignService autoAssignService;
    private final MailService mailService;
    private final SalesRepository salesRepository;

    public LeadService(LeadRepository leadRepository,
                       CampaignRepository campaignRepository,
                       UserRepository userRepository,
                       LeadStatusLogRepository leadStatusLogRepository, LeadActivityLogRepository leadActivityLogRepository, AutoAssignService autoAssignService, MailService mailService, SalesRepository salesRepository) {
        this.leadRepository = leadRepository;
        this.campaignRepository = campaignRepository;
        this.userRepository = userRepository;
        this.leadStatusLogRepository = leadStatusLogRepository;
        this.leadActivityLogRepository = leadActivityLogRepository;
        this.autoAssignService = autoAssignService;
        this.mailService = mailService;
        this.salesRepository = salesRepository;
    }

    // ───────────────────────────────
    // CREATE LEAD
    // ───────────────────────────────
    @Transactional
    public Lead createLead(UUID organizationId, LeadCreateRequest request) {
        Lead lead = new Lead();
        lead.setOrganizationId(organizationId);
        lead.setName(request.getName());
        lead.setPhone(request.getPhone());
        lead.setEmail(request.getEmail());
        lead.setLanguage(request.getLanguage());
        lead.setNotes(request.getNotes());
        lead.setStatus(LeadStatus.valueOf(request.getStatus().toUpperCase()));

        if (request.getCampaignId() != null) {
            Campaign campaign = campaignRepository.findById(request.getCampaignId())
                    .filter(c -> c.getOrganizationId().equals(organizationId))
                    .orElseThrow(() -> new ResourceNotFoundException("Campaign not found or access denied."));
            lead.setCampaign(campaign);
        }

        if (request.getAssignedToUserId() != null) {
            User assignedUser = userRepository.findById(request.getAssignedToUserId())
                    .filter(u -> u.getOrganizationId().equals(organizationId))
                    .orElseThrow(() -> new ResourceNotFoundException("Assigned user not found or access denied."));
            lead.setAssignedToUser(assignedUser);

            // ✅ E-posta bildirimi
            try {
                mailService.sendLeadAssignedEmail(
                        assignedUser.getEmail(),
                        assignedUser.getFirstName(),
                        lead.getName(),
                        lead.getCampaign() != null ? lead.getCampaign().getName() : null,
                        lead.getLanguage(),
                        lead.getStatus().name(),
                        lead.getId().toString()
                );
            } catch (Exception e) {
                logger.warn("Lead atama bildirimi gönderilemedi: {}", e.getMessage());
            }
        }

        Lead saved = leadRepository.save(lead);
        autoAssignService.assignLeadAutomatically(saved);
        logger.info("Yeni lead oluşturuldu: {} ({})", saved.getId(), saved.getName());
        return saved;
    }

    // ───────────────────────────────
    // UPDATE LEAD
    // ───────────────────────────────
    @Transactional
    public Lead updateLead(UUID leadId, UUID organizationId, LeadUpdateRequest request) {
        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new ResourceNotFoundException("Lead not found with ID: " + leadId));

        if (!lead.getOrganizationId().equals(organizationId)) {
            throw new SecurityException("Access denied: Lead does not belong to this organization.");
        }

        if (request.getName() != null) lead.setName(request.getName());
        if (request.getPhone() != null) lead.setPhone(request.getPhone());
        if (request.getEmail() != null) lead.setEmail(request.getEmail());
        if (request.getLanguage() != null) lead.setLanguage(request.getLanguage());
        if (request.getNotes() != null) lead.setNotes(request.getNotes());

        if (request.getStatus() != null) {
            lead.setStatus(LeadStatus.valueOf(request.getStatus().toUpperCase()));
        }

        if (request.getCampaignId() != null) {
            Campaign campaign = campaignRepository.findById(request.getCampaignId())
                    .filter(c -> c.getOrganizationId().equals(organizationId))
                    .orElseThrow(() -> new ResourceNotFoundException("Campaign not found or access denied."));
            lead.setCampaign(campaign);
        } else if (request.isClearCampaign()) {
            lead.setCampaign(null);
        }

        if (request.getAssignedToUserId() != null) {
            User assignedUser = userRepository.findById(request.getAssignedToUserId())
                    .filter(u -> u.getOrganizationId().equals(organizationId))
                    .orElseThrow(() -> new ResourceNotFoundException("Assigned user not found or access denied."));
            lead.setAssignedToUser(assignedUser);
        } else if (request.isClearAssignedUser()) {
            lead.setAssignedToUser(null);
        }

        return leadRepository.save(lead);
    }

    // ───────────────────────────────
    // DELETE LEAD
    // ───────────────────────────────
    @Transactional
    public void deleteLead(UUID leadId, UUID organizationId) {
        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new ResourceNotFoundException("Lead not found with ID: " + leadId));

        if (!lead.getOrganizationId().equals(organizationId)) {
            throw new SecurityException("Access denied: Lead does not belong to this organization.");
        }

        leadRepository.delete(lead);
        logger.info("Lead silindi: {} ({})", leadId, lead.getName());
    }

    // ───────────────────────────────
    // GET LEADS
    // ───────────────────────────────
    public LeadResponse getLeadById(UUID leadId, UUID organizationId) {
        Lead lead = leadRepository.findById(leadId)
                .filter(l -> l.getOrganizationId().equals(organizationId))
                .orElseThrow(() -> new ResourceNotFoundException("Lead not found or access denied."));

        return mapToLeadResponse(lead);
    }

    public List<Lead> getLeadsByOrganization(UUID organizationId, String campaignName, String status, UUID assignedToUserId) {
        UUID campaignId = null;
        if (campaignName != null && !campaignName.isEmpty()) {
            campaignId = campaignRepository.findByOrganizationIdAndName(organizationId, campaignName)
                    .map(Campaign::getId)
                    .orElse(null);
        }

        LeadStatus leadStatus = null;
        if (status != null && !status.isEmpty()) {
            try {
                leadStatus = LeadStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }

        return leadRepository.findByOrganizationIdAndFilters(organizationId, campaignId, leadStatus, assignedToUserId);
    }

    public Page<Lead> getLeadsByOrganizationPaged(UUID organizationId,
                                                  String campaign,
                                                  String status,
                                                  UUID assigneeId,
                                                  Pageable pageable) {
        LeadStatus statusEnum = null;

        if (status != null && !status.isBlank()) {
            try {
                statusEnum = LeadStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // Geçersiz bir status değeri gelirse, filtreleme yapılmaz
                statusEnum = null;
            }
        }

        // 🧩 Null-safe sorgu çağrısı
        return leadRepository.findByOrganizationIdAndOptionalFilters(
                organizationId,
                (campaign != null && !campaign.isBlank()) ? campaign.trim() : null,
                statusEnum,
                assigneeId,
                pageable
        );
    }


    // ───────────────────────────────
    // UPDATE LEAD STATUS
    // ───────────────────────────────
    @Transactional
    public LeadResponse updateLeadStatus(UUID leadId, LeadStatus newStatus, UUID userId, UUID organizationId) {
        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new ResourceNotFoundException("Lead bulunamadı: " + leadId));

        if (!lead.getOrganizationId().equals(organizationId)) {
            throw new SecurityException("Access denied: Lead does not belong to this organization.");
        }

        LeadStatus oldStatus = lead.getStatus();
        if (oldStatus == newStatus) {
            logger.info("Lead {} zaten {} durumunda, güncellenmedi.", leadId, newStatus);
            return mapToLeadResponse(lead);
        }

        lead.setStatus(newStatus);
        lead.setUpdatedAt(Instant.now());
        Lead savedLead = leadRepository.save(lead);

        LeadStatusLog log = LeadStatusLog.builder()
                .leadId(leadId)
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .changedBy(userId)
                .build();

        leadStatusLogRepository.save(log);
        logger.info("Lead {} durumu değişti: {} → {}", leadId, oldStatus, newStatus);

        return mapToLeadResponse(savedLead);
    }

    private LeadResponse mapToLeadResponse(Lead lead) {
        Optional<Sale> lastSale = salesRepository
                .findTopByLead_IdAndOrganizationIdOrderByCreatedAtDesc(lead.getId(), lead.getOrganizationId());

        SaleResponse lastSaleResponse = lastSale.map(this::mapToSaleResponse).orElse(null);

        return LeadResponse.builder()
                .id(lead.getId())
                .name(lead.getName())
                .status(lead.getStatus())
                .phone(lead.getPhone())
                .messengerPageId(lead.getPageId())
                .lastSaleId(lastSale.map(Sale::getId).orElse(null))
                .lastSale(lastSaleResponse)
                .build();
    }

    private SaleResponse mapToSaleResponse(Sale sale) {
        return SaleResponse.builder()
                .id(sale.getId())
                .leadId(sale.getLead() != null ? sale.getLead().getId() : null)
                .productName(sale.getOperationType())
                .amount(sale.getPrice())
                .currency(sale.getCurrency())
                .createdAt(sale.getCreatedAt())
                .build();
    }

    // ───────────────────────────────
    // ASSIGN LEAD TO USER
    // ───────────────────────────────
    @Transactional
    public Lead assignLead(UUID leadId, UUID userId, UUID organizationId) {
        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new ResourceNotFoundException("Lead not found: " + leadId));

        if (!lead.getOrganizationId().equals(organizationId)) {
            throw new SecurityException("Access denied: Lead does not belong to this organization.");
        }

        User user = null;
        if (userId != null) {
            user = userRepository.findById(userId)
                    .filter(u -> u.getOrganizationId().equals(organizationId))
                    .orElseThrow(() -> new ResourceNotFoundException("User not found or access denied."));
        }

        lead.setAssignedToUser(user);
        lead.setUpdatedAt(Instant.now());
        Lead updated = leadRepository.save(lead);

        logger.info("Lead {} kullanıcıya atandı: {}", leadId, (user != null ? user.getEmail() : "unassigned"));
        return updated;
    }

    // ───────────────────────────────
    // BULK ASSIGN LEADS
    // ───────────────────────────────
    @Transactional
    public void bulkAssign(List<UUID> leadIds, UUID userId, UUID organizationId) {
        User user = userRepository.findById(userId)
                .filter(u -> u.getOrganizationId().equals(organizationId))
                .orElseThrow(() -> new ResourceNotFoundException("User not found or access denied."));

        List<Lead> leads = leadRepository.findAllById(leadIds);
        for (Lead lead : leads) {
            if (!lead.getOrganizationId().equals(organizationId)) continue;
            lead.setAssignedToUser(user);
            lead.setUpdatedAt(Instant.now());
        }

        leadRepository.saveAll(leads);
        logger.info("{} lead {} kullanıcısına atandı.", leads.size(), user.getEmail());
    }

    // ───────────────────────────────
    // AUTOMATION RULES (SCHEDULED)
    // ───────────────────────────────
    @Scheduled(cron = "0 0 3 * * *") // Her gece 03:00'te çalışır
    @Transactional
    public void autoUpdateLeadStatuses() {
        Instant now = Instant.now();

        // 1️⃣ HOT → 7 gün geçti, hâlâ SOLD değilse bildirim oluştur
        Instant sevenDaysAgo = now.minus(7, ChronoUnit.DAYS);
        List<Lead> hotLeads = leadRepository.findAllByStatusAndUpdatedAtBefore(LeadStatus.HOT, sevenDaysAgo);
        for (Lead lead : hotLeads) {
            // Burada bildirim servisini çağırabilirsin
            logger.info("HOT lead {} ({}): 7 gün geçti, satış yapılmadı → Uyarı gönderiliyor.",
                    lead.getId(), lead.getName());
            // notificationService.notifyUser(lead.getAssignedToUser(), "Sıcak hasta için 7 gün geçti, satış yapılmadı!");
        }

        // 2️⃣ NOT_INTERESTED → ertesi gün Super User’a aktar
        Instant oneDayAgo = now.minus(1, ChronoUnit.DAYS);
        List<Lead> uninterestedLeads = leadRepository.findAllByStatusAndUpdatedAtBefore(LeadStatus.NOT_INTERESTED, oneDayAgo);
        for (Lead lead : uninterestedLeads) {
            transferToSuperUser(lead, "Lead ilgisiz olarak işaretlendi.");
        }

        // 3️⃣ BLOCKED → ertesi gün Super User’a aktar
        List<Lead> blockedLeads = leadRepository.findAllByStatusAndUpdatedAtBefore(LeadStatus.BLOCKED, oneDayAgo);
        for (Lead lead : blockedLeads) {
            transferToSuperUser(lead, "Lead blocked olarak işaretlendi.");
        }

        // 4️⃣ WRONG_INFO → ertesi gün Super User’a aktar
        List<Lead> wrongLeads = leadRepository.findAllByStatusAndUpdatedAtBefore(LeadStatus.WRONG_INFO, oneDayAgo);
        for (Lead lead : wrongLeads) {
            transferToSuperUser(lead, "Lead yanlış bilgi içeriyor.");
        }

        logger.info("""
        Otomatik lead güncelleme tamamlandı:
        🔸 HOT kontrolü: {} lead
        🔸 NOT_INTERESTED → SuperUser: {}
        🔸 BLOCKED → SuperUser: {}
        🔸 WRONG_INFO → SuperUser: {}
        """,
                hotLeads.size(),
                uninterestedLeads.size(),
                blockedLeads.size(),
                wrongLeads.size()
        );
    }

    // Yardımcı: ISO Instant parse (null-safe)
    private Instant parseOrDefault(String iso, Instant def) {
        if (iso == null || iso.isBlank()) return def;
        try { return Instant.parse(iso); } catch (Exception e) { return def; }
    }

    private void transferToSuperUser(Lead lead, String reason) {
        try {
            // Super User bul (rolü SUPER_ADMIN olan)
            Optional<User> superUserOpt = userRepository.findFirstByOrganizationIdAndRole(
                    lead.getOrganizationId(), Role.SUPER_ADMIN
            );

            if (superUserOpt.isPresent()) {
                User superUser = superUserOpt.get();
                lead.setAssignedToUser(superUser);
                leadRepository.save(lead);
                logger.info("Lead {} Super User'a aktarıldı. Sebep: {}", lead.getId(), reason);

                // Bildirim gönder (opsiyonel)
                // notificationService.notifyUser(superUser, "Yeni lead size aktarıldı: " + lead.getName());
            } else {
                logger.warn("Super User bulunamadı, lead {} aktarılmadı.", lead.getId());
            }
        } catch (Exception e) {
            logger.error("Lead {} aktarım hatası: {}", lead.getId(), e.getMessage());
        }
    }


    // NEW dışı statüler "contacted" sayılır
    private static final EnumSet<LeadStatus> CONTACTED_STATUSES = EnumSet.of(
            LeadStatus.HOT,
            LeadStatus.SOLD,
            LeadStatus.NOT_INTERESTED,
            LeadStatus.BLOCKED,
            LeadStatus.WRONG_INFO
    );


    /**
     * Dashboard istatistiklerini döndürür.
     * startIso / endIso opsiyonel (ISO-8601). Sağlanmazsa son 30 gün.
     */
    @Transactional(readOnly = true)
    public LeadStatsResponse getDashboardStats(UUID organizationId, String startIso, String endIso) {
        Instant end = parseOrDefault(endIso, Instant.now());
        Instant start = parseOrDefault(startIso, end.minus(30, DAYS));

        long total = leadRepository.countByOrganizationIdAndCreatedAtBetween(organizationId, start, end);

        // Status breakdown
        List<Object[]> statusRows = leadRepository.countByStatusBetween(organizationId, start, end);
        List<LeadStatsResponse.StatusCount> statusBreakdown = statusRows.stream()
                .map(r -> LeadStatsResponse.StatusCount.builder()
                        .status(((LeadStatus) r[0]).name())
                        .count((long) (Long) r[1])
                        .build())
                .toList();

        long closedWon = statusBreakdown.stream()
                .filter(s -> "CLOSED_WON".equals(s.getStatus()))
                .mapToLong(LeadStatsResponse.StatusCount::getCount)
                .sum();

        long contacted = statusBreakdown.stream()
                .filter(s -> !s.getStatus().equals("NEW"))
                .mapToLong(LeadStatsResponse.StatusCount::getCount)
                .sum();

        double conversionRate = (total > 0) ? (closedWon * 100.0 / total) : 0.0;

        // Campaign breakdown (toplam dağılım)
        List<Object[]> campRows = leadRepository.countByCampaignBetween(organizationId, start, end);
        List<LeadStatsResponse.CampaignCount> campaignBreakdown = campRows.stream()
                .map(r -> LeadStatsResponse.CampaignCount.builder()
                        .campaignName((String) r[0])
                        .count((long) (Long) r[1])
                        .build())
                .toList();

        // Ortalama ilk yanıt süresi (createdAt -> ilk LeadStatusLog kaydı)
        List<Lead> leadsInRange = leadRepository.findByOrganizationIdAndCreatedAtBetween(organizationId, start, end);
        Map<UUID, Instant> createdMap = leadsInRange.stream()
                .collect(Collectors.toMap(Lead::getId, Lead::getCreatedAt));

        Long avgFirstRespondMinutes = null;
        if (!leadsInRange.isEmpty()) {
            List<UUID> ids = leadsInRange.stream().map(Lead::getId).toList();
            List<LeadStatusLog> logs = leadStatusLogRepository.findByLeadIdInOrderByCreatedAtAsc(ids);

            // leadId -> ilk log zamanı
            Map<UUID, Instant> firstLogPerLead = new HashMap<>();
            for (LeadStatusLog log : logs) {
                firstLogPerLead.putIfAbsent(log.getId() != null ? log.getLeadId() : null, log.getCreatedAt());
            }

            long sumMinutes = 0;
            int counted = 0;
            for (UUID leadId : ids) {
                Instant createdAt = createdMap.get(leadId);
                Instant firstLogAt = firstLogPerLead.get(leadId);
                if (createdAt != null && firstLogAt != null && firstLogAt.isAfter(createdAt)) {
                    long minutes = ChronoUnit.MINUTES.between(createdAt, firstLogAt);
                    sumMinutes += minutes;
                    counted++;
                }
            }
            if (counted > 0) avgFirstRespondMinutes = sumMinutes / counted;
        }

        return LeadStatsResponse.builder()
                .totalLeads(total)
                .contactedLeads(contacted)
                .conversionRate(Math.round(conversionRate * 10.0) / 10.0) // 1 ondalık
                .avgFirstResponseMinutes(avgFirstRespondMinutes)
                .statusBreakdown(statusBreakdown)
                .campaignBreakdown(campaignBreakdown)
                .build();
    }

    @Transactional
    public LeadActionResponse addActivity(UUID leadId, UUID organizationId, UUID userId, LeadActionRequest request) {
        // 1) Lead doğrulaması (org bazlı erişim)
        Lead lead = leadRepository.findById(leadId)
                .filter(l -> l.getOrganizationId().equals(organizationId))
                .orElseThrow(() -> new ResourceNotFoundException("Lead not found or not accessible."));

        // 2) Log oluştur
        LeadActivityLog log = new LeadActivityLog();
        log.setLead(lead);
        log.setUserId(userId);
        log.setAction(request.getActionType());
        log.setDetails(request.getMessage());
        log.setCreatedAt(Instant.now());

        // 3) Kaydet
        LeadActivityLog saved = leadActivityLogRepository.save(log);

        // 4) İlk aksiyon zamanı
        if (lead.getFirstActionAt() == null) {
            lead.setFirstActionAt(saved.getCreatedAt());
            leadRepository.save(lead);
        }

        // 5) Response
        return LeadActionResponse.fromEntity(saved);
    }

}
