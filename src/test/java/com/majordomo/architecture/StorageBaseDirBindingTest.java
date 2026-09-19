package com.majordomo.architecture;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Closes the gap {@link AttachmentStorageDurabilityTest} cannot see (#338).
 *
 * <p>That test proves {@code docker-compose.yml} mounts a volume at the
 * directory it tells the container to use. It cannot prove the application
 * agrees: the compose file sets {@code MAJORDOMO_STORAGE_BASE_DIR}, while
 * {@code LocalFileStorageAdapter} reads {@code majordomo.storage.base-dir} —
 * which {@code application.yml} already defines as {@code ./data/attachments}.
 *
 * <p>If Spring's relaxed binding did not connect those two names, or if the
 * bundled default outranked the environment, both files would still agree with
 * each other and the container would still write into its writable layer. The
 * suite would be green and the bug unfixed.
 *
 * <p>So this drives the real machinery: a {@link StandardEnvironment} with the
 * system-environment source replaced by a simulated one, then Boot's own
 * config-data processor layering {@code application.yml} beneath it at the
 * precedence it uses in production.
 *
 * <p>That file is named by path rather than left to the classpath on purpose.
 * {@code src/test/resources/application.yml} shadows the shipped one entirely —
 * first match wins, it is not a merge — and it says nothing about storage. Read
 * through the classpath the default would resolve to {@code null}, and the
 * override assertion would be comparing the environment against nothing.
 */
class StorageBaseDirBindingTest {

    private static final String PROPERTY = "majordomo.storage.base-dir";

    private static final String CONTAINER_DIR = "/var/lib/majordomo/attachments";

    @Test
    void environmentVariable_overridesTheBundledDefault() {
        assertThat(resolve(Map.of(
                AttachmentStorageDurabilityTest.STORAGE_DIR_VAR, CONTAINER_DIR)))
                .isEqualTo(CONTAINER_DIR);
    }

    /**
     * The control. With nothing set, the bundled default is what wins — which
     * is the state that lost the attachments, and proof the assertion above is
     * reading the environment rather than finding the answer either way.
     */
    @Test
    void bundledDefault_appliesWhenTheEnvironmentIsSilent() {
        assertThat(resolve(Map.of())).isEqualTo("./data/attachments");
    }

    /** Resolves {@link #PROPERTY} as the app would, given these environment variables. */
    private static String resolve(Map<String, Object> environmentVariables) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addLast(new MapPropertySource(
                "shippedConfig",
                Map.of("spring.config.location", "file:src/main/resources/application.yml")));
        environment.getPropertySources().replace(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                new SystemEnvironmentPropertySource(
                        StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        environmentVariables));
        ConfigDataEnvironmentPostProcessor.applyTo(environment);
        return environment.getProperty(PROPERTY);
    }
}
