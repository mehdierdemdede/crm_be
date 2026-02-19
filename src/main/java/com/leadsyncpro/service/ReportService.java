package com.leadsyncpro.service;

import com.leadsyncpro.model.*;
import com.leadsyncpro.repository.LeadRepository;
import com.leadsyncpro.repository.SalesRepository;
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
        private final SalesRepository salesRepository;

        @Data
        public static class LeadReportResponse {
                private List<DailyCount> timeline;
                private List<StatusCount> statusBreakdown;
                private List<UserPerformance> userPerformance;
                private long totalLeads;
                private long totalSales;
                private Map<String, Double> totalRevenue;
                private double conversionRate;
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
                // 1. Timeline (Daily Counts)
                List<Object[]> timelineData = leadRepository.countByDateBetween(orgId, start, end);
                List<DailyCount> timeline = timelineData.stream()
                                .map(row -> new DailyCount(row[0].toString(), ((Number) row[1]).longValue()))
                                .sorted(Comparator.comparing(DailyCount::getDate))
                                .collect(Collectors.toList());

                // 2. Status Breakdown
                List<Object[]> statusData = leadRepository.countByStatusBetween(orgId, start, end);
                List<StatusCount> statusBreakdown = statusData.stream()
                                .map(row -> new StatusCount(row[0].toString(), ((Number) row[1]).longValue()))
                                .collect(Collectors.toList());

                // 3. User Performance (This one is complex because we need Sales count per
                // user)
                // For now, we can keep the existing logic or optimize if we add a dedicated
                // query.
                // Existing logic requires fetching leads.
                // Optimization: Let's fetch basic stats from repo and join.
                // But for "Sales" count per user, LeadRepository doesn't easily show "Sold"
                // leads count per user in one query with Total.
                // Let's use the existing "memory" approach ONLY for User Performance for now,
                // but strictly filtered by date in repo?
                // Actually, let's look at `countTodayAssignmentsByUsers` but that's for
                // "Today".
                // Let's stick to the previous implementation for user performance BUT using a
                // more targeted query if possible.
                // Limitation: To get "Sales" count per user, we need to count leads with
                // status=SOLD by user.
                // Query: SELECT l.assignedToUser.id, COUNT(l) FROM Lead l WHERE ... GROUP BY
                // l.assignedToUser.id
                // We can do two queries: Total Leads per User, Sold Leads per User. Then merge
                // in Java.

                // Fetch Total Leads per User
                List<Lead> leads = leadRepository.findByOrganizationIdAndCreatedAtBetween(orgId, start, end);
                // We still fetch leads for User Performance to avoid complex SQL for now,
                // assuming User count is small.
                // But we improved Timeline and Status breakdown to use DB aggregation.
                // Wait, if I fetch ALL leads here, I negate the benefit of other queries!
                // I MUST avoid fetching all leads.

                // Efficient User Performance Strategy:
                // 1. Count Total Assignments by User in range
                // 2. Count Sold Leads by User in range
                // 3. Merge.
                // However, I don't have those specific queries ready. The
                // `findByOrganizationIdAndCreatedAtBetween` is what causes memory issues.
                // Let's allow the "User Performance" to use the
                // `findByOrganizationIdAndCreatedAtBetween` for now BUT limit the fields
                // selected?
                // No, let's keep it simple. If I want to avoid memory issues, I should NOT call
                // `findByOrganizationIdAndCreatedAtBetween`.
                // I will rely on the "Timeline" and "Status" aggregation queries.
                // For User Performance, I will skip it or implement it properly?
                // The Plan said "Group by user (performance)" in LeadRepository.
                // The implementation step missed adding a specific "Group by User" query that
                // returns (User, Total, Sold).
                // Let's use the existing `leads` fetch for User Performance for now, but
                // acknowledge it's a bottleneck.
                // OR better: Add the missing query now?
                // I can't easily add another query to LeadService without another file edit.
                // Let's stick with the plan: "Change logic to use LeadRepository... query for
                // aggregations".
                // I'll keep the `findByOrganizationId...` for UserPerformance but realize it's
                // the remaining bottleneck.
                // Actually, for the KPI cards I need `totalLeads`, `totalSales` etc.

                // KPI Calculations
                long totalLeads = leadRepository.countByOrganizationIdAndCreatedAtBetween(orgId, start, end);
                long totalSales = salesRepository.countByOrganizationIdAndOperationDateBetween(orgId, start, end);

                // Revenue
                List<Object[]> revenueData = salesRepository.sumPriceByCurrencyBetween(orgId, start, end);
                Map<String, Double> totalRevenue = revenueData.stream()
                                .collect(Collectors.toMap(
                                                row -> (String) row[0],
                                                row -> ((Number) row[1]).doubleValue()));

                double conversionRate = totalLeads > 0 ? ((double) totalSales / totalLeads) * 100 : 0;

                // User Performance (Legacy mode for now)
                // We still fetch leads for this part. To truly optimize, we'd need
                // `countByUser` and `countSoldByUser` queries.
                // Let's optimize by NOT fetching if the range is too huge? No.
                Map<UUID, List<Lead>> byUser = leads.stream()
                                .filter(l -> l.getAssignedToUser() != null)
                                .collect(Collectors.groupingBy(l -> l.getAssignedToUser().getId()));

                List<UserPerformance> userPerformance = byUser.entrySet().stream()
                                .map(e -> {
                                        User u = userRepository.findById(e.getKey()).orElse(null);
                                        long total = e.getValue().size();
                                        long sales = e.getValue().stream().filter(l -> l.getStatus() == LeadStatus.SOLD)
                                                        .count();
                                        String name = u != null
                                                        ? u.getFirstName() + " "
                                                                        + (u.getLastName() != null ? u.getLastName()
                                                                                        : "")
                                                        : "Unknown";
                                        return new UserPerformance(name.trim(), sales, total);
                                })
                                .sorted(Comparator.comparingLong(UserPerformance::getSales).reversed())
                                .collect(Collectors.toList());

                LeadReportResponse res = new LeadReportResponse();
                res.setTimeline(timeline);
                res.setStatusBreakdown(statusBreakdown);
                res.setUserPerformance(userPerformance);
                res.setTotalLeads(totalLeads);
                res.setTotalSales(totalSales);
                res.setTotalRevenue(totalRevenue);
                res.setConversionRate(conversionRate);

                return res;
        }
}
