package com.majordomo.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * ArchUnit tests enforcing hexagonal architecture boundaries.
 * Violations fail the build.
 *
 * <p>Uses plain JUnit Jupiter {@code @Test} methods rather than ArchUnit's
 * {@code @ArchTest} static-field discovery, which Surefire was silently
 * skipping (see issue #122).</p>
 */
class HexagonalArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.majordomo");
    }

    /** Domain model must not depend on Spring framework. */
    @Test
    void domainModelHasNoSpringDependency() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain.model..")
                .should().dependOnClassesThat().resideInAPackage("org.springframework..");
        rule.check(classes);
    }

    /** Domain model must not depend on JPA/Hibernate. */
    @Test
    void domainModelHasNoJpaDependency() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain.model..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "jakarta.persistence..", "org.hibernate..");
        rule.check(classes);
    }

    /** Domain ports must not depend on adapter packages. */
    @Test
    void domainPortsHaveNoAdapterDependency() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain.port..")
                .should().dependOnClassesThat().resideInAPackage("..adapter..");
        rule.check(classes);
    }

    /** Application services must not depend on adapter packages. */
    @Test
    void applicationHasNoAdapterDependency() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAPackage("..adapter..");
        rule.check(classes);
    }

    /** Persistence adapters must not depend on inbound adapters. */
    @Test
    void outboundAdaptersHaveNoInboundDependency() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..adapter.out..")
                .should().dependOnClassesThat().resideInAPackage("..adapter.in..");
        rule.check(classes);
    }

    /**
     * Inbound adapters (controllers) must not depend on outbound adapters
     * (persistence, notification, storage). They go through ports only
     * (ADR-0004).
     */
    @Test
    void inboundAdaptersHaveNoOutboundDependency() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..adapter.in..")
                .should().dependOnClassesThat().resideInAPackage("..adapter.out..");
        rule.check(classes);
    }

    /**
     * Production code must use {@code UuidFactory.newId()} (UUIDv7) instead of
     * {@code UUID.randomUUID()}, per ADR-0018. Tests are not analyzed
     * ({@code DoNotIncludeTests} above).
     */
    @Test
    void productionCodeMustNotCallUuidRandomUUID() {
        ArchRule rule = noClasses().should(callRandomUUID());
        rule.check(classes);
    }

    private static ArchCondition<JavaClass> callRandomUUID() {
        return new ArchCondition<>("call java.util.UUID.randomUUID") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                for (JavaMethodCall call : item.getMethodCallsFromSelf()) {
                    if ("java.util.UUID".equals(call.getTargetOwner().getFullName())
                            && "randomUUID".equals(call.getName())) {
                        events.add(SimpleConditionEvent.violated(item,
                                item.getName() + " calls UUID.randomUUID at "
                                        + call.getSourceCodeLocation()
                                        + " — use UuidFactory.newId()"));
                    }
                }
            }
        };
    }

    /** No circular dependencies between top-level slices. */
    @Test
    void slicesAreFreeOfCycles() {
        ArchRule rule = slices()
                .matching("com.majordomo.(*)..")
                .should().beFreeOfCycles();
        rule.check(classes);
    }
}
