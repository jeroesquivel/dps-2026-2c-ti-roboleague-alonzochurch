package com.dps.roboleague.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Límites entre los bounded contexts del core. Si alguien agrega un import que cruza un límite, el build falla.
 *
 * <pre>
 * shared  <-  enrollment  <-  rules  <-  scheduling  <-  results  <-  appeals
 * </pre>
 *
 * Cada contexto solo puede conocer a los que están a su izquierda. Cuando uno necesita algo de uno que está a su
 * derecha, define un puerto y lo implementa el otro (por ejemplo {@code EligibilityRequirementsSource} y
 * {@code PendingAppeals}).
 */
@AnalyzeClasses(packages = "com.dps.roboleague", importOptions = ImportOption.DoNotIncludeTests.class)
class ContextBoundariesTest {

    private static final String SHARED = "com.dps.roboleague.shared..";
    private static final String ENROLLMENT = "com.dps.roboleague.enrollment..";
    private static final String RULES = "com.dps.roboleague.rules..";
    private static final String SCHEDULING = "com.dps.roboleague.scheduling..";
    private static final String RESULTS = "com.dps.roboleague.results..";
    private static final String APPEALS = "com.dps.roboleague.appeals..";

    @ArchTest
    static final ArchRule contextsHaveNoCyclicDependencies = slices()
            .matching("com.dps.roboleague.(*)..")
            .should().beFreeOfCycles();

    @ArchTest
    static final ArchRule eachContextOnlyKnowsTheOnesBelowIt = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("shared").definedBy(SHARED)
            .layer("enrollment").definedBy(ENROLLMENT)
            .layer("rules").definedBy(RULES)
            .layer("scheduling").definedBy(SCHEDULING)
            .layer("results").definedBy(RESULTS)
            .layer("appeals").definedBy(APPEALS)
            .whereLayer("shared").mayNotAccessAnyLayer()
            .whereLayer("enrollment").mayOnlyAccessLayers("shared")
            .whereLayer("rules").mayOnlyAccessLayers("shared", "enrollment")
            .whereLayer("scheduling").mayOnlyAccessLayers("shared", "enrollment", "rules")
            .whereLayer("results").mayOnlyAccessLayers("shared", "enrollment", "rules", "scheduling")
            .whereLayer("appeals").mayOnlyAccessLayers("shared", "rules", "scheduling", "results");

    @ArchTest
    static final ArchRule theDomainDoesNotKnowTheUseCases = noClasses()
            .that().resideOutsideOfPackages("..application..", "..port.in..")
            .should().dependOnClassesThat().resideInAnyPackage("..application..", "..port.in..");

    @ArchTest
    static final ArchRule thePortsDoNotKnowTheirImplementations = noClasses()
            .that().resideInAPackage("..port.in..")
            .should().dependOnClassesThat().resideInAPackage("..application..");

    @ArchTest
    static final ArchRule theCoreKnowsNoFramework = noClasses()
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta..", "com.fasterxml..");
}
