package com.majordomo;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Meta-annotation for integration tests that run against a real PostgreSQL
 * instance via Testcontainers. Activates the {@code integration} profile
 * which configures Testcontainers JDBC driver and enables Flyway.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@ActiveProfiles("integration")
public @interface IntegrationTest {
}
