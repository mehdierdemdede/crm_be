package com.leadsyncpro.service;

import com.leadsyncpro.model.*;
import com.leadsyncpro.repository.LeadRepository;
import com.leadsyncpro.repository.UserRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class ReportService {

    private final LeadRepository leadRepository;
    private final UserRepository userRepository;

    @Data
    public static class LeadReportResponse {
        private List<DailyCount> timeline;
        private List<StatusCount> statusBreakdown;
        private List<UserPerformance> userPerformance;
    }

    @Data
    @AllArgsConstructor
    public static class DailyCount {
        private String date;
        private long leads;
    }

    @Data
    @AllArgsConstructor
    public static class StatusCount {
        private String status;
        private long count;
    }

    @Data
    @AllArgsConstructor
    public static class UserPerformance {
        private String userName;
        private long sales;
        private long total;
    }

    public LeadReportResponse getLeadReport(UUID orgId, Instant start, Instant end) {
        List<Lead> leads = leadRepository.findByOrganizationIdAndCreatedAtBetween(orgId, start, end);

        // 🔹 Timeline: günlük lead sayısı
        Map<LocalDate, Long> daily = leads.stream()
                .collect(Collectors.groupingBy(
                        l -> l.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate(),
                        Collectors.counting()
                ));

        List<DailyCount> timeline = daily.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new DailyCount(e.getKey().toString(), e.getValue()))
                .toList();

        // 🔹 Status breakdown
        Map<LeadStatus, Long> statusCounts = leads.stream()
                .collect(Collectors.groupingBy(Lead::getStatus, Collectors.counting()));

        List<StatusCount> statusBreakdown = statusCounts.entrySet().stream()
                .map(e -> new StatusCount(e.getKey().name(), e.getValue()))
                .toList();

        // 🔹 Kullanıcı performansı
        Map<UUID, List<Lead>> byUser = leads.stream()
                .filter(l -> l.getAssignedToUser() != null)
                .collect(Collectors.groupingBy(l -> l.getAssignedToUser().getId()));

        List<UserPerformance> userPerformance = byUser.entrySet().stream()
                .map(e -> {
                    User u = userRepository.findById(e.getKey()).orElse(null);
                    long total = e.getValue().size();
                    long sales = e.getValue().stream()
                            .filter(l -> l.getStatus() == LeadStatus.CLOSED_WON)
                            .count();
                    String name = u != null ? u.getFirstName() + " " + u.getLastName() : "Unknown";
                    return new UserPerformance(name, sales, total);
                })
                .sorted(Comparator.comparingLong(UserPerformance::getSales).reversed())
                .toList();

        LeadReportResponse res = new LeadReportResponse();
        res.setTimeline(timeline);
        res.setStatusBreakdown(statusBreakdown);
        res.setUserPerformance(userPerformance);

        return res;
    }
}
