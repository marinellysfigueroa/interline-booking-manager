package com.insightdevelop.interline.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Reglas de la arquitectura hexagonal, verificadas en cada {@code mvn test}.
 * (En Spring se usa exactamente igual: ArchUnit no depende del framework.)
 */
@AnalyzeClasses(packages = "com.insightdevelop.interline", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

    private static final String BASE = "com.insightdevelop.interline";

    @ArchTest
    static final ArchRule domain_is_framework_free = noClasses()
            .that().resideInAPackage(BASE + ".domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "jakarta..", "javax..", "io.quarkus..", "io.smallrye..", "org.hibernate..",
                    "com.fasterxml..", "org.eclipse.microprofile..", "io.vertx..")
            .because("el dominio es Java puro (ADR 0001, D2)");

    @ArchTest
    static final ArchRule domain_does_not_depend_on_outer_layers = noClasses()
            .that().resideInAPackage(BASE + ".domain..")
            .should().dependOnClassesThat().resideInAnyPackage(BASE + ".application..", BASE + ".infrastructure..");

    @ArchTest
    static final ArchRule application_does_not_depend_on_infrastructure = noClasses()
            .that().resideInAPackage(BASE + ".application..")
            .should().dependOnClassesThat().resideInAPackage(BASE + ".infrastructure..");

    @ArchTest
    static final ArchRule rest_dtos_stay_in_the_rest_adapter = noClasses()
            .that().resideOutsideOfPackage(BASE + ".infrastructure.rest..")
            .should().dependOnClassesThat().resideInAPackage(BASE + ".infrastructure.rest.dto..")
            .because("los DTO generados del contrato solo existen en el borde HTTP");

    @ArchTest
    static final ArchRule persistence_entities_stay_in_the_persistence_adapter = noClasses()
            .that().resideOutsideOfPackage(BASE + ".infrastructure..")
            .should().dependOnClassesThat().resideInAPackage(BASE + ".infrastructure.persistence.entity..");

    @ArchTest
    static final ArchRule domain_packages_are_free_of_cycles = slices()
            .matching(BASE + ".domain.(*)..")
            .should().beFreeOfCycles();
}
