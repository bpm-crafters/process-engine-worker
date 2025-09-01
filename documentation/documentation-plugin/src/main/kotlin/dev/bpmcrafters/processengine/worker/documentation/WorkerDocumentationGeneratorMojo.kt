package dev.bpmcrafters.processengine.worker.documentation

import dev.bpmcrafters.processengine.worker.documentation.core.*
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.logging.Log
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.plugins.annotations.ResolutionScope
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "generate", defaultPhase = LifecyclePhase.PROCESS_CLASSES, requiresDependencyResolution = ResolutionScope.RUNTIME)
class WorkerDocumentationGeneratorMojo: AbstractMojo() {

  private val log: Log = getLog()

  @Parameter(defaultValue = "\${project}", required = true, readonly = true)
  var project: MavenProject? = null

  /**
   * The target platform for which goal should be executed.
   */
  @Parameter(name = "targetPlatform", property = "elementtemplategen.targetPlatform", required = true)
  var targetPlatform: TargetPlattform? = null

  /**
   * Directory to output the generated element templates to.
   */
  @Parameter(property = "elementtemplategen.outputDirectory", defaultValue = "\${project.build.directory/generated-sources/element-templates}")
  var outputDirectory: File? = null

  /**
   * The naming policy for input values.
   * EMPTY => ${}
   * ATTRIBUTE_NAME => ${<attributeName>}
  </attributeName> */
  @Parameter(name = "inputValueNamingPolicy", property = "elementtemplategen.inputValueNamingPolicy", defaultValue = "EMPTY")
  var inputValueNamingPolicy: InputValueNamingPolicy? = null

  /**
   * A flag indicating if the output directory should be cleaned before generation.
   */
  @Parameter(name = "clean", property = "elementtemplategen.clean", defaultValue = "false")
  var clean: Boolean = false

  /**
   * Platform-specific configuration options
   */
  @Parameter
  var platformSpecificConfig: EngineSpecificConfig = EngineSpecificConfig()

  // Minimal factory hook for testing/injection
  var generatorFactory: (MavenProject, TargetPlattform, File, EngineSpecificConfig, InputValueNamingPolicy) -> WorkerDocumentationGenerator =
    { project, target, outputDir, cfg, naming -> WorkerDocumentationGenerator(project, target, outputDir, cfg, naming) }

  override fun execute() {
    log.info("Generating element templates for ${project?.artifactId}")

    val workerDocumentationGenerator = generatorFactory(
      requireNotNull(project),
      requireNotNull(targetPlatform),
      requireNotNull(outputDirectory),
      platformSpecificConfig,
      requireNotNull(inputValueNamingPolicy)
    )

    try {
      if (clean) {
        log.info("Cleaning output directory...")
        workerDocumentationGenerator.clean()
      }
      log.info("Generate worker documentation")
      workerDocumentationGenerator.generate()
    } catch (e: DocumentationFailedException) {
      log.error(e.message)
      log.debug(e)
      throw MojoExecutionException(e.message)
    }

  }

}
