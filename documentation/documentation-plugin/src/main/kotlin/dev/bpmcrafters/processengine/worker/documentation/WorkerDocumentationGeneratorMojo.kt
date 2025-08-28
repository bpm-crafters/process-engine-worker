package dev.bpmcrafters.processengine.worker.documentation

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.documentation.api.ProcessEngineWorkerDocumentation
import dev.bpmcrafters.processengine.worker.documentation.core.InputValueNamingPolicy
import dev.bpmcrafters.processengine.worker.documentation.core.TargetPlattform
import dev.bpmcrafters.processengine.worker.documentation.core.generator.DocumentationGenerator
import org.apache.commons.io.FileUtils
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.logging.Log
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.plugins.annotations.ResolutionScope
import org.apache.maven.project.MavenProject
import org.reflections.Reflections
import org.reflections.scanners.Scanners
import org.reflections.util.ClasspathHelper
import org.reflections.util.ConfigurationBuilder
import java.io.File
import java.io.IOException
import java.net.URL
import java.net.URLClassLoader

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

  override fun execute() {
    log.info("Generating element templates for ${project?.artifactId}")

    // Clean output directory
    if (clean) {
      try {
        log.info("Cleaning output directory...")
        FileUtils.deleteDirectory(outputDirectory)
      } catch(e: IOException) {
        log.error("Failed to clean output directory.", e)
        throw MojoExecutionException("Failed to clean output directory.", e)
      }
    }

    // Find annotated workers
    val classpathURLs = project!!.compileClasspathElements
      .map { File(it).toURI().toURL() }
    val urlClassLoader = URLClassLoader(classpathURLs.toTypedArray<URL?>(), javaClass.getClassLoader())
    val reflections = Reflections(
      ConfigurationBuilder()
        .setUrls(ClasspathHelper.forClassLoader(urlClassLoader))
        .addClassLoaders(urlClassLoader)
        .addScanners(Scanners.MethodsAnnotated)
    )
    val workers = reflections.getMethodsAnnotatedWith(ProcessEngineWorker::class.java)
    val generator = DocumentationGenerator(outputDirectory!!, targetPlatform!!, inputValueNamingPolicy!!)

    // generate documentation for each worker
    workers.forEach({
      val inputParams = it.parameterTypes

      if (inputParams.size > 1) {
        throw MojoExecutionException("Worker method ${it.name} has more than one parameter.")
      }

      try {
        val workerAnnotation = it.getAnnotation(ProcessEngineWorker::class.java)
        val documentationAnnotation = it.getAnnotation(ProcessEngineWorkerDocumentation::class.java)

        generator.generate(workerAnnotation, documentationAnnotation, inputParams.first(), it.returnType)
      } catch(e: NullPointerException) {
        log.error("Worker method ${it.name} is missing required annotation.", e)
        throw MojoExecutionException("Worker method ${it.name} is missing @ProcessEngineWorkerDocumentation annotation.")
      }
    })
  }

}
