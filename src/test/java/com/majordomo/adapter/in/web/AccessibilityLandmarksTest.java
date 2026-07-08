package com.majordomo.adapter.in.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Structural accessibility guardrails for the Thymeleaf templates (#294). Enforces
 * the invariants from {@code doc/accessibility.md} across every full-page template
 * so a new page can't silently drop them: a language, a single named main landmark,
 * and an {@code <h1>}. The shared skip link and primary-nav name are checked on the
 * fragments that provide them.
 *
 * <p>Reads the templates from source rather than rendering them — the invariants are
 * literal in the markup — so it is fast and needs no Spring context.</p>
 */
class AccessibilityLandmarksTest {

    private static final Path TEMPLATES = Path.of("src/main/resources/templates");

    private List<Path> fullPageTemplates() throws IOException {
        try (Stream<Path> paths = Files.walk(TEMPLATES)) {
            return paths
                    .filter(p -> p.toString().endsWith(".html"))
                    .filter(p -> !p.toString().contains("fragments"))
                    .filter(this::isFullDocument)
                    .toList();
        }
    }

    private boolean isFullDocument(Path p) {
        return read(p).contains("<html");
    }

    private static String read(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void everyFullPageDeclaresLanguage() throws IOException {
        for (Path page : fullPageTemplates()) {
            assertThat(read(page))
                    .as("%s must set <html lang=...>", page.getFileName())
                    .containsPattern("<html[^>]*lang=");
        }
    }

    @Test
    void everyFullPageHasANamedMainLandmark() throws IOException {
        for (Path page : fullPageTemplates()) {
            assertThat(read(page))
                    .as("%s must have <main id=\"main-content\"> (skip-link target)", page.getFileName())
                    .contains("<main id=\"main-content\"");
        }
    }

    @Test
    void everyFullPageHasATopLevelHeading() throws IOException {
        for (Path page : fullPageTemplates()) {
            assertThat(read(page))
                    .as("%s must have an <h1>", page.getFileName())
                    .contains("<h1");
        }
    }

    @Test
    void headerFragmentProvidesSkipLink() {
        assertThat(read(TEMPLATES.resolve("fragments/header.html")))
                .contains("href=\"#main-content\"")
                .contains("Skip to main content");
    }

    @Test
    void primaryNavigationHasAccessibleName() {
        assertThat(read(TEMPLATES.resolve("fragments/sidebar.html")))
                .containsPattern("<nav[^>]*aria-label=");
    }
}
