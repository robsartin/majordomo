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
 * Fitness function for attachment durability across an upgrade (#338).
 *
 * <p>Attachments are the only durable state the app writes outside PostgreSQL.
 * They were written into the container's writable layer, so the documented
 * upgrade — {@code docker compose up -d --build} — destroyed them silently:
 * the {@code attachments} rows survive and go on pointing at files that are
 * no longer there.
 *
 * <p>The two facts that have to agree live in the same file, so the check is
 * decidable from it alone: the directory the container writes to, and the
 * directory it mounts. Pinning the write path to an absolute value is part of
 * the requirement rather than a detail — a relative path resolves against the
 * image's {@code WORKDIR}, which neither this test, a backup script, nor a
 * person reading the compose file can see.
 */
class AttachmentStorageDurabilityTest {

    private static final Path COMPOSE = Path.of("docker-compose.yml");

    /** Shared with {@link StorageBaseDirBindingTest}, which proves this name binds. */
    static final String STORAGE_DIR_VAR = "MAJORDOMO_STORAGE_BASE_DIR";

    /**
     * The container's attachment directory must be stated absolutely. Left
     * relative it resolves against {@code WORKDIR}, and nothing outside the
     * running container can tell where the files went.
     */
    @Test
    void appService_writesAttachmentsToAnAbsolutePath() throws IOException {
        assertThat(storageBaseDir())
                .as("%s on the app service in %s", STORAGE_DIR_VAR, COMPOSE)
                .isNotNull()
                .startsWith("/");
    }

    /** That directory must be a mount, or an upgrade takes the files with it. */
    @Test
    void appService_mountsAVolumeAtTheAttachmentDirectory() throws IOException {
        List<String> mounts = mountTargets();
        assertThat(mounts)
                .as("volumes declared on the app service in %s", COMPOSE)
                .isNotEmpty();

        String baseDir = storageBaseDir();
        assertThat(mounts)
                .as("a mount covering the attachment directory %s", baseDir)
                .anyMatch(target -> covers(target, baseDir));
    }

    /**
     * Whether a mount at {@code target} makes {@code dir} durable. A mount at
     * {@code /data} covers {@code /data} itself and {@code /data/attachments},
     * but a mount at {@code /dat} covers neither.
     */
    private static boolean covers(String target, String dir) {
        String mount = target.endsWith("/") ? target.substring(0, target.length() - 1) : target;
        return dir.equals(mount) || dir.startsWith(mount + "/");
    }

    private static String storageBaseDir() throws IOException {
        Object environment = appService().get("environment");
        if (!(environment instanceof Map<?, ?> env)) {
            return null;
        }
        Object value = env.get(STORAGE_DIR_VAR);
        return value == null ? null : value.toString();
    }

    /** Container-side path of each short-syntax {@code source:target[:mode]} mount. */
    private static List<String> mountTargets() throws IOException {
        Object volumes = appService().get("volumes");
        if (!(volumes instanceof List<?> declared)) {
            return List.of();
        }
        return declared.stream()
                .map(Object::toString)
                .map(mount -> mount.split(":"))
                .filter(parts -> parts.length >= 2)
                .map(parts -> parts[1])
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> appService() throws IOException {
        Map<String, Object> compose;
        try (var in = Files.newInputStream(COMPOSE)) {
            compose = new Yaml().load(in);
        }
        Map<String, Object> services = (Map<String, Object>) compose.get("services");
        return (Map<String, Object>) services.get("app");
    }
}
