package com.leadsyncpro.service;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class OrganizationService {

    // Demo: gerçek sistemde DB’den Organization tablosundan limit çekilecek
    private final Map<UUID, Integer> orgLimits = new HashMap<>();

    public int getMemberLimit(UUID orgId) {
        // Eğer DB’de organization tablosu varsa oradan oku
        return orgLimits.getOrDefault(orgId, 10); // default 10
    }

    // Test için organizasyona limit set edebilirsin
    public void setMemberLimit(UUID orgId, int limit) {
        orgLimits.put(orgId, limit);
    }
}