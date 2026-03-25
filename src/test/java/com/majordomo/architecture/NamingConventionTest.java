package com.majordomo.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * ArchUnit tests enforcing naming conventions across the codebase.
 */
@AnalyzeClasses(packages = "com.majordomo")
class NamingConventionTest {

    /** Application service classes should end with Service. */
    @ArchTest
    static final ArchRule APPLICATION_CLASSES_NAMED_SERVICE = classes()
            .that().resideInAPackage("..application..")
            .should().haveSimpleNameEndingWith("Service");
}
