package com.majordomo.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * ArchUnit tests enforcing naming conventions across the codebase.
 */
class NamingConventionTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter().importPackages("com.majordomo");
    }

    /** Application service classes should end with Service. */
    @Test
    void applicationClassesNamedService() {
        ArchRule rule = classes()
                .that().resideInAPackage("..application..")
                .should().haveSimpleNameEndingWith("Service");
        rule.check(classes);
    }
}
