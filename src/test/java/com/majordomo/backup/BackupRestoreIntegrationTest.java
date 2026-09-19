package com.majordomo.backup;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trip test for the backup and restore scripts (#337).
 *
 * <p>A backup nobody has restored is a hypothesis. This takes a real backup of
 * a populated database, restores it into a <em>different, empty</em> one, and
 * asserts the data came back — the only evidence that distinguishes a working
 * backup from a file of the right size.
 *
 * <p>It drives the shipped image rather than the host's tools, so what is
 * tested is what runs on the home server: same {@code pg_dump} major version as
 * the server, same {@code age}, same scripts.
 */
@Testcontainers
class BackupRestoreIntegrationTest {

    private static final Network NETWORK = Network.newNetwork();

    private static final String PASSWORD = "backup-test-password";

    @Container
    private static final PostgreSQLContainer<?> SOURCE = postgres("source-db");

    @Container
    private static final PostgreSQLContainer<?> TARGET = postgres("target-db");

    @Container
    private static final GenericContainer<?> TOOLS = new GenericContainer<>(
            new ImageFromDockerfile()
                    .withFileFromPath("Dockerfile", Path.of("docker/backup/Dockerfile"))
                    .withFileFromPath("scripts", Path.of("scripts")))
            .withNetwork(NETWORK)
            .withCommand("sleep", "infinity");

    private static String recipient;

    private static PostgreSQLContainer<?> postgres(String alias) {
        return new PostgreSQLContainer<>("postgres:18")
                .withNetwork(NETWORK)
                .withNetworkAliases(alias)
                .withDatabaseName("majordomo")
                .withUsername("majordomo")
                .withPassword(PASSWORD);
    }

    @BeforeAll
    static void setUp() throws Exception {
        recipient = run(TOOLS, "sh", "-c",
                "age-keygen -o /identity.age 2>/dev/null; age-keygen -y /identity.age").trim();
        assertThat(recipient).startsWith("age1");

        run(TOOLS, "sh", "-c", "mkdir -p /attachments/org-1 "
                + "&& echo 'boiler manual' > /attachments/org-1/manual.pdf");

        sql(SOURCE, "CREATE TABLE books (id int primary key, title text not null)");
        sql(SOURCE, "INSERT INTO books VALUES (1, 'The Left Hand of Darkness')");
    }

    /** A backup is one encrypted archive carrying both the database and the files. */
    @Test
    void backup_producesAnEncryptedArchiveOfTheDatabaseAndAttachments() throws Exception {
        backup();

        String archive = latestArchive();
        assertThat(archive).endsWith(".tar.age");

        String contents = run(TOOLS, "sh", "-c",
                "age -d -i /identity.age '" + archive + "' | tar -t");
        assertThat(contents.lines()).contains("database.dump", "attachments.tar");
    }

    /** The restore has to put the data into an empty database, not just exit zero. */
    @Test
    void restore_bringsBackTheDataIntoAnEmptyDatabase() throws Exception {
        backup();

        String output = run(TOOLS, "sh", "-c",
                "PGHOST=target-db PGUSER=majordomo PGPASSWORD=" + PASSWORD
                        + " PGDATABASE=majordomo"
                        + " BACKUP_AGE_IDENTITY=/identity.age"
                        + " ATTACHMENTS_DIR=/restored-attachments"
                        + " /usr/local/bin/restore.sh '" + latestArchive() + "'");
        assertThat(output).doesNotContain("ERROR");

        assertThat(sql(TARGET, "SELECT title FROM books WHERE id = 1"))
                .contains("The Left Hand of Darkness");
        assertThat(run(TOOLS, "cat", "/restored-attachments/org-1/manual.pdf"))
                .contains("boiler manual");
    }

    private static void backup() throws Exception {
        run(TOOLS, "sh", "-c",
                "PGHOST=source-db PGUSER=majordomo PGPASSWORD=" + PASSWORD
                        + " PGDATABASE=majordomo"
                        + " BACKUP_AGE_RECIPIENT=" + recipient
                        + " BACKUP_DIR=/backups ATTACHMENTS_DIR=/attachments"
                        + " /usr/local/bin/backup.sh");
    }

    private static String latestArchive() throws Exception {
        return run(TOOLS, "sh", "-c", "ls -1 /backups/*.tar.age | tail -1").trim();
    }

    /** Runs a command, failing the test with both streams if it exits non-zero. */
    private static String run(GenericContainer<?> container, String... command) throws Exception {
        var result = container.execInContainer(command);
        assertThat(result.getExitCode())
                .as("%s%nstdout: %s%nstderr: %s",
                        String.join(" ", command), result.getStdout(), result.getStderr())
                .isZero();
        return result.getStdout();
    }

    private static String sql(PostgreSQLContainer<?> db, String statement) throws Exception {
        return run(db, "psql", "-U", "majordomo", "-d", "majordomo", "-c", statement);
    }
}
