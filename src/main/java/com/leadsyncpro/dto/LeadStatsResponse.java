package com.leadsyncpro.dto;

import lombok.*;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeadStatsResponse {
    private long totalLeads;
    private long contactedLeads;           // NEW dışındaki tüm statüler
    private double conversionRate;         // CLOSED_WON / totalLeads * 100
    private Long avgFirstResponseMinutes;  // ilk aksiyona kadar ortalama dk (log yoksa null)

    private List<StatusCount> statusBreakdown;
    private List<CampaignCount> campaignBreakdown;

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class StatusCount {
        private String status;
        private long count;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CampaignCount {
        private String campaignName; // null ise "Unassigned"
        private long count;
    }
}
