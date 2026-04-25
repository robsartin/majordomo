package com.majordomo.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * ArchUnit tests enforcing naming conventions across the codebase.
 *
 * <p>Uses plain JUnit Jupiter {@code @Test} methods rather than ArchUnit's
 * {@code @ArchTest} static-field discovery, which Surefire was silently
 * skipping (see issue #122).</p>
 */
class NamingConventionTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.majordomo");
    }

    /**
     * Spring {@code @Service}-annotated classes in {@code ..application..}
     * should end with {@code Service}. Helper classes (assemblers, builders,
     * scorers), exceptions, value records, and tests don't carry the
     * annotation and are correctly excluded.
     */
    @Test
    void applicationServicesEndWithService() {
        ArchRule rule = classes()
                .that().resideInAPackage("..application..")
                .and().areAnnotatedWith("org.springframework.stereotype.Service")
                .should().haveSimpleNameEndingWith("Service");
        rule.check(classes);
    }
}
