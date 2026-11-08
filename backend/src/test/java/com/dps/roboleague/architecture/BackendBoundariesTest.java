package com.dps.roboleague.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Los detalles apuntan al negocio, nunca al revés: el dominio no conoce los adaptadores, la configuración ni la
 * infraestructura del backend, y los adaptadores web de cada recurso no se enredan entre sí. Los adaptadores de
 * entrada (REST y GraphQL) no se conocen entre sí y sólo llegan al núcleo por sus puertos de entrada.
 */
@AnalyzeClasses(packages = "com.dps.roboleague", importOptions = ImportOption.DoNotIncludeTests.class)
class BackendBoundariesTest {

    private static final String[] CONTEXTS = {
        "com.dps.roboleague.shared..", "com.dps.roboleague.enrollment..", "com.dps.roboleague.rules..",
        "com.dps.roboleague.scheduling..", "com.dps.roboleague.results..", "com.dps.roboleague.appeals.."
    };

    @ArchTest
    static final ArchRule theBusinessDoesNotKnowTheBackend = noClasses()
            .that().resideInAnyPackage(CONTEXTS)
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.dps.roboleague.adapter..", "com.dps.roboleague.config..",
                    "com.dps.roboleague.infrastructure..", "com.dps.roboleague.demo..",
                    "org.springframework..", "com.fasterxml..", "jakarta..");

    @ArchTest
    static final ArchRule theWebAdaptersAreNotCyclic = slices()
            .matching("com.dps.roboleague.adapter.in.web.(*)..")
            .should().beFreeOfCycles();

    @ArchTest
    static final ArchRule theWebAdaptersDoNotTouchRepositoriesOrInfrastructure = noClasses()
            .that().resideInAPackage("com.dps.roboleague.adapter.in.web..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.dps.roboleague.infrastructure..", "com.dps.roboleague.config..");

    @ArchTest
    static final ArchRule theGraphQlAdaptersAreNotCyclic = slices()
            .matching("com.dps.roboleague.adapter.in.graphql.(*)..")
            .should().beFreeOfCycles();

    @ArchTest
    static final ArchRule theGraphQlAdaptersDoNotTouchInfrastructureOrConfiguration = noClasses()
            .that().resideInAPackage("com.dps.roboleague.adapter.in.graphql..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.dps.roboleague.infrastructure..", "com.dps.roboleague.config..");

    @ArchTest
    static final ArchRule theEntryAdaptersDoNotKnowEachOther = slices()
            .matching("com.dps.roboleague.adapter.in.(*)..")
            .should().notDependOnEachOther();

    @ArchTest
    static final ArchRule theEntryAdaptersOnlyReachTheCoreThroughInputPorts = noClasses()
            .that().resideInAPackage("com.dps.roboleague.adapter.in..")
            .should().dependOnClassesThat().resideInAPackage("com.dps.roboleague.*.application..")
            .orShould().dependOnClassesThat().haveSimpleNameEndingWith("UseCase")
            .orShould().dependOnClassesThat().haveSimpleNameEndingWith("Repository");
}
