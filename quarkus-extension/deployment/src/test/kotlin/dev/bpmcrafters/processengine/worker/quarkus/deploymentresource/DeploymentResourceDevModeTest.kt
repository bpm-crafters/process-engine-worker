package dev.bpmcrafters.processengine.worker.quarkus.deploymentresource

import io.quarkus.bootstrap.app.CuratedApplication
import io.quarkus.deployment.dev.DevModeMain
import io.quarkus.test.QuarkusDevModeTest
import org.assertj.core.api.Assertions.assertThat
import org.jboss.shrinkwrap.api.asset.StringAsset
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class DeploymentResourceDevModeTest {
  companion object {
    @JvmField @RegisterExtension
    val APP = QuarkusDevModeTest().withApplicationRoot { it.addClasses(ReloadRecordingDeploymentApi::class.java,
      ReloadRecordingDeploymentApi.Companion::class.java, ReloadRecordingDeploymentApi.Metadata::class.java)
      .addAsResource(StringAsset("initial"), "resource-reload/change.bpmn")
      .addAsResource(StringAsset("permanent"), "resource-reload/keep.dmn")
      .addAsResource(StringAsset("""
        quarkus.http.port=0
        dev.bpm-crafters.process-api.worker.enabled=false
        dev.bpm-crafters.process-api.worker.deployment.enabled=true
        dev.bpm-crafters.process-api.worker.deployment.bpmn-resource-pattern=classpath*:/resource-reload/*.bpmn
        dev.bpm-crafters.process-api.worker.deployment.dmn-resource-pattern=classpath*:/resource-reload/*.dmn
      """.trimIndent()), "application.properties") }

    @JvmStatic @AfterAll fun clearRecordings() {
      System.clearProperty(ReloadRecordingDeploymentApi.SNAPSHOT_PROPERTY)
      System.clearProperty(ReloadRecordingDeploymentApi.REVISION_PROPERTY)
    }
  }
  @Test fun resourceChangesAdditionsAndDeletionsRegenerateActualStartupDeploymentBundles() {
    assertThat(snapshot()).isEqualTo("{change.bpmn=initial, keep.dmn=permanent}")
    APP.modifyResourceFile("resource-reload/change.bpmn") { "changed" }
    reloadAndAssert("{change.bpmn=changed, keep.dmn=permanent}")
    APP.addResourceFile("resource-reload/added.bpmn", "added")
    reloadAndAssert("{added.bpmn=added, change.bpmn=changed, keep.dmn=permanent}")
    APP.deleteResourceFile("resource-reload/change.bpmn")
    // The nonempty next deployment proves deletion rebuilt the bundle rather than retaining stale bytes.
    reloadAndAssert("{added.bpmn=added, keep.dmn=permanent}")
  }
  private fun reloadAndAssert(expected: String) {
    val previous = revision()
    val loader = curatedApplication().augmentClassLoader
    val updates = loader.loadClass("io.quarkus.deployment.dev.RuntimeUpdatesProcessor")
    val processor = updates.getField("INSTANCE").get(null)
    assertThat(processor).isNotNull()
    val original = Thread.currentThread().contextClassLoader
    try {
      Thread.currentThread().contextClassLoader = loader
      assertThat(updates.getMethod("doScan", Boolean::class.javaPrimitiveType).invoke(processor, true)).isEqualTo(true)
      assertThat(updates.getMethod("getDeploymentProblem").invoke(processor)).isNull()
    } finally { Thread.currentThread().contextClassLoader = original }
    assertThat(revision()).isGreaterThan(previous)
    assertThat(snapshot()).isEqualTo(expected)
  }
  private fun curatedApplication(): CuratedApplication {
    // Harness helpers wait for filesystem timestamps, not reload completion. Invoke the real scanner.
    val mainField = QuarkusDevModeTest::class.java.getDeclaredField("devModeMain")
    mainField.isAccessible = true
    val main = mainField.get(APP)
    val applicationField = DevModeMain::class.java.getDeclaredField("curatedApplication")
    applicationField.isAccessible = true
    return applicationField.get(main) as CuratedApplication
  }
  private fun snapshot(): String? = System.getProperty(ReloadRecordingDeploymentApi.SNAPSHOT_PROPERTY)
  private fun revision() = System.getProperty(ReloadRecordingDeploymentApi.REVISION_PROPERTY).toInt()
}
