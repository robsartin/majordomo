package com.majordomo.architecture;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The integration tests have to actually run somewhere (#344).
 *
 * <p>They live behind the {@code integration-tests} profile, so a plain
 * {@code mvn verify} skips them and reports success. That is how they went
 * unrun in CI for months: nothing failed, there was simply no evidence either
 * way — including for {@code BackupRestoreIntegrationTest}, which is the only
 * thing standing behind ADR-0025's claim that a backup can be restored.
 *
 * <p>The workflow is parsed rather than searched. A commented-out step is
 * absent from the parsed document but still present in the file, so text
 * matching would keep passing after someone disabled the job.
 */
class CiCoverageTest {

    private static final Path WORKFLOW = Path.of(".github/workflows/ci.yml");

    private static final String PROFILE = "-Pintegration-tests";

    @Test
    void ci_runsTheIntegrationTestProfile() throws IOException {
        assertThat(commandsRunByCi())
                .as("a step running %s in %s", PROFILE, WORKFLOW)
                .anyMatch(command -> command.contains(PROFILE));
    }

    /** Every {@code run:} script across every job, in workflow order. */
    private static List<String> commandsRunByCi() throws IOException {
        Map<String, Object> workflow;
        try (var in = Files.newInputStream(WORKFLOW)) {
            workflow = new Yaml().load(in);
        }
        Object jobs = workflow.get("jobs");
        assertThat(jobs).as("jobs in %s", WORKFLOW).isInstanceOf(Map.class);

        return ((Map<?, ?>) jobs).values().stream()
                .filter(Map.class::isInstance)
                .map(job -> ((Map<?, ?>) job).get("steps"))
                .filter(List.class::isInstance)
                .flatMap(steps -> ((List<?>) steps).stream())
                .filter(Map.class::isInstance)
                .map(step -> ((Map<?, ?>) step).get("run"))
                .filter(java.util.Objects::nonNull)
                .map(Object::toString)
                .toList();
    }
}
