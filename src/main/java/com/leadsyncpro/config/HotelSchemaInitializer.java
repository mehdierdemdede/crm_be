package com.leadsyncpro.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Ensures legacy databases receive the {@code hotels.currency} column that newer
 * application versions expect. This allows environments that were provisioned
 * before the currency field existed to keep working without manual migration.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HotelSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        if (!hotelTableExists()) {
            log.debug("Skipping hotel currency migration because the hotels table is missing");
            return;
        }

        addCurrencyColumnIfMissing();
        normalizeCurrencyValues();
        enforceCurrencyConstraints();
    }

    private boolean hotelTableExists() {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (" +
                        "SELECT 1 FROM information_schema.tables WHERE LOWER(table_name) = 'hotels')",
                Boolean.class
        );
        return Boolean.TRUE.equals(exists);
    }

    private void addCurrencyColumnIfMissing() {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (" +
                        "SELECT 1 FROM information_schema.columns " +
                        "WHERE LOWER(table_name) = 'hotels' AND LOWER(column_name) = 'currency')",
                Boolean.class
        );

        if (Boolean.TRUE.equals(exists)) {
            return;
        }

        jdbcTemplate.execute("ALTER TABLE hotels ADD COLUMN currency VARCHAR(3)");
        log.info("Added missing currency column to hotels table");
    }

    private void normalizeCurrencyValues() {
        Boolean needsUpdate = jdbcTemplate.queryForObject(
                "SELECT EXISTS (" +
                        "SELECT 1 FROM hotels WHERE currency IS NULL OR TRIM(currency) = '' " +
                        "OR currency <> UPPER(TRIM(currency)) OR LENGTH(TRIM(currency)) <> 3)",
                Boolean.class
        );

        if (!Boolean.TRUE.equals(needsUpdate)) {
            return;
        }

        int rows = jdbcTemplate.update(
                "UPDATE hotels " +
                        "SET currency = COALESCE(NULLIF(LEFT(UPPER(TRIM(currency)), 3), ''), 'EUR') " +
                        "WHERE currency IS NULL OR TRIM(currency) = '' " +
                        "OR currency <> UPPER(TRIM(currency)) OR LENGTH(TRIM(currency)) <> 3"
        );

        if (rows > 0) {
            log.info("Normalized currency values for {} hotel record(s)", rows);
        }
    }

    private void enforceCurrencyConstraints() {
        jdbcTemplate.execute("ALTER TABLE hotels ALTER COLUMN currency SET DEFAULT 'EUR'");
        jdbcTemplate.execute("ALTER TABLE hotels ALTER COLUMN currency SET NOT NULL");
    }
}
