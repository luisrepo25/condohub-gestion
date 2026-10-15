package com.condohub.gestion;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Reglas de dependencia de la arquitectura Hexagonal. No borrar (ver CLAUDE.md del servicio). */
@AnalyzeClasses(packages = "com.condohub.gestion", importOptions = ImportOption.DoNotIncludeTests.class)
class ArquitecturaTest {

    @ArchTest
    static final ArchRule el_dominio_solo_depende_del_dominio_y_java = classes()
            .that().resideInAPackage("..domain..")
            .should().onlyDependOnClassesThat().resideInAnyPackage("..domain..", "java..");

    @ArchTest
    static final ArchRule la_aplicacion_solo_depende_del_dominio = classes()
            .that().resideInAPackage("..application..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage("..application..", "..domain..", "java..", "org.springframework.transaction.annotation..");

    @ArchTest
    static final ArchRule dominio_y_aplicacion_no_conocen_los_adaptadores = noClasses()
            .that().resideInAnyPackage("..domain..", "..application..")
            .should().dependOnClassesThat().resideInAPackage("..adapter..");

    @ArchTest
    static final ArchRule el_dominio_no_tiene_anotaciones_de_persistencia = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..", "org.hibernate..");
}