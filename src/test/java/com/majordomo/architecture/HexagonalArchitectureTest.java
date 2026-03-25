package com.majordomo.architecture;

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
    static final ArchRule DOMAIN_MODEL_NO_SPRING = noClasses()
            .that().resideInAPackage("..domain.model..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework..");

    /** Domain model must not depend on JPA/Hibernate. */
    @ArchTest
    static final ArchRule DOMAIN_MODEL_NO_JPA = noClasses()
            .that().resideInAPackage("..domain.model..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "jakarta.persistence..", "org.hibernate..");

    /** Domain ports must not depend on adapter packages. */
    @ArchTest
    static final ArchRule DOMAIN_PORTS_NO_ADAPTERS = noClasses()
            .that().resideInAPackage("..domain.port..")
            .should().dependOnClassesThat().resideInAPackage("..adapter..");

    /** Application services must not depend on adapter packages. */
    @ArchTest
    static final ArchRule APPLICATION_NO_ADAPTERS = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..adapter..");

    /** Persistence adapters must not depend on inbound adapters. */
    @ArchTest
    static final ArchRule ADAPTERS_NO_CROSS_REFERENCE = noClasses()
            .that().resideInAPackage("..adapter.out..")
            .should().dependOnClassesThat().resideInAPackage("..adapter.in..");

    /** No circular dependencies between top-level slices. */
    @ArchTest
    static final ArchRule NO_CYCLIC_DEPENDENCIES = slices()
            .matching("com.majordomo.(*)..")
            .should().beFreeOfCycles();
}
