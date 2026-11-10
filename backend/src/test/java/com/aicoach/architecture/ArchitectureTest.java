package com.aicoach.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

/**
 * Executable architecture rules (docs/06-folder-structure.md). Layering inside each feature:
 * web → service → repository → domain. Rules that target layers which do not exist yet use
 * {@code allowEmptyShould(true)} so they start enforcing as soon as the first class appears.
 */
class ArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.aicoach");
    }

    @Test
    void controllersLiveInWebPackages() {
        ArchRule rule = classes().that().areAnnotatedWith(RestController.class)
                .should().resideInAPackage("..web..");
        rule.check(classes);
    }

    @Test
    void controllersDoNotUseRepositoriesOrEntitiesDirectly() {
        ArchRule rule = noClasses().that().areAnnotatedWith(RestController.class)
                .should().dependOnClassesThat().resideInAPackage("..repository..")
                .orShould().dependOnClassesThat().areAnnotatedWith(Entity.class)
                .because("controllers speak DTOs; entities never leave the service layer");
        rule.check(classes);
    }

    @Test
    void featuresDoNotReachIntoOtherFeaturesRepositories() {
        ArchRule rule = noClasses().that().resideInAPackage("com.aicoach.auth..")
                .should().dependOnClassesThat().resideInAPackage("com.aicoach.user.repository..")
                .orShould().dependOnClassesThat().resideInAPackage("com.aicoach.audit.repository..");
        rule.check(classes);
        noClasses().that().resideInAPackage("com.aicoach.user..")
                .should().dependOnClassesThat().resideInAPackage("com.aicoach.auth.repository..")
                .orShould().dependOnClassesThat().resideInAPackage("com.aicoach.audit.repository..")
                .check(classes);
    }

    @Test
    void repositoriesAreOnlyUsedByServicesOrOtherRepositories() {
        ArchRule rule = classes().that().resideInAPackage("..repository..")
                .should().onlyBeAccessed().byAnyPackage("..service..", "..repository..", "..job..")
                .allowEmptyShould(true);
        rule.check(classes);
    }

    @Test
    void servicesDoNotDependOnWebLayer() {
        ArchRule rule = noClasses().that().resideInAPackage("..service..")
                .should().dependOnClassesThat().areAnnotatedWith(RestController.class)
                .allowEmptyShould(true);
        rule.check(classes);
    }

    @Test
    void transactionsAreDemarcatedOnlyInServicesAndJobs() {
        ArchRule rule = methods().that().areAnnotatedWith(Transactional.class)
                .should().beDeclaredInClassesThat().resideInAnyPackage("..service..", "..job..")
                .allowEmptyShould(true);
        rule.check(classes);
    }

    @Test
    void candidateCodeIsNeverExecutedOnTheApiHost() {
        ArchRule rule = noClasses()
                .should().dependOnClassesThat().haveFullyQualifiedName(ProcessBuilder.class.getName())
                .orShould().callMethod(Runtime.class, "exec", String.class)
                .orShould().callMethod(Runtime.class, "exec", String[].class)
                .orShould().dependOnClassesThat().resideInAnyPackage("javax.script..", "jdk.jshell..")
                .because("user code must only run inside the Judge0 sandbox (docs/09-risks.md R6)");
        rule.check(classes);
    }
}
