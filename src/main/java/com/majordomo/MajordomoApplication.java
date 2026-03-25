package com.majordomo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Majordomo Spring Boot application.
 *
 * <p>Majordomo is a personal information and property management system built
 * on a hexagonal architecture. Domain logic is isolated from infrastructure
 * concerns; inbound REST adapters delegate to domain ports rather than
 * accessing persistence or external services directly.</p>
 */
@SpringBootApplication
public class MajordomoApplication {

    /**
     * Bootstraps the Spring application context and starts the embedded server.
     *
     * @param args command-line arguments passed through to Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(MajordomoApplication.class, args);
    }

}
