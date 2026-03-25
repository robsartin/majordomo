package com.majordomo;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test verifying Flyway migrations run successfully against real PostgreSQL.
 */
@IntegrationTest
@AutoConfigureMockMvc
class FlywayMigrationIntegrationTest {

    @Autowired
    private Flyway flyway;

    /** All migrations should be applied successfully. */
    @Test
    void allMigrationsApplied() {
        var info = flyway.info();
        var applied = info.applied();
        assertTrue(applied.length > 0, "At least one migration should be applied");
        for (var migration : applied) {
            assertTrue(migration.getState().isApplied(),
                    "Migration " + migration.getVersion() + " should be applied");
        }
    }
}
