package com.leadsyncpro.billing.service;

import java.util.List;
import java.util.Map;

public record CreatePlanCommand(
        String code,
        String name,
        String description,
        List<String> features,
        Map<String, Object> metadata) {}
