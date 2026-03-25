package com.majordomo.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * ArchUnit tests enforcing hexagonal architecture boundaries.
 * Violations fail the build.
 */
@AnalyzeClasses(packages = "com.majordomo")
class HexagonalArchitectureTest {

    /** Domain model must not depend on Spring framework. */
    @ArchTest
    static final ArchRule domainModelDoesNotDependOnSpring = noClasses()
            .that().resideInAPackage("..domain.model..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework..");

    /** Domain model must not depend on JPA/Hibernate. */
    @ArchTest
    static final ArchRule domainModelDoesNotDependOnJpa = noClasses()
            .that().resideInAPackage("..domain.model..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "jakarta.persistence..", "org.hibernate..");

    /** Domain ports must not depend on adapter packages. */
    @ArchTest
    static final ArchRule domainPortsDoNotDependOnAdapters = noClasses()
            .that().resideInAPackage("..domain.port..")
            .should().dependOnClassesThat().resideInAPackage("..adapter..");

    /** Application services must not depend on adapter packages. */
    @ArchTest
    static final ArchRule applicationDoesNotDependOnAdapters = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..adapter..");

    /** Persistence adapters must not depend on inbound adapters. */
    @ArchTest
    static final ArchRule adaptersDontCrossReference = noClasses()
            .that().resideInAPackage("..adapter.out..")
            .should().dependOnClassesThat().resideInAPackage("..adapter.in..");

    /** No circular dependencies between top-level slices. */
    @ArchTest
    static final ArchRule noCyclicDependencies = slices()
            .matching("com.majordomo.(*)..")
            .should().beFreeOfCycles();
}
