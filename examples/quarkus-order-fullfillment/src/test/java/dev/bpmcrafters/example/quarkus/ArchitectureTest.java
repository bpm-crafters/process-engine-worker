package dev.bpmcrafters.example.quarkus;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

class ArchitectureTest {
  private final com.tngtech.archunit.core.domain.JavaClasses classes = new ClassFileImporter()
    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("dev.bpmcrafters.example.quarkus");

  @Test
  void domainAndApplicationAreFrameworkFree() {
    classes().that().resideInAnyPackage("..domain..", "..application..")
      .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "..domain..", "..application..")
      .check(classes);
    noClasses().that().resideInAPackage("..domain..")
      .should().dependOnClassesThat().resideOutsideOfPackages("java..", "..domain..").check(classes);
  }

  @Test
  void inputAdaptersUseInPortsRatherThanImplementationsOrOutputAdapters() {
    noClasses().that().resideInAPackage("..adapter.in..")
      .should().dependOnClassesThat().resideInAnyPackage("..application.usecase..", "..application.port.out..", "..adapter.out..")
      .check(classes);
    classes().that().resideInAPackage("..application.usecase..")
      .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "..domain..", "..application.port.in..", "..application.port.out..",
        "..application.usecase..")
      .check(classes);
  }
}
