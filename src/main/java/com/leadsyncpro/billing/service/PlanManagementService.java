package com.leadsyncpro.billing.service;

import com.leadsyncpro.model.billing.BillingPeriod;
import com.leadsyncpro.model.billing.Plan;
import com.leadsyncpro.model.billing.Price;
import com.leadsyncpro.repository.billing.PlanRepository;
import com.leadsyncpro.repository.billing.PriceRepository;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Service
public class PlanManagementService {

    private final PlanRepository planRepository;
    private final PriceRepository priceRepository;

    public PlanManagementService(PlanRepository planRepository, PriceRepository priceRepository) {
        this.planRepository = Objects.requireNonNull(planRepository, "planRepository must not be null");
        this.priceRepository = Objects.requireNonNull(priceRepository, "priceRepository must not be null");
    }

    @Transactional
    public Plan createPlan(CreatePlanCommand command) {
        if (command == null) {
            throw new PlanValidationException("CreatePlanCommand must not be null");
        }
        String code = normalize(command.code());
        String name = normalize(command.name());

        planRepository
                .findByCode(code)
                .ifPresent(plan -> {
                    throw new PlanConflictException("Plan with id '%s' already exists".formatted(code));
                });

        List<String> features = sanitizeFeatures(command.features());
        List<Price> prices = buildPrices(command.prices(), command.metadata());

        Plan plan = Plan.builder()
                .code(code)
                .name(name)
                .description(StringUtils.hasText(command.description()) ? command.description().trim() : null)
                .features(features)
                .metadata(new LinkedHashMap<>())
                .build();

        Plan savedPlan = planRepository.save(plan);
        prices.forEach(price -> price.setPlan(savedPlan));
        List<Price> savedPrices = priceRepository.saveAll(prices);
        Map<BillingPeriod, Price> priceByPeriod = savedPrices.stream()
                .collect(Collectors.toMap(
                        Price::getBillingPeriod,
                        p -> p,
                        (left, right) -> left,
                        () -> new EnumMap<>(BillingPeriod.class)));

        Map<String, Object> metadata = buildMetadata(command.metadata(), priceByPeriod);
        savedPlan.setMetadata(metadata);
        savedPlan.setPrices(savedPrices);

        return planRepository.save(savedPlan);
    }

    private List<String> sanitizeFeatures(List<String> features) {
        if (CollectionUtils.isEmpty(features)) {
            return List.of();
        }
        return features.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private List<Price> buildPrices(
            List<CreatePlanCommand.PlanPriceDefinition> definitions, CreatePlanCommand.PlanMetadata metadata) {
        if (CollectionUtils.isEmpty(definitions)) {
            throw new PlanValidationException("At least one price definition is required");
        }

        Set<String> clientIds = new HashSet<>();
        EnumSet<BillingPeriod> periods = EnumSet.noneOf(BillingPeriod.class);
        List<Price> prices = new ArrayList<>();
        for (CreatePlanCommand.PlanPriceDefinition definition : definitions) {
            String clientPriceId = definition.clientPriceId() != null ? definition.clientPriceId().trim() : null;
            if (!StringUtils.hasText(clientPriceId)) {
                throw new PlanValidationException("Price identifier must not be blank");
            }
            if (!clientIds.add(clientPriceId)) {
                throw new PlanConflictException("Price identifiers must be unique within the request");
            }
            BillingPeriod period = definition.billingPeriod();
            if (period == null) {
                throw new PlanValidationException("Billing period must be provided for each price");
            }
            if (!periods.add(period)) {
                throw new PlanValidationException(
                        "Only one price per billing period is allowed. Duplicate: %s".formatted(period));
            }

            long perSeatAmount = toMinorUnits(definition.amount(),
                    "amount for price '%s'".formatted(definition.clientPriceId()));
            long baseAmount = resolveBaseAmount(metadata, period,
                    "basePrice for period %s".formatted(period));

            String currency = definition.currency() != null ? definition.currency().trim() : null;
            if (!StringUtils.hasText(currency)) {
                throw new PlanValidationException("Currency must be provided for each price");
            }

            Price price = Price.builder()
                    .billingPeriod(period)
                    .perSeatAmountCents(perSeatAmount)
                    .baseAmountCents(baseAmount)
                    .currency(currency.toUpperCase(Locale.ROOT))
                    .seatLimit(definition.seatLimit())
                    .trialDays(definition.trialDays())
                    .build();
            prices.add(price);
        }
        return prices;
    }

    private long resolveBaseAmount(
            CreatePlanCommand.PlanMetadata metadata, BillingPeriod period, String label) {
        BigDecimal value = null;
        if (metadata != null) {
            value = switch (period) {
                case MONTH -> firstNonNull(metadata.basePriceMonth(), metadata.basePrice());
                case YEAR -> firstNonNull(metadata.basePriceYear(), metadata.basePrice());
            };
        }
        return toMinorUnits(value != null ? value : BigDecimal.ZERO, label);
    }

    private Map<String, Object> buildMetadata(
            CreatePlanCommand.PlanMetadata metadata, Map<BillingPeriod, Price> priceByPeriod) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (metadata != null) {
            putIfNotNull(values, "basePrice", metadata.basePrice());
            putIfNotNull(values, "perSeatPrice", metadata.perSeatPrice());
            putIfNotNull(values, "basePrice_month", metadata.basePriceMonth());
            putIfNotNull(values, "perSeatPrice_month", metadata.perSeatPriceMonth());
            putIfNotNull(values, "basePrice_year", metadata.basePriceYear());
            putIfNotNull(values, "perSeatPrice_year", metadata.perSeatPriceYear());
        }

        priceByPeriod.forEach((period, price) -> {
            if (price == null) {
                return;
            }
            String suffix = period.name().toLowerCase(Locale.ROOT);
            values.put("basePrice_" + suffix, centsToCurrency(price.getBaseAmountCents()));
            values.put("perSeatPrice_" + suffix, centsToCurrency(price.getPerSeatAmountCents()));
        });

        if (!values.containsKey("basePrice")) {
            Price preferred = priceByPeriod.getOrDefault(BillingPeriod.MONTH,
                    priceByPeriod.values().stream().findFirst().orElse(null));
            if (preferred != null) {
                values.put("basePrice", centsToCurrency(preferred.getBaseAmountCents()));
            }
        }
        if (!values.containsKey("perSeatPrice")) {
            Price preferred = priceByPeriod.getOrDefault(BillingPeriod.MONTH,
                    priceByPeriod.values().stream().findFirst().orElse(null));
            if (preferred != null) {
                values.put("perSeatPrice", centsToCurrency(preferred.getPerSeatAmountCents()));
            }
        }

        return values;
    }

    private void putIfNotNull(Map<String, Object> map, String key, BigDecimal value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private long toMinorUnits(BigDecimal amount, String label) {
        if (amount == null) {
            throw new PlanValidationException(label + " must be provided");
        }
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();
        } catch (ArithmeticException ex) {
            throw new PlanValidationException(label + " must have at most two decimal places");
        }
    }

    private BigDecimal centsToCurrency(Long cents) {
        if (cents == null) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(cents, 2);
    }

    private BigDecimal firstNonNull(BigDecimal first, BigDecimal fallback) {
        return first != null ? first : fallback;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        return value.trim();
    }
}
