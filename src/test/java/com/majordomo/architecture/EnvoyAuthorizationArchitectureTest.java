package com.majordomo.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

/**
 * ArchUnit fitness function gating new {@code /envoy} controller routes (issue #174).
 *
 * <p>Every HTTP-mapped method on a {@code @Controller} or {@code @RestController}
 * residing under {@code com.majordomo.adapter.in.web.envoy..} must satisfy at least
 * one of:
 *
 * <ul>
 *   <li>The owning class declares a field of type
 *       {@link com.majordomo.application.identity.OrganizationAccessService}.</li>
 *   <li>The method takes a parameter annotated
 *       {@code @org.springframework.security.core.annotation.AuthenticationPrincipal}.</li>
 * </ul>
 *
 * <p>HTTP-mapped means annotated with {@code @RequestMapping},
 * {@code @GetMapping}, {@code @PostMapping}, {@code @PutMapping},
 * {@code @PatchMapping}, or {@code @DeleteMapping}.
 *
 * <p>This rule does <em>not</em> verify the implementation actually calls
 * {@code verifyAccess} or actually uses the principal &mdash; that is a code
 * review concern. What it does prevent is silently shipping a new {@code /envoy}
 * route that has neither hook in place at all.
 *
 * <p>Uses plain JUnit Jupiter {@code @Test} methods rather than ArchUnit's
 * {@code @ArchTest} static-field discovery, which Surefire was silently
 * skipping (see issue #122).
 */
class EnvoyAuthorizationArchitectureTest {

    private static final String CONTROLLER_ANNOTATION =
            "org.springframework.stereotype.Controller";
    private static final String REST_CONTROLLER_ANNOTATION =
            "org.springframework.web.bind.annotation.RestController";

    private static final String AUTHENTICATION_PRINCIPAL_ANNOTATION =
            "org.springframework.security.core.annotation.AuthenticationPrincipal";

    private static final String ORG_ACCESS_SERVICE =
            "com.majordomo.application.identity.OrganizationAccessService";

    private static final Set<String> HTTP_MAPPING_ANNOTATIONS = Set.of(
            "org.springframework.web.bind.annotation.RequestMapping",
            "org.springframework.web.bind.annotation.GetMapping",
            "org.springframework.web.bind.annotation.PostMapping",
            "org.springframework.web.bind.annotation.PutMapping",
            "org.springframework.web.bind.annotation.PatchMapping",
            "org.springframework.web.bind.annotation.DeleteMapping");

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.majordomo");
    }

    /**
     * Every HTTP-mapped method on an /envoy controller must be gated by
     * {@code OrganizationAccessService} on the owning class or
     * {@code @AuthenticationPrincipal} on the method.
     */
    @Test
    void everyEnvoyControllerRouteIsAuthorized() {
        ArchRule rule = methods()
                .that(areDeclaredInEnvoyController())
                .and(areHttpMapped())
                .should(beGatedByOrgAccessOrAuthenticationPrincipal());

        rule.check(classes);
    }

    private static DescribedPredicate<JavaMethod> areDeclaredInEnvoyController() {
        return new DescribedPredicate<>(
                "declared in an envoy @Controller or @RestController") {
            @Override
            public boolean test(JavaMethod method) {
                JavaClass owner = method.getOwner();
                if (!owner.getPackageName()
                        .startsWith("com.majordomo.adapter.in.web.envoy")) {
                    return false;
                }
                return owner.isAnnotatedWith(CONTROLLER_ANNOTATION)
                        || owner.isAnnotatedWith(REST_CONTROLLER_ANNOTATION);
            }
        };
    }

    private static DescribedPredicate<JavaMethod> areHttpMapped() {
        return new DescribedPredicate<>("annotated with a Spring HTTP mapping") {
            @Override
            public boolean test(JavaMethod method) {
                return method.getAnnotations().stream()
                        .anyMatch(a -> HTTP_MAPPING_ANNOTATIONS.contains(
                                a.getRawType().getFullName()));
            }
        };
    }

    private static ArchCondition<JavaMethod> beGatedByOrgAccessOrAuthenticationPrincipal() {
        return new ArchCondition<>(
                "have an OrganizationAccessService field on the owner OR an "
                        + "@AuthenticationPrincipal parameter") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                boolean ownerHasOrgAccess = ownerHasOrgAccessField(method.getOwner());
                boolean methodHasPrincipal = methodHasAuthenticationPrincipalParam(method);

                if (ownerHasOrgAccess || methodHasPrincipal) {
                    return;
                }

                String message = String.format(
                        "Method %s.%s on /envoy controller is not gated: "
                                + "owner has no OrganizationAccessService field "
                                + "and method has no @AuthenticationPrincipal "
                                + "parameter (declared in %s)",
                        method.getOwner().getSimpleName(),
                        method.getName(),
                        method.getSourceCodeLocation());
                events.add(SimpleConditionEvent.violated(method, message));
            }
        };
    }

    private static boolean ownerHasOrgAccessField(JavaClass owner) {
        for (JavaField field : owner.getAllFields()) {
            if (field.getRawType().getFullName().equals(ORG_ACCESS_SERVICE)) {
                return true;
            }
        }
        return false;
    }

    private static boolean methodHasAuthenticationPrincipalParam(JavaMethod method) {
        return method.getParameters().stream()
                .anyMatch(p -> p.isAnnotatedWith(AUTHENTICATION_PRINCIPAL_ANNOTATION));
    }
}
