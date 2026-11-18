package dev.bpmcrafters.processengine.worker.quarkus.idempotency.jpa.deployment

import dev.bpmcrafters.processengine.worker.quarkus.idempotency.jpa.JpaIdempotencyRegistry
import dev.bpmcrafters.processengine.worker.quarkus.idempotency.jpa.TaskLogEntry
import dev.bpmcrafters.processengine.worker.quarkus.idempotency.jpa.TaskResultMapConverter
import io.quarkus.arc.deployment.AdditionalBeanBuildItem
import io.quarkus.deployment.annotations.BuildProducer
import io.quarkus.deployment.annotations.BuildStep
import io.quarkus.deployment.builditem.FeatureBuildItem
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem
import io.quarkus.hibernate.orm.deployment.spi.AdditionalJpaModelBuildItem

class JpaIdempotencyProcessor {
  @BuildStep
  fun feature(): FeatureBuildItem = FeatureBuildItem("process-engine-worker-idempotency-registry-jpa")

  @BuildStep
  fun registry(): AdditionalBeanBuildItem = AdditionalBeanBuildItem.unremovableOf(JpaIdempotencyRegistry::class.java)

  @BuildStep
  fun model(models: BuildProducer<AdditionalJpaModelBuildItem>) {
    // Force the default PU even when an application restricts its entity packages.
    val defaultPersistenceUnit = setOf("<default>")
    models.produce(AdditionalJpaModelBuildItem(TaskLogEntry::class.java.name, defaultPersistenceUnit))
    models.produce(AdditionalJpaModelBuildItem(TaskResultMapConverter::class.java.name, defaultPersistenceUnit))
  }

  @BuildStep
  fun serialization(): ReflectiveClassBuildItem = ReflectiveClassBuildItem.builder(*SerializationTypes.names())
    .serialization().reason("Worker retry-result Java serialization").build()
}
