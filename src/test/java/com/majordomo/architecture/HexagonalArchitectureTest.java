package com.majordomo.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * ArchUnit tests enforcing hexagonal architecture boundaries.
 * Violations fail the build.
 */
class HexagonalArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter().importPackages("com.majordomo");
    }

    /** Domain model must not depend on Spring framework. */
    @Test
    void domainModelDoesNotDependOnSpring() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain.model..")
                .should().dependOnClassesThat()
                .resideInAPackage("org.springframework..");
        rule.check(classes);
    }

    /** Domain model must not depend on JPA/Hibernate. */
    @Test
    void domainModelDoesNotDependOnJpa() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain.model..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("jakarta.persistence..", "org.hibernate..");
        rule.check(classes);
    }

    /** Domain ports must not depend on adapter packages. */
    @Test
    void domainPortsDoNotDependOnAdapters() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain.port..")
                .should().dependOnClassesThat()
                .resideInAPackage("..adapter..");
        rule.check(classes);
    }

    /** Application services must not depend on adapter packages. */
    @Test
    void applicationDoesNotDependOnAdapters() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat()
                .resideInAPackage("..adapter..");
        rule.check(classes);
    }

    /** Adapters must not depend on other adapter sub-packages. */
    @Test
    void adaptersDontCrossReference() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..adapter.out.persistence..")
                .should().dependOnClassesThat()
                .resideInAPackage("..adapter.in..");
        rule.check(classes);
    }

    /** No circular dependencies between top-level slices. */
    @Test
    void noCyclicDependenciesBetweenSlices() {
        ArchRule rule = slices()
                .matching("com.majordomo.(*)..")
                .should().beFreeOfCycles();
        rule.check(classes);
    }
}
