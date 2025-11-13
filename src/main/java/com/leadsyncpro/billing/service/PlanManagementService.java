package com.leadsyncpro.billing.service;

import com.leadsyncpro.model.billing.Plan;
import com.leadsyncpro.repository.billing.PlanRepository;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PlanManagementService {

    private final PlanRepository planRepository;

    public PlanManagementService(PlanRepository planRepository) {
        this.planRepository = planRepository;
    }

    @Transactional
    public Plan createPlan(CreatePlanCommand command) {
        String code = normalize(command.code());
        String name = normalize(command.name());

        planRepository
                .findByCode(code)
                .ifPresent(plan -> {
                    throw new IllegalArgumentException("Plan with code %s already exists".formatted(code));
                });

        List<String> features = command.features() != null
                ? command.features().stream()
                        .filter(StringUtils::hasText)
                        .map(String::trim)
                        .collect(Collectors.toCollection(ArrayList::new))
                : new ArrayList<>();

        Map<String, Object> metadata = command.metadata() != null
                ? new HashMap<>(command.metadata())
                : new HashMap<>();

        Plan plan = Plan.builder()
                .code(code)
                .name(name)
                .description(command.description())
                .features(features)
                .metadata(metadata)
                .build();

        return planRepository.save(plan);
    }

    private String normalize(String value) {
        return value != null ? value.trim() : null;
    }
}
