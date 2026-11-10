package dev.bpmcrafters.processengine.worker.quarkus.deployment

import dev.bpmcrafters.processengine.worker.quarkus.deployment.vetoedfixture.PackageVetoedWorker
import dev.bpmcrafters.processengine.worker.quarkus.deployment.vetoedfixture.active.ActiveSubpackageWorker
import dev.bpmcrafters.processengine.worker.quarkus.deployment.vetoedfixture.active.ActiveVetoedBaseSubclass
import dev.bpmcrafters.processengine.worker.quarkus.deployment.vetoedfixture.active.VetoedWorkerBase
import io.quarkus.arc.Arc
import io.quarkus.test.QuarkusExtensionTest
import jakarta.enterprise.inject.Vetoed
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class ActiveCounterpartsOfVetoedWorkerTest {
  companion object {
    @JvmField
    @RegisterExtension
    val APP = QuarkusExtensionTest().withApplicationRoot {
      it.addClasses(
        PackageVetoedWorker::class.java, ActiveSubpackageWorker::class.java,
        VetoedWorkerBase::class.java, ActiveVetoedBaseSubclass::class.java,
        WorkerIdentityAndMetadataTest.Engine::class.java, WorkerIdentityAndMetadataTest.Handle::class.java,
        WorkerIdentityAndMetadataTest.Metadata::class.java
      ).addClass("dev.bpmcrafters.processengine.worker.quarkus.deployment.vetoedfixture.package-info")
    }
  }

  @Inject
  lateinit var engine: WorkerIdentityAndMetadataTest.Engine

  @Test
  fun vetoIsNeitherRecursiveAcrossPackagesNorInheritedBySubclasses() {
    assertThat(PackageVetoedWorker::class.java.`package`.isAnnotationPresent(Vetoed::class.java)).isTrue()
    assertThat(ActiveSubpackageWorker::class.java.`package`.isAnnotationPresent(Vetoed::class.java)).isFalse()
    assertThat(ActiveVetoedBaseSubclass::class.java.isAnnotationPresent(Vetoed::class.java)).isFalse()
    assertThat(Arc.container().instance(PackageVetoedWorker::class.java).isAvailable).isFalse()
    assertThat(engine.commands.map { it.taskDescriptionKey })
      .containsExactlyInAnyOrder("active-subpackage", "inherited-vetoed-base")
    for (topic in listOf("active-subpackage", "inherited-vetoed-base")) {
      engine.deliver(topic, emptyMap())
      assertThat(engine.results[topic]).containsExactly(topic)
    }
  }
}
