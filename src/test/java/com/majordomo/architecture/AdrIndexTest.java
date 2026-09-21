package com.majordomo.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every ADR has to appear in the index {@code CLAUDE.md} carries.
 *
 * <p>That table is maintained by hand, one row per ADR, and nothing has ever
 * checked it. An ADR missing from it is invisible to anyone reading the project
 * guide rather than listing the directory — which, for a decision record, is
 * the same as not having written it.
 *
 * <p>The set is derived from the files on disk rather than listed here, so a
 * new ADR is covered the moment it exists.
 */
class AdrIndexTest {

    private static final Path ADR_DIR = Path.of("doc/adr");

    private static final Path INDEX = Path.of("CLAUDE.md");

    private static final Pattern NUMBER = Pattern.compile("^(\\d{4})-");

    @Test
    void everyAdr_appearsInTheIndex() throws IOException {
        String index = Files.readString(INDEX);

        assertThat(adrNumbers())
                .as("ADR files under %s", ADR_DIR)
                .isNotEmpty()
                .allSatisfy(number -> assertThat(index)
                        .as("a row for ADR %s in %s", number, INDEX)
                        .containsPattern("\\|\\s*" + number + "\\s*\\|"));
    }

    /** The four-digit number of every ADR file, derived from the directory. */
    private static List<String> adrNumbers() throws IOException {
        try (Stream<Path> files = Files.list(ADR_DIR)) {
            return files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".md"))
                    .map(NUMBER::matcher)
                    .filter(Matcher::find)
                    .map(matcher -> matcher.group(1))
                    .sorted()
                    .toList();
        }
    }
}
